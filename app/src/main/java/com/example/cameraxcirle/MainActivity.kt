package com.example.cameraxcircle

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import androidx.activity.ComponentActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.camera.view.PreviewView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var recordButton: ImageButton
    private lateinit var switchButton: ImageButton

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        recordButton = findViewById(R.id.recordButton)
        switchButton = findViewById(R.id.switchCameraButton)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                10
            )
        }

        recordButton.setOnClickListener {
            if (recording == null) startRecording() else stopRecording()
        }

        switchButton.setOnClickListener {
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }
            startCamera()
        }
    }

    private fun allPermissionsGranted() = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    ).all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    videoCapture
                )
            } catch (e: Exception) {
                Log.e("CameraX", "Binding failed", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun startRecording() {
        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .format(System.currentTimeMillis())
        val file = File(externalCacheDir, "$name.mp4")

        val outputOptions = FileOutputOptions.Builder(file).build()
        recording = videoCapture?.output
            ?.prepareRecording(this, outputOptions)
            ?.withAudioEnabled()
            ?.start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        recordButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    }
                    is VideoRecordEvent.Finalize -> {
                        recording = null
                        recordButton.setImageResource(android.R.drawable.ic_btn_speak_now)
                        shareToTelegram(Uri.fromFile(file))
                    }
                }
            }
    }

    private fun stopRecording() {
        recording?.stop()
        recording = null
    }

    private fun shareToTelegram(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            `package` = "org.telegram.messenger"
        }
        startActivity(Intent.createChooser(intent, "Share video"))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == 10 && allPermissionsGranted()) {
            startCamera()
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
