package com.m2011b.coastertracker

import android.content.Context
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/**
 * The bundled map (assets/basemap.bin, built by tools/basemap/build_basemap.py from Natural Earth,
 * public domain). Coordinates are Web Mercator world units, 0..2^24 across the world.
 */
class Basemap(val fine: Layers, val coarse: Layers, val places: List<Place>) {
    class Feature(val minZoom: Float, val path: Path, val left: Float, val top: Float, val right: Float, val bottom: Float)
    class Layers(
        val land: List<Feature>, val lakes: List<Feature>, val rivers: List<Feature>,
        val countries: List<Feature>, val states: List<Feature>, val roads: List<Feature>,
    )
    class Place(val name: String, val x: Float, val y: Float, val minZoom: Float, val rank: Int)

    companion object {
        const val WORLD = 16_777_216.0  // 2^24

        fun x(lon: Double) = (lon + 180.0) / 360.0 * WORLD
        fun y(lat: Double): Double {
            val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
            return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * WORLD
        }

        @Volatile private var cached: Basemap? = null

        /** Loads once per process; call off the main thread. */
        fun get(ctx: Context): Basemap = cached ?: synchronized(this) {
            cached ?: ctx.assets.open("basemap.bin").use { parse(it.readBytes()) }.also { cached = it }
        }

        fun parse(d: ByteArray): Basemap {
            var pos = 0
            fun u8() = d[pos++].toInt() and 0xFF
            fun varint(): Long {
                var n = 0L; var shift = 0
                while (true) { val b = u8(); n = n or ((b and 0x7F).toLong() shl shift); shift += 7; if (b and 0x80 == 0) return n }
            }
            fun zz(n: Long) = (n ushr 1) xor -(n and 1)
            require(String(d, 0, 4) == "LHM1") { "Not a basemap" }
            pos = 4
            val byId = HashMap<Int, List<Feature>>()
            val places = ArrayList<Place>()
            repeat(u8()) {
                val kind = u8(); val id = u8()
                val count = varint().toInt()
                if (kind == 2) {
                    repeat(count) {
                        val len = varint().toInt(); val name = String(d, pos, len); pos += len
                        val x = zz(varint()).toFloat(); val y = zz(varint()).toFloat()
                        places += Place(name, x, y, u8() / 10f, u8())
                    }
                } else {
                    val list = ArrayList<Feature>(count)
                    repeat(count) {
                        val minZoom = u8() / 10f
                        val rings = if (kind == 0) varint().toInt() else 1
                        val path = Path().apply { fillType = PathFillType.EvenOdd }
                        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
                        repeat(rings) {
                            val n = varint().toInt(); var x = 0L; var y = 0L
                            for (i in 0 until n) {
                                x += zz(varint()); y += zz(varint())
                                val fx = x.toFloat(); val fy = y.toFloat()
                                if (i == 0) path.moveTo(fx, fy) else path.lineTo(fx, fy)
                                if (fx < l) l = fx; if (fx > r) r = fx; if (fy < t) t = fy; if (fy > b) b = fy
                            }
                            if (kind == 0) path.close()
                        }
                        list += Feature(minZoom, path, l, t, r, b)
                    }
                    byId[id] = list
                }
            }
            fun layers(off: Int) = Layers(
                byId[0 + off].orEmpty(), byId[1 + off].orEmpty(), byId[2 + off].orEmpty(),
                byId[3 + off].orEmpty(), byId[4 + off].orEmpty(), byId[5 + off].orEmpty(),
            )
            return Basemap(layers(0), layers(10), places)
        }
    }
}
