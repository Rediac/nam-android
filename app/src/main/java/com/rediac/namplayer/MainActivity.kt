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
            // ── Diagnostic info ─────────────────────────────────────────────
            val originalName = getFileName(uri) ?: "desconocido"
            val fileSize = getFileSize(uri)
            
            Log.d("NAM", "━━━ CARGANDO MODELO ━━━")
            Log.d("NAM", "Nombre original: $originalName")
            Log.d("NAM", "Tamaño: $fileSize bytes (${fileSize / 1024} KB)")
            Log.d("NAM", "URI: $uri")
            
            binding.tvModelName.text = "Copiando..."
            
            // ── Copia a storage interno ─────────────────────────────────────
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
                    Log.d("NAM", "Bytes copiados: $total")
                }
            } ?: run {
                Log.e("NAM", "ERROR: No se pudo abrir el stream del archivo")
                toast("✗ Error [E1]: No se pudo leer el archivo")
                binding.tvModelName.text = "Error E1: lectura"
                updateUI()
                return
            }

            // ── Verificar copia ─────────────────────────────────────────────
            if (!destFile.exists()) {
                Log.e("NAM", "ERROR: El archivo no existe después de copiar")
                toast("✗ Error [E2]: Archivo no encontrado tras copia")
                binding.tvModelName.text = "Error E2: no existe"
                updateUI()
                return
            }
            
            val copiedSize = destFile.length()
            Log.d("NAM", "Archivo copiado: $copiedSize bytes (${copiedSize / 1024} KB)")
            Log.d("NAM", "Path: ${destFile.absolutePath}")

            if (copiedSize == 0L) {
                Log.e("NAM", "ERROR: Archivo copiado tiene 0 bytes")
                toast("✗ Error [E3]: Archivo vacío")
                binding.tvModelName.text = "Error E3: vacío"
                updateUI()
                return
            }

            // ── Cargar modelo en el engine nativo ───────────────────────────
            Log.d("NAM", "Llamando a nativeLoadModel...")
            val t0 = System.currentTimeMillis()
            val success = engine.loadModel(destFile.absolutePath)
            val t1 = System.currentTimeMillis()
            
            Log.d("NAM", "nativeLoadModel retornó: $success (${t1 - t0} ms)")

            if (success) {
                binding.tvModelName.text = originalName
                toast("✓ Modelo cargado (${fileSize / 1024} KB, ${t1 - t0} ms)")
                Log.d("NAM", "✓ ÉXITO")
            } else {
                Log.e("NAM", "ERROR: nativeLoadModel retornó FALSE")
                toast("✗ Error [E4]: Modelo rechazado por el engine (${t1 - t0} ms)")
                binding.tvModelName.text = "Error E4: engine"
            }
        } catch (e: Exception) {
            Log.e("NAM", "EXCEPCIÓN: ${e.javaClass.simpleName}: ${e.message}", e)
            toast("✗ Error [EX]: ${e.javaClass.simpleName}")
            binding.tvModelName.text = "Error: ${e.message?.take(30) ?: "desconocido"}"
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
