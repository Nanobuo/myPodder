package com.nanobuo.mypodder.data.ads

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.IOException
import java.nio.ByteOrder

/** Decodifica un file audio con i decoder di sistema e ne calcola l'impronta. */
object AudioDecoder {
    private const val TIMEOUT_US = 10_000L

    /** `path`: file locale o URI content:// (cartella sulla microSD). */
    fun fingerprint(context: Context, path: String, isStopped: () -> Boolean = { false }): IntArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            if (path.startsWith("content://")) extractor.setDataSource(context, Uri.parse(path), null)
            else extractor.setDataSource(path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("nessuna traccia audio")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var isFloat = false
            var down = AdFingerprint.Downsampler(sampleRate)
            val fp = AdFingerprint.Fingerprinter()
            var pcm = FloatArray(16384)
            var mono = FloatArray(16384)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            while (true) {
                if (isStopped()) throw InterruptedException("stopped")
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = decoder.outputFormat
                        sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        isFloat = out.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            out.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        down = AdFingerprint.Downsampler(sampleRate)
                    }
                    outIndex >= 0 -> {
                        val buffer = decoder.getOutputBuffer(outIndex)!!
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        buffer.order(ByteOrder.nativeOrder())
                        val count = if (isFloat) info.size / 4 else info.size / 2
                        if (pcm.size < count) pcm = FloatArray(count)
                        if (isFloat) {
                            buffer.asFloatBuffer().get(pcm, 0, count)
                        } else {
                            val shorts = buffer.asShortBuffer()
                            for (i in 0 until count) pcm[i] = shorts.get(i) / 32768f
                        }
                        if (mono.size < count) mono = FloatArray(count)
                        val written = down.push(pcm, count, channels, mono)
                        fp.push(mono, written)
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            return fp.result()
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }
}
