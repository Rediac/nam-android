package com.rediac.namplayer

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
            val originalName = getFileName(uri) ?: "desconocido"
            val fileSize = getFileSize(uri)
            
            binding.tvModelName.text = "Copiando..."
            
            val destFile = File(filesDir, "model.nam")
            
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var total = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        total += bytesRead
                    }
                    output.flush()
                }
            } ?: run {
                toast("✗ Error [E1]: No se pudo leer el archivo")
                binding.tvModelName.text = "Error E1: lectura"
                updateUI()
                return
            }

            if (!destFile.exists() || destFile.length() == 0L) {
                toast("✗ Error [E2]: Archivo no se copió bien")
                binding.tvModelName.text = "Error E2: copia"
                updateUI()
                return
            }

            val t0 = System.currentTimeMillis()
            val result = engine.loadModel(destFile.absolutePath)
            val t1 = System.currentTimeMillis()
            
            if (result.startsWith("OK:")) {
                val sizeStr = result.removePrefix("OK:")
                binding.tvModelName.text = originalName
                toast("✓ Modelo cargado (${sizeStr} bytes, ${t1 - t0} ms)")
            } else {
                val cleanError = result.removePrefix("ERROR:")
                binding.tvModelName.text = cleanError.take(40) + "..."
                toast("✗ $cleanError")
            }
        } catch (e: Exception) {
            toast("✗ Error: ${e.message}")
            binding.tvModelName.text = "Error: ${e.message?.take(30)}"
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
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) name = cursor.getString(idx)
        }
        return name ?: uri.lastPathSegment
    }

    private fun getFileSize(uri: Uri): Long {
        var size = 0L
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (cursor.moveToFirst() && idx >= 0) size = cursor.getLong(idx)
        }
        return size
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        super.onDestroy()
        stopProcessing()
        engine.unloadModel()
    }
}
