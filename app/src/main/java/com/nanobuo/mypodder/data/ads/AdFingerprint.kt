package com.nanobuo.mypodder.data.ads

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Impronta audio per riconoscere le pubblicità (stesso metodo e stessi parametri della versione
 * desktop, desktop/src-tauri/src/ads_core.rs).
 *
 * Ogni 32 ms un numero a 32 bit descrive come cambia l'energia in 33 bande di frequenza
 * (metodo di Haitsma e Kalker). Due registrazioni dello stesso suono hanno impronte quasi uguali
 * anche se ricodificate o a volume diverso. Nessuna dipendenza da Android: si prova sulla JVM.
 */
object AdFingerprint {
    const val RATE = 8000
    const val FRAME = 2048
    const val HOP = 256
    const val HOP_MS = HOP * 1000.0 / RATE
    private const val BANDS = 33
    private const val LOW_HZ = 300.0
    private const val HIGH_HZ = 3000.0
    private const val MAGIC = 0x4D504631 // "MPF1"

    fun msToFrames(ms: Double): Int = (ms / HOP_MS).roundToInt()
    fun framesToMs(frames: Int): Long = (frames * HOP_MS).roundToLong()

    /** Calcola l'impronta da campioni mono a [RATE] Hz che arrivano un po' alla volta. */
    class Fingerprinter {
        private val window = FloatArray(FRAME) { (0.5 - 0.5 * cos(2 * PI * it / FRAME)).toFloat() }
        private val edges = IntArray(BANDS + 1) { k ->
            val f = LOW_HZ * (HIGH_HZ / LOW_HZ).pow(k.toDouble() / BANDS)
            (f * FRAME / RATE).roundToInt().coerceAtLeast(1)
        }
        private val fft = Fft(FRAME)
        private val re = FloatArray(FRAME)
        private val im = FloatArray(FRAME)
        private var buf = FloatArray(FRAME * 4)
        private var size = 0
        private var prev: FloatArray? = null
        private val energy = FloatArray(BANDS)
        private var out = IntArray(1 shl 14)
        private var count = 0

        fun push(samples: FloatArray, n: Int = samples.size) {
            if (size + n > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + n))
            System.arraycopy(samples, 0, buf, size, n)
            size += n
            var start = 0
            while (size - start >= FRAME) {
                frame(start)
                start += HOP
            }
            System.arraycopy(buf, start, buf, 0, size - start)
            size -= start
        }

        private fun frame(start: Int) {
            for (i in 0 until FRAME) {
                re[i] = buf[start + i] * window[i]
                im[i] = 0f
            }
            fft.transform(re, im)
            for (b in 0 until BANDS) {
                var e = 0f
                for (k in edges[b] until edges[b + 1]) e += re[k] * re[k] + im[k] * im[k]
                energy[b] = e
            }
            val p = prev
            if (p != null) {
                var bits = 0
                for (m in 0 until BANDS - 1) {
                    val d = (energy[m] - energy[m + 1]) - (p[m] - p[m + 1])
                    if (d > 0f) bits = bits or (1 shl m)
                }
                if (count == out.size) out = out.copyOf(out.size * 2)
                out[count++] = bits
                energy.copyInto(p)
            } else {
                prev = energy.copyOf()
            }
        }

        fun result(): IntArray = out.copyOf(count)
    }

    /** Porta l'audio a mono e a [RATE] Hz facendo la media dei campioni di ogni intervallo. */
    class Downsampler(inRate: Int) {
        private val ratio = inRate.toDouble() / RATE
        private var pos = 0.0
        private var acc = 0f
        private var n = 0

        /** `interleaved` in [-1, 1]; restituisce quanti campioni ha scritto in `out`. */
        fun push(interleaved: FloatArray, length: Int, channels: Int, out: FloatArray): Int {
            val ch = channels.coerceAtLeast(1)
            var written = 0
            var i = 0
            while (i + ch <= length) {
                var mono = 0f
                for (c in 0 until ch) mono += interleaved[i + c]
                acc += mono / ch
                n++
                pos += 1.0
                if (pos >= ratio) {
                    pos -= ratio
                    out[written++] = acc / n
                    acc = 0f
                    n = 0
                }
                i += ch
            }
            return written
        }
    }

    /** FFT complessa radix-2 iterativa, sul posto. */
    private class Fft(private val n: Int) {
        private val rev = IntArray(n)
        private val cosT = FloatArray(n / 2) { cos(2 * PI * it / n).toFloat() }
        private val sinT = FloatArray(n / 2) { (-sin(2 * PI * it / n)).toFloat() }

        init {
            val bits = Integer.numberOfTrailingZeros(n)
            for (i in 0 until n) rev[i] = Integer.reverse(i) ushr (32 - bits)
        }

        fun transform(re: FloatArray, im: FloatArray) {
            for (i in 0 until n) {
                val j = rev[i]
                if (j > i) {
                    val tr = re[i]; re[i] = re[j]; re[j] = tr
                    val ti = im[i]; im[i] = im[j]; im[j] = ti
                }
            }
            var len = 2
            while (len <= n) {
                val half = len / 2
                val step = n / len
                var i = 0
                while (i < n) {
                    var k = 0
                    for (j in i until i + half) {
                        val wr = cosT[k]
                        val wi = sinT[k]
                        val xr = re[j + half] * wr - im[j + half] * wi
                        val xi = re[j + half] * wi + im[j + half] * wr
                        re[j + half] = re[j] - xr
                        im[j + half] = im[j] - xi
                        re[j] += xr
                        im[j] += xi
                        k += step
                    }
                    i += len
                }
                len *= 2
            }
        }
    }

    fun save(file: File, fp: IntArray) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        DataOutputStream(tmp.outputStream().buffered()).use { out ->
            out.writeInt(MAGIC)
            for (v in fp) out.writeInt(v)
        }
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    fun load(file: File): IntArray {
        DataInputStream(file.inputStream().buffered()).use { input ->
            if (input.readInt() != MAGIC) throw IOException("impronta non valida")
            val n = ((file.length() - 4) / 4).toInt()
            return IntArray(n) { input.readInt() }
        }
    }

    /** Tratto uguale in due impronte: inizio e fine (in passi) in `a`, inizio in `b`. */
    data class Match(val aStart: Int, val aEnd: Int, val bStart: Int)

    private const val MAX_HITS = 30
    private const val MIN_VOTES = 6
    private const val MAX_BER = 0.30f
    private const val SMOOTH = 31

    private class Votes(var count: Int, val first: Int, var last: Int)

    /** Trova i tratti comuni ad `a` e `b` lunghi almeno `minFrames`. */
    fun findMatches(a: IntArray, b: IntArray, minFrames: Int): List<Match> {
        if (a.size < SMOOTH || b.size < SMOOTH) return emptyList()
        val index = HashMap<Int, IntArrayList>()
        for (j in b.indices) index.getOrPut(b[j]) { IntArrayList() }.add(j)
        // Ogni valore uguale, o diverso per un solo bit, "vota" lo spostamento tra le posizioni.
        val votes = HashMap<Long, Votes>()
        for (i in a.indices) {
            val h = a[i]
            for (flip in 0..BANDS - 1) {
                val key = if (flip == BANDS - 1) h else h xor (1 shl flip)
                val js = index[key] ?: continue
                if (js.size > MAX_HITS) continue
                for (t in 0 until js.size) {
                    val off = js[t].toLong() - i
                    val v = votes[off]
                    if (v == null) votes[off] = Votes(1, i, i) else { v.count++; v.last = i }
                }
            }
        }
        val offsets = votes.entries.filter { it.value.count >= MIN_VOTES }
            .sortedByDescending { it.value.count }
            .take(200)

        val margin = msToFrames(60_000.0)
        val found = ArrayList<Match>()
        for ((offset, v) in offsets) {
            val lo = maxOf(v.first - margin, 0, (-offset).coerceAtLeast(0L).toInt())
            val hi = minOf(v.last + margin, a.size, (b.size - offset).coerceAtLeast(0L).toInt())
            if (hi <= lo + SMOOTH) continue
            // Errore per passo, tollerando uno slittamento di un passo tra le due codifiche.
            val err = FloatArray(hi - lo) { idx ->
                val i = lo + idx
                val j = i + offset
                var best = 32
                for (k in j - 1..j + 1) {
                    if (k >= 0 && k < b.size) best = minOf(best, Integer.bitCount(a[i] xor b[k.toInt()]))
                }
                best / 32f
            }
            for ((s, e) in matchedRuns(err, minFrames)) {
                val m = Match(lo + s, lo + e, (lo + s + offset).toInt())
                val o = found.indexOfFirst {
                    overlap(it.aStart, it.aEnd, m.aStart, m.aEnd) > 0 && abs(it.bStart.toLong() - it.aStart - offset) <= 3
                }
                if (o >= 0) {
                    if (m.aEnd - m.aStart > found[o].aEnd - found[o].aStart) found[o] = m
                } else {
                    found += m
                }
            }
        }
        return found.sortedBy { it.aStart }
    }

    private fun matchedRuns(err: FloatArray, minFrames: Int): List<Pair<Int, Int>> {
        val n = err.size
        val half = SMOOTH / 2
        val prefix = FloatArray(n + 1)
        for (i in 0 until n) prefix[i + 1] = prefix[i] + err[i]
        val runs = ArrayList<Pair<Int, Int>>()
        var start = -1
        for (i in 0..n) {
            val ok = i < n && run {
                val lo = (i - half).coerceAtLeast(0)
                val hi = minOf(i + half + 1, n)
                (prefix[hi] - prefix[lo]) / (hi - lo) <= MAX_BER
            }
            if (ok && start < 0) start = i
            if (!ok && start >= 0) {
                runs += start to i
                start = -1
            }
        }
        val gap = msToFrames(2_000.0)
        val merged = ArrayList<Pair<Int, Int>>()
        for (r in runs) {
            val last = merged.lastOrNull()
            if (last != null && r.first - last.second <= gap) merged[merged.size - 1] = last.first to r.second
            else merged += r
        }
        return merged.filter { it.second - it.first >= minFrames }
    }

    private fun overlap(a0: Int, a1: Int, b0: Int, b1: Int) = (minOf(a1, b1) - maxOf(a0, b0)).coerceAtLeast(0)

    /** Unisce intervalli sovrapposti o vicini (entro `gap`). */
    fun merge(ranges: List<Pair<Int, Int>>, gap: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        for (r in ranges.sortedWith(compareBy({ it.first }, { it.second }))) {
            val last = out.lastOrNull()
            if (last != null && r.first <= last.second + gap) out[out.size - 1] = last.first to maxOf(last.second, r.second)
            else out += r
        }
        return out
    }

    const val MIN_AD_MS = 5_000.0
    const val MAX_AD_MS = 300_000.0

    /** Tratto ripetuto: posizione (in passi) in `a` e in `b`. */
    data class Repeat(val aStart: Int, val aEnd: Int, val bStart: Int, val bEnd: Int)

    /** Tratti di `a` presenti anche in `b`. */
    fun repeated(a: IntArray, b: IntArray): List<Repeat> {
        val min = msToFrames(MIN_AD_MS)
        val max = msToFrames(MAX_AD_MS)
        val matches = findMatches(a, b, min)
        // Se coincide gran parte dei due file è lo stesso episodio (es. ripubblicato).
        val covered = merge(matches.map { it.aStart to it.aEnd }, 0).sumOf { it.second - it.first }
        if (covered * 2 > minOf(a.size, b.size)) return emptyList()
        return matches.filter { it.aEnd - it.aStart <= max }
            .map { Repeat(it.aStart, it.aEnd, it.bStart, it.bStart + (it.aEnd - it.aStart)) }
    }

    /** Tratti di `local` che non compaiono in `other` (un secondo download dello stesso episodio). */
    fun inserted(local: IntArray, other: IntArray): List<Pair<Int, Int>> {
        val min = msToFrames(MIN_AD_MS)
        val max = msToFrames(MAX_AD_MS)
        val content = merge(findMatches(local, other, msToFrames(10_000.0)).map { it.aStart to it.aEnd }, msToFrames(1_000.0))
        val covered = content.sumOf { it.second - it.first }
        // I due file devono essere davvero lo stesso episodio.
        if (covered * 2 < local.size) return emptyList()
        val gaps = ArrayList<Pair<Int, Int>>()
        var pos = 0
        for ((s, e) in content + (local.size to local.size)) {
            if (s > pos) gaps += pos to s
            pos = maxOf(pos, e)
        }
        return gaps.filter { it.second - it.first in min..max }
    }

    /** Lista di interi senza boxing, per l'indice delle impronte. */
    private class IntArrayList {
        private var data = IntArray(2)
        var size = 0
            private set

        fun add(v: Int) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = v
        }

        operator fun get(i: Int) = data[i]
    }
}
