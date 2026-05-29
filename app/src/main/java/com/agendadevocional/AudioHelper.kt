package com.agendadevocional

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File

class AndroidAudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null

    private fun createRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    fun start(outputFile: File) {
        try {
            createRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)

                prepare()
                start()

                recorder = this
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        try {
            recorder?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            recorder?.reset()
            recorder?.release()
            recorder = null
        }
    }
}

class AndroidAudioPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var progressHandler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    fun playFile(
        file: File,
        onProgress: (currentMs: Int, totalMs: Int) -> Unit,
        onCompletion: () -> Unit
    ) {
        stop() // Garante a interrupção de reproduções anteriores
        try {
            MediaPlayer.create(context, Uri.fromFile(file)).apply {
                player = this
                setOnCompletionListener {
                    stopProgressUpdates()
                    onCompletion()
                    release()
                    player = null
                }
                start()
                startProgressUpdates(onProgress)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onCompletion()
        }
    }

    fun pause() {
        player?.let {
            if (it.isPlaying) {
                it.pause()
                stopProgressUpdates()
            }
        }
    }

    fun resume(onProgress: (currentMs: Int, totalMs: Int) -> Unit) {
        player?.let {
            if (!it.isPlaying) {
                it.start()
                startProgressUpdates(onProgress)
            }
        }
    }

    fun seekTo(positionMs: Int) {
        player?.seekTo(positionMs)
    }

    fun isPlaying(): Boolean {
        return player?.isPlaying ?: false
    }

    fun getDuration(): Int {
        return player?.duration ?: 0
    }

    fun getCurrentPosition(): Int {
        return player?.currentPosition ?: 0
    }

    fun stop() {
        stopProgressUpdates()
        try {
            player?.stop()
            player?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            player = null
        }
    }

    private fun startProgressUpdates(onProgress: (currentMs: Int, totalMs: Int) -> Unit) {
        stopProgressUpdates()
        progressRunnable = object : Runnable {
            override fun run() {
                player?.let {
                    if (it.isPlaying) {
                        onProgress(it.currentPosition, it.duration)
                        progressHandler.postDelayed(this, 250)
                    }
                }
            }
        }
        progressHandler.post(progressRunnable!!)
    }

    private fun stopProgressUpdates() {
        progressRunnable?.let {
            progressHandler.removeCallbacks(it)
        }
        progressRunnable = null
    }
}
