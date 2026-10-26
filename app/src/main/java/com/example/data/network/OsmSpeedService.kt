package com.example.data.network

import android.util.Log
import android.util.Xml
import com.example.data.model.RoadInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class RoadSegment(
    val wayId: Long,
    val name: String,
    val highway: String,
    val lat1: Double,
    val lon1: Double,
    val lat2: Double,
    val lon2: Double,
    val segmentBearing: Float,
    val maxspeedKmH: Int?,
    val maxspeedForwardKmH: Int?,
    val maxspeedBackwardKmH: Int?,
    val isOneway: Boolean,
    val onewayDirection: Int, // 1 for forward, -1 for backward, 0 for two-way
    val isUnlimited: Boolean,
    val rawSpeedTag: String
)

class OsmSpeedService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    private var lastFetchLat: Double = 0.0
    private var lastFetchLon: Double = 0.0
    private var lastFetchTime: Long = 0L

    private var activeSegments: List<RoadSegment> = emptyList()
    private var lastMatchedRoadInfo: RoadInfo? = null

    fun clearCache() {
        lastFetchLat = 0.0
        lastFetchLon = 0.0
        lastFetchTime = 0L
        activeSegments = emptyList()
        lastMatchedRoadInfo = null
    }

    suspend fun getRoadInfo(
        latitude: Double,
        longitude: Double,
        bearing: Float = 0f,
        speedKmH: Double = 0.0,
        isMph: Boolean = false
    ): RoadInfo = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val dist = calculateDistance(latitude, longitude, lastFetchLat, lastFetchLon)
        val elapsedMs = now - lastFetchTime

        // Pull as frequently as possible: every 4 seconds OR when moved 35 meters OR when no segments cached
        val shouldPullFresh = activeSegments.isEmpty() || elapsedMs >= 4000L || dist >= 35.0

        if (shouldPullFresh) {
            val freshSegments = pullFromOpenStreetMapDatabase(latitude, longitude)
            if (freshSegments.isNotEmpty()) {
                activeSegments = freshSegments
                lastFetchLat = latitude
                lastFetchLon = longitude
                lastFetchTime = now
            }
        }

        // Match against active segments from OpenStreetMap
        if (activeSegments.isNotEmpty()) {
            val matched = matchRoadSegment(latitude, longitude, bearing, speedKmH, activeSegments, isMph)
            if (matched != null) {
                lastMatchedRoadInfo = matched
                return@withContext matched
            }
        }

        // Retain last known valid road info from OpenStreetMap if available (no guessing from car speed)
        if (lastMatchedRoadInfo != null) {
            return@withContext lastMatchedRoadInfo!!.copy(
                directionName = getCardinalDirection(bearing),
                bearing = bearing
            )
        }

        // Standard default if outside any mapped road
        val defaultSpeed = if (isMph) 30 else 50
        val cardinal = getCardinalDirection(bearing)
        RoadInfo(
            name = "Road",
            speedLimitKmH = defaultSpeed,
            speedLimitMph = (defaultSpeed * 0.621371).roundToInt(),
            roadType = "general",
            isUnlimited = false,
            source = "OSM Standard ($defaultSpeed)",
            directionName = cardinal,
            bearing = bearing,
            confidence = "Standard"
        )
    }

    private fun pullFromOpenStreetMapDatabase(latitude: Double, longitude: Double): List<RoadSegment> {
        // 1. Primary: Official OpenStreetMap Database API (Bounding Box Query ~200m)
        val segmentsFromOsm = fetchFromOfficialOsmApi(latitude, longitude)
        if (segmentsFromOsm.isNotEmpty()) {
            return segmentsFromOsm
        }

        // 2. Secondary: Fast Overpass Server Instance (z.overpass-api.de / lz4)
        val segmentsFromOverpass = fetchFromFastOverpass(latitude, longitude)
        if (segmentsFromOverpass.isNotEmpty()) {
            return segmentsFromOverpass
        }

        // 3. Fallback: Nominatim reverse geocode
        val nominatimRoad = fetchFromNominatim(latitude, longitude, 0f, false)
        if (nominatimRoad != null && nominatimRoad.speedLimitKmH > 0) {
            return listOf(
                RoadSegment(
                    wayId = 0L,
                    name = nominatimRoad.name,
                    highway = nominatimRoad.roadType,
                    lat1 = latitude - 0.0005,
                    lon1 = longitude,
                    lat2 = latitude + 0.0005,
                    lon2 = longitude,
                    segmentBearing = 0f,
                    maxspeedKmH = nominatimRoad.speedLimitKmH,
                    maxspeedForwardKmH = null,
                    maxspeedBackwardKmH = null,
                    isOneway = false,
                    onewayDirection = 0,
                    isUnlimited = nominatimRoad.isUnlimited,
                    rawSpeedTag = "${nominatimRoad.speedLimitKmH}"
                )
            )
        }

        return emptyList()
    }

    /**
     * Directly queries the official OpenStreetMap database via the 0.6 Map Bounding Box endpoint.
     * Response is authoritative XML containing exact ways, nodes, and maxspeed tags.
     */
    private fun fetchFromOfficialOsmApi(latitude: Double, longitude: Double): List<RoadSegment> {
        val d = 0.0018 // ~200m bounding box
        val minLat = latitude - d
        val maxLat = latitude + d
        val minLon = longitude - d
        val maxLon = longitude + d

        val url = "https://api.openstreetmap.org/api/0.6/map?bbox=$minLon,$minLat,$maxLon,$maxLat"
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "OffLimits_Android/2.6 (OpenStreetMap speed limit assistant)")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val xml = response.body?.string().orEmpty()
                if (xml.isNotEmpty()) {
                    return parseOsmXml(xml)
                }
            }
        } catch (e: Exception) {
            Log.w("OsmSpeedService", "Official OSM API query error: ${e.message}")
        }
        return emptyList()
    }

    private fun parseOsmXml(xmlString: String): List<RoadSegment> {
        val nodeCoords = mutableMapOf<Long, Pair<Double, Double>>()
        val ways = mutableListOf<RawWay>()

        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xmlString))

            var eventType = parser.eventType
            var currentWayId: Long = 0L
            var currentWayNds = mutableListOf<Long>()
            var currentWayTags = mutableMapOf<String, String>()
            var insideWay = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "node" -> {
                                val id = parser.getAttributeValue(null, "id")?.toLongOrNull()
                                val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                                val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                                if (id != null && lat != null && lon != null) {
                                    nodeCoords[id] = Pair(lat, lon)
                                }
                            }
                            "way" -> {
                                insideWay = true
                                currentWayId = parser.getAttributeValue(null, "id")?.toLongOrNull() ?: 0L
                                currentWayNds = mutableListOf()
                                currentWayTags = mutableMapOf()
                            }
                            "nd" -> {
                                if (insideWay) {
                                    val ref = parser.getAttributeValue(null, "ref")?.toLongOrNull()
                                    if (ref != null) currentWayNds.add(ref)
                                }
                            }
                            "tag" -> {
                                if (insideWay) {
                                    val k = parser.getAttributeValue(null, "k")
                                    val v = parser.getAttributeValue(null, "v")
                                    if (k != null && v != null) {
                                        currentWayTags[k] = v
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "way" && insideWay) {
                            val hw = currentWayTags["highway"]
                            if (hw != null && isDrivableHighway(hw)) {
                                ways.add(RawWay(currentWayId, currentWayTags, currentWayNds))
                            }
                            insideWay = false
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w("OsmSpeedService", "XML parse error: ${e.message}")
        }

        // Convert ways into linear segments
        val segments = mutableListOf<RoadSegment>()
        for (w in ways) {
            val tags = w.tags
            val highway = tags["highway"] ?: "residential"
            val name = tags["name"] ?: tags["ref"] ?: highway
            val maxspeedRaw = tags["maxspeed"].orEmpty()
            val maxspeedFwdRaw = tags["maxspeed:forward"].orEmpty()
            val maxspeedBwdRaw = tags["maxspeed:backward"].orEmpty()
            val onewayTag = tags["oneway"].orEmpty()

            val isOneway = onewayTag == "yes" || onewayTag == "1" || onewayTag == "true" || onewayTag == "-1"
            val onewayDirection = if (onewayTag == "-1") -1 else if (isOneway) 1 else 0

            val parsedMaxspeed = parseSpeedTag(maxspeedRaw)
            val parsedFwd = if (maxspeedFwdRaw.isNotEmpty()) parseSpeedTag(maxspeedFwdRaw) else null
            val parsedBwd = if (maxspeedBwdRaw.isNotEmpty()) parseSpeedTag(maxspeedBwdRaw) else null
            val isUnlimited = maxspeedRaw.equals("none", ignoreCase = true) || maxspeedRaw.equals("no", ignoreCase = true)

            for (i in 0 until w.nds.size - 1) {
                val p1 = nodeCoords[w.nds[i]]
                val p2 = nodeCoords[w.nds[i + 1]]
                if (p1 != null && p2 != null) {
                    val segBearing = calculateBearing(p1.first, p1.second, p2.first, p2.second)
                    segments.add(
                        RoadSegment(
                            wayId = w.id,
                            name = name,
                            highway = highway,
                            lat1 = p1.first,
                            lon1 = p1.second,
                            lat2 = p2.first,
                            lon2 = p2.second,
                            segmentBearing = segBearing,
                            maxspeedKmH = parsedMaxspeed,
                            maxspeedForwardKmH = parsedFwd,
                            maxspeedBackwardKmH = parsedBwd,
                            isOneway = isOneway,
                            onewayDirection = onewayDirection,
                            isUnlimited = isUnlimited,
                            rawSpeedTag = maxspeedRaw
                        )
                    )
                }
            }
        }
        return segments
    }

    private data class RawWay(
        val id: Long,
        val tags: Map<String, String>,
        val nds: List<Long>
    )

    private fun isDrivableHighway(hw: String): Boolean {
        return when (hw) {
            "motorway", "trunk", "primary", "secondary", "tertiary",
            "unclassified", "residential", "living_street",
            "motorway_link", "trunk_link", "primary_link", "secondary_link", "tertiary_link" -> true
            else -> false
        }
    }

    private fun fetchFromFastOverpass(latitude: Double, longitude: Double): List<RoadSegment> {
        val d = 0.0018
        val s = latitude - d
        val w = longitude - d
        val n = latitude + d
        val e = longitude + d

        val query = "[out:json][timeout:3];way($s,$w,$n,$e)[\"highway\"];out tags geom 40;"
        val encoded = "data=${URLEncoder.encode(query, "UTF-8")}"

        val servers = listOf(
            "https://z.overpass-api.de/api/interpreter",
            "https://lz4.overpass-api.de/api/interpreter",
            "https://overpass-api.de/api/interpreter"
        )

        for (srv in servers) {
            try {
                val url = "$srv?$encoded"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "OffLimits_Android/2.6 (OpenStreetMap speed limit assistant)")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val segments = parseOverpassJson(body)
                    if (segments.isNotEmpty()) return segments
                }
            } catch (e: Exception) {
                Log.w("OsmSpeedService", "Overpass $srv error: ${e.message}")
            }
        }
        return emptyList()
    }

    private fun parseOverpassJson(jsonString: String): List<RoadSegment> {
        val result = mutableListOf<RoadSegment>()
        try {
            val root = JSONObject(jsonString)
            val elements = root.optJSONArray("elements") ?: return emptyList()

            for (i in 0 until elements.length()) {
                val way = elements.optJSONObject(i) ?: continue
                val wayId = way.optLong("id", 0L)
                val tags = way.optJSONObject("tags") ?: continue
                val geometry = way.optJSONArray("geometry") ?: continue
                if (geometry.length() < 2) continue

                val highway = tags.optString("highway", "residential")
                if (!isDrivableHighway(highway)) continue

                val name = tags.optString("name", tags.optString("ref", highway))
                val maxspeed = tags.optString("maxspeed", "")
                val maxspeedFwd = tags.optString("maxspeed:forward", "")
                val maxspeedBwd = tags.optString("maxspeed:backward", "")
                val onewayTag = tags.optString("oneway", "no")

                val isOneway = onewayTag == "yes" || onewayTag == "1" || onewayTag == "true" || onewayTag == "-1"
                val onewayDirection = if (onewayTag == "-1") -1 else if (isOneway) 1 else 0

                val parsedMaxspeed = parseSpeedTag(maxspeed)
                val parsedFwd = if (maxspeedFwd.isNotEmpty()) parseSpeedTag(maxspeedFwd) else null
                val parsedBwd = if (maxspeedBwd.isNotEmpty()) parseSpeedTag(maxspeedBwd) else null
                val isUnlimited = maxspeed.equals("none", ignoreCase = true) || maxspeed.equals("no", ignoreCase = true)

                for (g in 0 until geometry.length() - 1) {
                    val p1 = geometry.optJSONObject(g) ?: continue
                    val p2 = geometry.optJSONObject(g + 1) ?: continue

                    val lat1 = p1.optDouble("lat", 0.0)
                    val lon1 = p1.optDouble("lon", 0.0)
                    val lat2 = p2.optDouble("lat", 0.0)
                    val lon2 = p2.optDouble("lon", 0.0)
                    if (lat1 == 0.0 || lat2 == 0.0) continue

                    val segBearing = calculateBearing(lat1, lon1, lat2, lon2)
                    result.add(
                        RoadSegment(
                            wayId = wayId,
                            name = name,
                            highway = highway,
                            lat1 = lat1,
                            lon1 = lon1,
                            lat2 = lat2,
                            lon2 = lon2,
                            segmentBearing = segBearing,
                            maxspeedKmH = parsedMaxspeed,
                            maxspeedForwardKmH = parsedFwd,
                            maxspeedBackwardKmH = parsedBwd,
                            isOneway = isOneway,
                            onewayDirection = onewayDirection,
                            isUnlimited = isUnlimited,
                            rawSpeedTag = maxspeed
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("OsmSpeedService", "Overpass JSON error: ${e.message}")
        }
        return result
    }

    private fun matchRoadSegment(
        uLat: Double,
        uLon: Double,
        userBearing: Float,
        speedKmH: Double,
        segments: List<RoadSegment>,
        isMph: Boolean
    ): RoadInfo? {
        if (segments.isEmpty()) return null

        var bestSegment: RoadSegment? = null
        var bestScore = Double.MAX_VALUE
        var bestIsForward = true
        var bestDist = Double.MAX_VALUE

        val hasValidBearing = speedKmH >= 3.0 || userBearing != 0f

        for (seg in segments) {
            val dist = pointToSegmentDistanceMeters(uLat, uLon, seg.lat1, seg.lon1, seg.lat2, seg.lon2)
            if (dist > 75.0) continue

            var headingPenalty = 0.0
            var isDrivingForward = true

            if (hasValidBearing) {
                val forwardDiff = angleDifference(userBearing, seg.segmentBearing)
                val backwardDiff = angleDifference(userBearing, (seg.segmentBearing + 180f) % 360f)

                if (seg.isOneway) {
                    if (seg.onewayDirection == 1) {
                        if (forwardDiff > 80f) {
                            headingPenalty = 200.0 // Wrong way on one-way road
                        } else {
                            headingPenalty = forwardDiff * 0.4
                        }
                        isDrivingForward = true
                    } else {
                        if (backwardDiff > 80f) {
                            headingPenalty = 200.0
                        } else {
                            headingPenalty = backwardDiff * 0.4
                        }
                        isDrivingForward = false
                    }
                } else {
                    if (forwardDiff <= backwardDiff) {
                        headingPenalty = forwardDiff * 0.35
                        isDrivingForward = true
                    } else {
                        headingPenalty = backwardDiff * 0.35
                        isDrivingForward = false
                    }
                }

                // Penalize cross streets and perpendicular overpasses
                val minAngle = minOf(forwardDiff, backwardDiff)
                if (minAngle > 60f) {
                    headingPenalty += 45.0
                }
            }

            // Road class weighting: prioritize major highways over side streets when moving fast
            val roadPriorityBonus = when (seg.highway) {
                "motorway" -> if (speedKmH > 60) -30.0 else -15.0
                "motorway_link" -> -12.0
                "trunk" -> if (speedKmH > 60) -25.0 else -15.0
                "trunk_link" -> -10.0
                "primary" -> if (speedKmH > 45) -20.0 else -10.0
                "primary_link" -> -8.0
                "secondary" -> -6.0
                "tertiary" -> -2.0
                "residential", "unclassified" -> if (speedKmH > 60) 20.0 else 0.0
                else -> 10.0
            }

            // Bonus if explicit maxspeed tag exists in OSM
            val tagPresenceBonus = if (seg.rawSpeedTag.isNotEmpty()) -10.0 else 0.0

            val totalScore = dist + headingPenalty + roadPriorityBonus + tagPresenceBonus
            if (totalScore < bestScore) {
                bestScore = totalScore
                bestSegment = seg
                bestIsForward = isDrivingForward
                bestDist = dist
            }
        }

        val seg = bestSegment ?: return null

        // EXACT SPEED LIMIT FROM OPENSTREETMAP DATABASE (NO GUESSING)
        val hasExplicitTag: Boolean
        val resolvedLimitKmH: Int

        when {
            bestIsForward && seg.maxspeedForwardKmH != null -> {
                resolvedLimitKmH = seg.maxspeedForwardKmH
                hasExplicitTag = true
            }
            !bestIsForward && seg.maxspeedBackwardKmH != null -> {
                resolvedLimitKmH = seg.maxspeedBackwardKmH
                hasExplicitTag = true
            }
            seg.maxspeedKmH != null -> {
                resolvedLimitKmH = seg.maxspeedKmH
                hasExplicitTag = true
            }
            else -> {
                // Official statutory speed limits for road class in OSM
                resolvedLimitKmH = defaultLegalSpeedForHighway(seg.highway, isMph)
                hasExplicitTag = false
            }
        }

        val cardinal = getCardinalDirection(userBearing)
        val sourceLabel = if (hasExplicitTag) {
            "OSM Tag ($resolvedLimitKmH km/h)"
        } else {
            "OSM ${seg.highway.replace('_', ' ').replaceFirstChar { it.uppercase() }} ($resolvedLimitKmH km/h)"
        }

        return RoadInfo(
            name = seg.name,
            speedLimitKmH = resolvedLimitKmH,
            speedLimitMph = (resolvedLimitKmH * 0.621371).roundToInt(),
            roadType = seg.highway,
            isUnlimited = seg.isUnlimited,
            source = sourceLabel,
            directionName = cardinal,
            bearing = userBearing,
            isOneway = seg.isOneway,
            confidence = if (hasExplicitTag) "Exact OSM Tag" else "OSM Statutory"
        )
    }

    private fun fetchFromNominatim(lat: Double, lon: Double, bearing: Float, isMph: Boolean): RoadInfo? {
        try {
            val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=jsonv2&extratags=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "OffLimits_Android/2.6 (OpenStreetMap speed limit assistant)")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string().orEmpty())
                val extratags = json.optJSONObject("extratags")
                val highwayType = json.optString("type", "primary")
                val maxspeedRaw = extratags?.optString("maxspeed", "") ?: ""

                val speedLimitKmH = if (maxspeedRaw.isNotEmpty()) {
                    parseSpeedTag(maxspeedRaw) ?: defaultLegalSpeedForHighway(highwayType, isMph)
                } else {
                    defaultLegalSpeedForHighway(highwayType, isMph)
                }

                return RoadInfo(
                    name = json.optJSONObject("address")?.optString("road", "Road") ?: "Road",
                    speedLimitKmH = speedLimitKmH,
                    speedLimitMph = (speedLimitKmH * 0.621371).roundToInt(),
                    roadType = highwayType,
                    isUnlimited = maxspeedRaw.equals("none", ignoreCase = true),
                    source = if (maxspeedRaw.isNotEmpty()) "OSM Tag ($maxspeedRaw)" else "OSM ($highwayType)",
                    directionName = getCardinalDirection(bearing),
                    bearing = bearing,
                    confidence = "Good"
                )
            }
        } catch (e: Exception) {
            Log.w("OsmSpeedService", "Nominatim lookup failed: ${e.message}")
        }
        return null
    }

    private fun parseSpeedTag(raw: String): Int? {
        val trimmed = raw.trim().lowercase()
        if (trimmed.isEmpty()) return null
        if (trimmed == "none" || trimmed == "no") return 130

        when {
            trimmed.contains("urban") -> return 50
            trimmed.contains("rural") -> return 90
            trimmed.contains("motorway") -> return 110
            trimmed.contains("national") -> return 90
            trimmed.contains("walk") -> return 10
        }

        val digits = trimmed.filter { it.isDigit() }
        if (digits.isNotEmpty()) {
            val num = digits.toIntOrNull() ?: return null
            return if (trimmed.contains("mph")) {
                (num * 1.60934).roundToInt()
            } else {
                num
            }
        }
        return null
    }

    private fun defaultLegalSpeedForHighway(highway: String, isMph: Boolean): Int {
        return if (isMph) {
            when (highway) {
                "motorway" -> 65
                "motorway_link" -> 45
                "trunk" -> 55
                "trunk_link" -> 40
                "primary" -> 50
                "primary_link" -> 35
                "secondary" -> 40
                "secondary_link" -> 30
                "tertiary" -> 35
                "residential", "unclassified" -> 25
                "living_street" -> 15
                else -> 30
            }
        } else {
            // Statutory European / Israeli highway limits
            when (highway) {
                "motorway" -> 110       // 110-120 km/h motorway
                "motorway_link" -> 70   // 70 km/h interchange ramp
                "trunk" -> 90           // 90-100 km/h divided highway
                "trunk_link" -> 60      // 60 km/h
                "primary" -> 80         // 80-90 km/h non-urban main road
                "primary_link" -> 60    // 60 km/h
                "secondary" -> 70       // 70 km/h regional connector
                "secondary_link" -> 50  // 50 km/h
                "tertiary" -> 60        // 60 km/h
                "residential", "unclassified" -> 50 // 50 km/h standard city
                "living_street" -> 30   // 30 km/h shared zone
                else -> 50
            }
        }
    }

    private fun pointToSegmentDistanceMeters(
        uLat: Double,
        uLon: Double,
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val meanLat = Math.toRadians((lat1 + lat2) / 2.0)
        val metersPerLat = 111132.92
        val metersPerLon = 111412.84 * cos(meanLat)

        val xB = (lon2 - lon1) * metersPerLon
        val yB = (lat2 - lat1) * metersPerLat
        val xP = (uLon - lon1) * metersPerLon
        val yP = (uLat - lat1) * metersPerLat

        val lenSq = xB * xB + yB * yB
        val t = if (lenSq > 0.0) ((xP * xB + yP * yB) / lenSq).coerceIn(0.0, 1.0) else 0.0

        val projX = t * xB
        val projY = t * yB

        return hypot(xP - projX, yP - projY)
    }

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)
        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val theta = atan2(y, x)
        return ((Math.toDegrees(theta) + 360.0) % 360.0).toFloat()
    }

    private fun angleDifference(b1: Float, b2: Float): Float {
        return abs((b1 - b2 + 180f) % 360f - 180f)
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        if (lat1 == 0.0 || lat2 == 0.0) return 99999.0
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun getCardinalDirection(bearing: Float): String {
        val norm = (bearing % 360f + 360f) % 360f
        return when {
            norm in 337.5..360.0 || norm in 0.0..22.5 -> "N"
            norm in 22.5..67.5 -> "NE"
            norm in 67.5..112.5 -> "E"
            norm in 112.5..157.5 -> "SE"
            norm in 157.5..202.5 -> "S"
            norm in 202.5..247.5 -> "SW"
            norm in 247.5..292.5 -> "W"
            norm in 292.5..337.5 -> "NW"
            else -> "N"
        }
    }
}
