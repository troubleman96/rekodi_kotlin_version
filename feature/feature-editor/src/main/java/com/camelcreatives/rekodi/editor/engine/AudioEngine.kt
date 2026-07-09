package com.camelcreatives.rekodi.editor.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.camelcreatives.rekodi.editor.model.AudioClip
import com.camelcreatives.rekodi.editor.model.WaveformPeaks
import com.camelcreatives.rekodi.common.RekodiResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun extractWaveform(filePath: String, targetPeaks: Int = 1000): WaveformPeaks? {
        return try {
            val extractor = MediaExtractor().apply { setDataSource(filePath) }
            val trackIndex = selectAudioTrack(extractor) ?: return null
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val duration = format.getLong(MediaFormat.KEY_DURATION) / 1000

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val inputBuffers = decoder.inputBuffers
            val outputBuffers = decoder.outputBuffers
            val bufferInfo = MediaCodec.BufferInfo()
            val pcmChunks = mutableListOf<ShortArray>()
            var sawInputEOS = false
            var sawOutputEOS = false
            var inputChunk = 0

            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inputIndex = decoder.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val inputBuf = inputBuffers[inputIndex] ?: continue
                        val chunkSize = extractor.readSampleData(inputBuf, 0)
                        if (chunkSize < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            val presentationTime = extractor.sampleTime
                            decoder.queueInputBuffer(inputIndex, 0, chunkSize, presentationTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputIndex >= 0) {
                    val outputBuf = outputBuffers[outputIndex] ?: continue
                    val byteBuf = ByteBuffer.allocate(bufferInfo.size)
                    outputBuf.position(bufferInfo.offset)
                    outputBuf.limit(bufferInfo.offset + bufferInfo.size)
                    byteBuf.put(outputBuf)
                    byteBuf.flip()

                    val shortBuf = ShortBuffer.allocate(byteBuf.remaining() / 2)
                    byteBuf.order(ByteOrder.nativeOrder()).asShortBuffer().get(shortBuf.array(), 0, shortBuf.remaining())
                    pcmChunks.add(shortBuf.array())

                    decoder.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEOS = true
                    }
                }
            }

            decoder.stop()
            decoder.release()
            extractor.release()

            val allPcm = pcmChunks.flatMap { it.toList() }
            if (allPcm.isEmpty()) return null

            val peaks = computePeaks(allPcm.toShortArray(), targetPeaks)
            WaveformPeaks(peaks, sampleRate, duration)
        } catch (e: Exception) {
            null
        }
    }

    fun trimAudio(inputPath: String, outputPath: String, startMs: Long, endMs: Long): RekodiResult<String> {
        return try {
            val extractor = MediaExtractor().apply { setDataSource(inputPath) }
            val trackIndex = selectAudioTrack(extractor) ?: return RekodiResult.Error("No audio track")
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)

            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return RekodiResult.Error("No mime type")
            val newTrack = muxer.addTrack(format)
            muxer.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val buffer = ByteBuffer.allocate(1024 * 1024)
            var sawEOS = false

            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val startUs = startMs * 1000
            val endUs = endMs * 1000

            while (!sawEOS) {
                val chunkSize = extractor.readSampleData(buffer, 0)
                if (chunkSize < 0) { sawEOS = true; break }
                val presentationTimeUs = extractor.sampleTime
                if (presentationTimeUs > endUs) { sawEOS = true; break }

                bufferInfo.set(0, chunkSize, presentationTimeUs - startUs, extractor.sampleFlags)
                muxer.writeSampleData(newTrack, buffer, bufferInfo)
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            extractor.release()
            RekodiResult.Success(outputPath)
        } catch (e: Exception) {
            RekodiResult.Error("Trim failed: ${e.message}", e)
        }
    }

    fun applyFade(inputPath: String, outputPath: String, fadeInMs: Long, fadeOutMs: Long): RekodiResult<String> {
        return try {
            val extractor = MediaExtractor().apply { setDataSource(inputPath) }
            val trackIndex = selectAudioTrack(extractor) ?: return RekodiResult.Error("No audio track")
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)

            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return RekodiResult.Error("No mime type")
            val newTrack = muxer.addTrack(format)
            muxer.start()

            val duration = format.getLong(MediaFormat.KEY_DURATION)
            val bufferInfo = MediaCodec.BufferInfo()
            val buffer = ByteBuffer.allocate(1024 * 1024)
            var sawEOS = false

            while (!sawEOS) {
                val chunkSize = extractor.readSampleData(buffer, 0)
                if (chunkSize < 0) { sawEOS = true; break }
                val presentationTimeUs = extractor.sampleTime
                var flags = extractor.sampleFlags

                val progress = presentationTimeUs.toFloat() / duration.toFloat()
                val fadeInEnd = fadeInMs * 1000f
                val fadeOutStart = duration.toFloat() - fadeOutMs * 1000f

                if (presentationTimeUs < fadeInEnd && fadeInMs > 0) {
                    val gain = presentationTimeUs / fadeInEnd
                    flags = 0
                } else if (presentationTimeUs > fadeOutStart && fadeOutMs > 0) {
                    val gain = (duration.toFloat() - presentationTimeUs) / (fadeOutMs * 1000f)
                    flags = 0
                }

                bufferInfo.set(0, chunkSize, presentationTimeUs, flags)
                muxer.writeSampleData(newTrack, buffer, bufferInfo)
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            extractor.release()
            RekodiResult.Success(outputPath)
        } catch (e: Exception) {
            RekodiResult.Error("Fade failed: ${e.message}", e)
        }
    }

    fun mergeAudio(clips: List<AudioClip>, outputPath: String): RekodiResult<String> {
        return try {
            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            var totalOffsetUs = 0L

            for (clip in clips) {
                val extractor = MediaExtractor().apply { setDataSource(clip.filePath) }
                val idx = selectAudioTrack(extractor) ?: continue
                extractor.selectTrack(idx)
                val format = extractor.getTrackFormat(idx)
                if (trackIndex < 0) {
                    trackIndex = muxer.addTrack(format)
                    muxer.start()
                }

                val bufferInfo = MediaCodec.BufferInfo()
                val buffer = ByteBuffer.allocate(1024 * 1024)
                var sawEOS = false

                val startUs = clip.startMs * 1000
                val endUs = clip.endMs * 1000
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                while (!sawEOS) {
                    val chunkSize = extractor.readSampleData(buffer, 0)
                    if (chunkSize < 0) { sawEOS = true; break }
                    val pts = extractor.sampleTime
                    if (pts > endUs) { sawEOS = true; break }

                    bufferInfo.set(0, chunkSize, totalOffsetUs + pts - startUs, extractor.sampleFlags)
                    muxer.writeSampleData(trackIndex, buffer, bufferInfo)
                    extractor.advance()
                }

                totalOffsetUs += (clip.endMs - clip.startMs) * 1000
                extractor.release()
            }

            muxer.stop()
            muxer.release()
            RekodiResult.Success(outputPath)
        } catch (e: Exception) {
            RekodiResult.Error("Merge failed: ${e.message}", e)
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return null
    }

    private fun computePeaks(pcm: ShortArray, targetPeaks: Int): FloatArray {
        val chunkSize = maxOf(1, pcm.size / targetPeaks)
        val peaks = FloatArray(targetPeaks)
        for (i in 0 until targetPeaks) {
            val start = i * chunkSize
            val end = minOf(start + chunkSize, pcm.size)
            var max: Float = 0f
            for (j in start until end) {
                val abs = kotlin.math.abs(pcm[j].toFloat() / Short.MAX_VALUE)
                if (abs > max) max = abs
            }
            peaks[i] = max
        }
        return peaks
    }
}
