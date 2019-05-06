package com.example.myapplication

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Bundle
import android.os.Environment
import android.support.v7.app.AppCompatActivity
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Create output file
        val outputFile = File("${Environment.getExternalStorageDirectory()}${File.separator}output.mp4")
        outputFile.createNewFile()
        val outputFilePath = outputFile.absolutePath

        // Init media extractor and setting data source.
        val videoExtractor = MediaExtractor()
        val assetFileDescriptor = resources.openRawResourceFd(R.raw.test)
        videoExtractor.setDataSource(assetFileDescriptor.fileDescriptor, assetFileDescriptor.startOffset, assetFileDescriptor.length)

        // Init media muxer
        val mediaMuxer = MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // Video format which contains additional info about frame rate, codec, mime type
        var videoFormat: MediaFormat? = null
        for(trackIndex in 0..videoExtractor.trackCount) {
            // Determinate video track index
            if (videoExtractor.getTrackFormat(trackIndex).getString("mime").contains("video")) {
                videoFormat = videoExtractor.getTrackFormat(trackIndex)
                // It's needed for correct fetching samples
                videoExtractor.selectTrack(trackIndex)
                // Exit from the loop
                break
            }
        }

        if (videoFormat == null) {
            throw Exception("There is no video track")
        }

        val videoTrack = mediaMuxer.addTrack(videoFormat)
        var frameCount = 0L
        // Size for one frame, it is enough
        val sampleSize = 256 * 1024
        val videoByteBuffer = ByteBuffer.allocate(sampleSize)
        val videoBufferInfo = MediaCodec.BufferInfo()


        // Determinate video time limits.
        val fromTime = TimeUnit.MINUTES.toSeconds(1)
        val toTime = TimeUnit.MINUTES.toSeconds(1) + TimeUnit.SECONDS.toSeconds(10)
        // Use seekTo method to move left video edge
        videoExtractor.seekTo(TimeUnit.SECONDS.toMicros(fromTime), MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        val frameRate = videoFormat.getInteger("frame-rate")
        // Determinate to which frame we should save video, move right video edge)
        val toFrame = (frameRate * TimeUnit.SECONDS.toSeconds(toTime)) - (frameRate * TimeUnit.SECONDS.toSeconds(fromTime))

        mediaMuxer.start()

        fun writeNextSample() {
            videoBufferInfo.size = videoExtractor.readSampleData(videoByteBuffer, 0)
            if (videoBufferInfo.size < 0 || frameCount == toFrame) {
                return
            }
            videoBufferInfo.offset = 0
            videoBufferInfo.presentationTimeUs = videoExtractor.sampleTime
            videoBufferInfo.flags = videoExtractor.sampleFlags
            try {
                mediaMuxer.writeSampleData(videoTrack, videoByteBuffer, videoBufferInfo)
                frameCount++
                videoExtractor.advance()
                writeNextSample()
            } catch (exception: Exception) {
                Log.d(MainActivity::class.java.simpleName, "Error occured: ${exception.localizedMessage}")
            }
        }

        writeNextSample()
        mediaMuxer.stop()
        mediaMuxer.release()
    }
}
