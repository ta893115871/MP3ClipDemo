package com.bj.gxz.mp3clipdemo

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer


/**
 * Created by guxiuzhong@baidu.com on 2021/3/7.
 */
object Clip {
    const val TAG = "Clip"

    fun clip(srcMp3Path: String, destWavPath: String, startTimeUs: Long, endTimeUs: Long) {
        if (endTimeUs < startTimeUs) {
            Log.w(TAG, "endTimeMs < startTimeMs")
            return
        }
        // 解码MP3为PCM，保存pcm的临时文件
        val outPcmFile = File(Environment.getExternalStorageDirectory(), "temp.pcm")
        val outPcmChannel = FileOutputStream(outPcmFile).channel

        // 提取器
        val mediaExtractor = MediaExtractor()
        mediaExtractor.setDataSource(srcMp3Path)
        // 选择音频轨道
        val audioTrackIndex = selectTrack(mediaExtractor)
        if (audioTrackIndex == -1) {
            Log.w(TAG, "audioTrackIndex=-1")
            return
        }
        // 选择提取的数据轨道
        mediaExtractor.selectTrack(audioTrackIndex)
        // 最关键的一句，从哪个时间开始提取
        mediaExtractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

        // 获取对应轨道的信息，这里是音频轨道，比如采样率等
        val format = mediaExtractor.getTrackFormat(audioTrackIndex)
        Log.d(TAG, "format=$format")

        // 缓存区
        val maxBufferSize: Int
        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            maxBufferSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            Log.d(TAG, "KEY_MAX_INPUT_SIZE")
        } else {
            maxBufferSize = 100 * 1000
        }
        Log.d(TAG, "maxBufferSize=$maxBufferSize")

        val buffer = ByteBuffer.allocateDirect(maxBufferSize)

        // 创建硬解码器（一般是硬解码）
        val mediaCodec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        mediaCodec.configure(format, null, null, 0)
        mediaCodec.start()

        // 解码
        val info = MediaCodec.BufferInfo()
        while (true) {
            val inputIndex = mediaCodec.dequeueInputBuffer(10_1000)
            if (inputIndex >= 0) {
                val sampleTimeUs = mediaExtractor.sampleTime
                if (sampleTimeUs == -1L) {
                    Log.d(TAG, "-1 break")
                    break
                } else if (sampleTimeUs > endTimeUs) {
                    Log.d(TAG, "sampleTimeUs > endTimeUs break")
                    break
                } else if (sampleTimeUs < startTimeUs) {
                    mediaExtractor.advance()
                }

                info.presentationTimeUs = sampleTimeUs
                info.flags = mediaExtractor.sampleFlags
                info.size = mediaExtractor.readSampleData(buffer, 0)


                val data = ByteArray(buffer.remaining())
                buffer.get(data)

                // 送入MP3数据到解码器
                val inputBuffer = mediaCodec.getInputBuffer(inputIndex)
                inputBuffer!!.clear()
                inputBuffer.put(data)

                mediaCodec.queueInputBuffer(
                    inputIndex,
                    0,
                    info.size,
                    info.presentationTimeUs,
                    info.flags
                )
                // 读取下一个采样
                mediaExtractor.advance()
            }
            // 获取解码后的pcm
            var outputIndex = mediaCodec.dequeueOutputBuffer(info, 10_1000)
            while (outputIndex >= 0) {
                val outByteBuffer = mediaCodec.getOutputBuffer(outputIndex)

                // PCM数据，你也可以做其它操作
//                val data = ByteArray(info.size)
//                buffer.get(data)

                // 这里写入文件
                outPcmChannel.write(outByteBuffer)

                mediaCodec.releaseOutputBuffer(outputIndex, false)
                outputIndex = mediaCodec.dequeueOutputBuffer(info, 0)
            }
        }

        // 各种释放
        outPcmChannel.close()
        mediaCodec.stop()
        mediaCodec.release()
        mediaExtractor.release()


        //  demo的MP3：采样率是44100hz，声道数是 双声道 2，16位的
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        Log.d(TAG, "pcm -> WAV sampleRate:$sampleRate")
        Log.d(TAG, "pcm -> WAV channelCount:$channelCount")

        // pcm -> WAV
        val outWavPath = File(destWavPath)
        PcmToWavUtil(sampleRate, channelCount, 16)
            .pcmToWav(outPcmFile.absolutePath, outWavPath.absolutePath)
        Log.d(TAG, "pcm -> WAV done:$outWavPath")
    }

    private fun selectTrack(mediaExtractor: MediaExtractor): Int {
        //获取轨道数量
        val count = mediaExtractor.trackCount
        for (i in 0 until count) {
            // 获取当前轨道的编码信息
            val format = mediaExtractor.getTrackFormat(i)
            // 通过编码格式字符串对比获取指定轨道
            if (format.getString(MediaFormat.KEY_MIME)!!.startsWith("audio/")) {
                return i
            }
        }
        return -1
    }
}