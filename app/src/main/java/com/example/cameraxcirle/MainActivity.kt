package com.example.cameraxcircle

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var recordButton: ImageButton
    private lateinit var switchButton: ImageButton

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var lensFacing = CameraSelector.LENS_FACING_FRONT

    private val permissions = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    ).apply {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        recordButton = findViewById(R.id.recordButton)
        switchButton = findViewById(R.id.switchButton)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionsLauncher.launch(permissions)
        }

        recordButton.setOnClickListener {
            if (recording == null) startRecording() else stopRecording()
        }

        switchButton.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT)
                CameraSelector.LENS_FACING_BACK
            else
                CameraSelector.LENS_FACING_FRONT
            startCamera()
        }
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
            }
        }

    private fun allPermissionsGranted(): Boolean =
        permissions.all {
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

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    videoCapture
                )
            } catch (e: Exception) {
                Log.e("CameraX", "Failed to bind camera use cases", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun startRecording() {
        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraXVideos")
            }
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        recording = videoCapture?.output
            ?.prepareRecording(this, outputOptions)
            ?.withAudioEnabled()
            ?.start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        recordButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    }
                    is VideoRecordEvent.Finalize -> {
                        recordButton.setImageResource(android.R.drawable.ic_btn_speak_now)
                        recording = null
                        if (event.hasError()) {
                            Toast.makeText(this, "Recording failed: ${event.error}", Toast.LENGTH_SHORT).show()
                        } else {
                            val savedUri = event.outputResults.outputUri
                            Toast.makeText(this, "Saved: $savedUri", Toast.LENGTH_SHORT).show()
                            shareToTelegram(savedUri)
                        }
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
            setPackage("org.telegram.messenger")
        }
        try {
            startActivity(Intent.createChooser(intent, "Share to Telegram"))
        } catch (e: Exception) {
            Toast.makeText(this, "Telegram not installed", Toast.LENGTH_SHORT).show()
        }
    }
}
