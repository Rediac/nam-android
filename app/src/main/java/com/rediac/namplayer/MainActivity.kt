package com.rediac.namplayer

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.rediac.namplayer.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val engine = NamEngine()
    private var isPlaying = false

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { loadNamFile(it) } }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startProcessing()
        else toast("Se necesita permiso de micrófono")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvVersion.text = engine.version
        updateUI()

        binding.btnLoadNam.setOnClickListener {
            filePicker.launch("*/*")
        }

        binding.btnPlayStop.setOnClickListener {
            if (isPlaying) stopProcessing() else checkPermissionAndStart()
        }
    }

    private fun loadNamFile(uri: Uri) {
        try {
            val fileName = getFileName(uri) ?: "model.nam"
            val destFile = File(filesDir, fileName)

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }

            val success = engine.loadModel(destFile.absolutePath)
            if (success) {
                binding.tvModelName.text = fileName
                toast("✓ Modelo cargado")
            } else {
                toast("✗ Error al cargar el modelo NAM")
                binding.tvModelName.text = "Error al cargar"
            }
        } catch (e: Exception) {
            toast("Error: ${e.message}")
        }
        updateUI()
    }

    private fun checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            startProcessing()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startProcessing() {
        if (!engine.isModelLoaded) { toast("Carga un modelo .nam primero"); return }
        if (engine.startAudio()) {
            isPlaying = true
            toast("▶ Procesando audio…")
        } else {
            toast("✗ Error al iniciar audio")
        }
        updateUI()
    }

    private fun stopProcessing() {
        engine.stopAudio()
        isPlaying = false
        toast("⏹ Detenido")
        updateUI()
    }

    private fun updateUI() {
        val loaded = engine.isModelLoaded
        binding.btnPlayStop.isEnabled = loaded
        binding.btnPlayStop.text = if (isPlaying) "⏹  Detener" else "▶  Iniciar"
        if (!loaded) binding.tvModelName.text = "Sin modelo"
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) name = cursor.getString(idx)
        }
        return name ?: uri.lastPathSegment
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        stopProcessing()
        engine.unloadModel()
    }
}
