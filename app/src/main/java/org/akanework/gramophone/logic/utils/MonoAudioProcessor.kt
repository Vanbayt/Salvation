package org.akanework.gramophone.logic.utils

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

class MonoAudioProcessor : BaseAudioProcessor() {

    var isMonoEnabled: Boolean = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (!isMonoEnabled) return AudioProcessor.AudioFormat.NOT_SET
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return AudioProcessor.AudioFormat(
            inputAudioFormat.sampleRate,
            1, // Mono channel count
            inputAudioFormat.encoding
        )
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isMonoEnabled || inputAudioFormat.channelCount <= 1) {
            val frameCount = inputBuffer.remaining() / inputAudioFormat.bytesPerFrame
            val outputBuffer = replaceOutputBuffer(frameCount * outputAudioFormat.bytesPerFrame)
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val frameCount = inputBuffer.remaining() / inputAudioFormat.bytesPerFrame
        val outputBuffer = replaceOutputBuffer(frameCount * outputAudioFormat.bytesPerFrame)

        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            val channelCount = inputAudioFormat.channelCount
            while (inputBuffer.hasRemaining()) {
                var sum = 0
                for (c in 0 until channelCount) {
                    sum += inputBuffer.short.toInt()
                }
                val monoSample = (sum / channelCount).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                outputBuffer.putShort(monoSample)
            }
        } else if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            val channelCount = inputAudioFormat.channelCount
            while (inputBuffer.hasRemaining()) {
                var sum = 0f
                for (c in 0 until channelCount) {
                    sum += inputBuffer.float
                }
                val monoSample = (sum / channelCount).coerceIn(-1.0f, 1.0f)
                outputBuffer.putFloat(monoSample)
            }
        }

        outputBuffer.flip()
    }
}
