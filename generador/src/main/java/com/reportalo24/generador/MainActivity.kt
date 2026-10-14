package com.reportalo24.generador

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    /** Último video elegido en el selector de video. */
    private var videoUri: Uri? = null
    private var pickingVideo = false

    private var transformer: Transformer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val pickFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (pickingVideo && uri != null) videoUri = uri
        fileCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        fileCallback = null
    }

    private val requestStorage = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
        }
        webView.addJavascriptInterface(Bridge(), "R24Android")
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                pickingVideo = params.acceptTypes.any { it.startsWith("video") }
                pickFile.launch(if (pickingVideo) "video/*" else "image/*")
                return true
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestStorage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl("file:///android_asset/generador.html")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        transformer?.cancel()
        super.onDestroy()
    }

    private fun hasLegacyStoragePermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    /** Copia un archivo a la galería (Pictures o Movies / Reportalo24). */
    private fun saveToGallery(fileName: String, mime: String, isVideo: Boolean, write: (OutputStream) -> Unit) {
        val baseDir = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$baseDir/Reportalo24")
            }
            val uri = contentResolver.insert(collection, values) ?: error("No se pudo crear el archivo")
            contentResolver.openOutputStream(uri)?.use(write) ?: error("No se pudo escribir el archivo")
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(baseDir), "Reportalo24")
            dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use(write)
            MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), arrayOf(mime), null)
        }
    }

    private fun callJs(fn: String, arg: Any) {
        val js = "window.$fn && window.$fn(${JSONObject.quote(arg.toString())})"
        mainHandler.post { webView.evaluateJavascript(js, null) }
    }

    /** Quema la plantilla (PNG transparente) sobre el video y lo guarda en la galería. */
    private fun startVideoExport(overlay: Bitmap, width: Int, height: Int, fileName: String) {
        val uri = videoUri ?: return callJs("onVideoError", "Primero elige un video")
        val out = File(cacheDir, "export.mp4").apply { delete() }

        val effects = Effects(
            listOf(),
            listOf(
                Presentation.createForWidthAndHeight(width, height, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP),
                OverlayEffect(ImmutableList.of(BitmapOverlay.createStaticBitmapOverlay(overlay)))
            )
        )
        val item = EditedMediaItem.Builder(MediaItem.fromUri(uri)).setEffects(effects).build()

        val t = Transformer.Builder(this)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    finishExport()
                    try {
                        saveToGallery(fileName, "video/mp4", true) { os -> FileInputStream(out).use { it.copyTo(os) } }
                        out.delete()
                        Toast.makeText(this@MainActivity, "Video guardado en Galería › Reportalo24", Toast.LENGTH_LONG).show()
                        callJs("onVideoDone", fileName)
                    } catch (e: Exception) {
                        callJs("onVideoError", e.message ?: "No se pudo guardar el video")
                    }
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    finishExport()
                    out.delete()
                    callJs("onVideoError", exportException.message ?: "Error al procesar el video")
                }
            })
            .build()

        transformer = t
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        t.start(item, out.absolutePath)

        val holder = ProgressHolder()
        mainHandler.post(object : Runnable {
            override fun run() {
                val current = transformer ?: return
                if (current.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    callJs("onVideoProgress", holder.progress)
                }
                mainHandler.postDelayed(this, 500)
            }
        })
    }

    private fun finishExport() {
        transformer = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** Funciones que el HTML puede llamar como window.R24Android.xxx() */
    inner class Bridge {

        /** Guarda un PNG (data URL) en la galería, carpeta Pictures/Reportalo24. Devuelve "ok" o el error. */
        @JavascriptInterface
        fun savePng(dataUrl: String, fileName: String): String {
            return try {
                if (!hasLegacyStoragePermission()) {
                    runOnUiThread { requestStorage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
                    return "Da permiso de almacenamiento y vuelve a tocar el botón"
                }
                val bytes = Base64.decode(dataUrl.substringAfter(","), Base64.DEFAULT)
                saveToGallery(fileName, "image/png", false) { it.write(bytes) }
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Guardado en Galería › Reportalo24", Toast.LENGTH_SHORT).show()
                }
                "ok"
            } catch (e: Exception) {
                e.message ?: "Error desconocido"
            }
        }

        /** true si el archivo existe dentro de la app (assets). */
        @JavascriptInterface
        fun hasAsset(name: String): Boolean =
            try { assets.open(name).close(); true } catch (e: Exception) { false }

        /** Un cuadro del video elegido (segundo 1) como data URL JPEG, para el preview. "" si falla. */
        @JavascriptInterface
        fun videoFrame(): String {
            val uri = videoUri ?: return ""
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(this@MainActivity, uri)
                val frame = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime ?: return ""
                val scale = minOf(1f, 1280f / maxOf(frame.width, frame.height))
                val small = Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt(), (frame.height * scale).toInt(), true)
                val bos = ByteArrayOutputStream()
                small.compress(Bitmap.CompressFormat.JPEG, 85, bos)
                "data:image/jpeg;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
            } catch (e: Exception) {
                ""
            } finally {
                retriever.release()
            }
        }

        /** Arranca la exportación del video con la plantilla encima. El avance llega a window.onVideoProgress. */
        @JavascriptInterface
        fun exportVideo(overlayDataUrl: String, width: Int, height: Int, fileName: String): String {
            if (videoUri == null) return "Primero elige un video"
            if (transformer != null) return "Ya se está exportando un video"
            if (!hasLegacyStoragePermission()) {
                runOnUiThread { requestStorage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
                return "Da permiso de almacenamiento y vuelve a tocar el botón"
            }
            val bytes = Base64.decode(overlayDataUrl.substringAfter(","), Base64.DEFAULT)
            val overlay = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return "No se pudo leer la plantilla"
            runOnUiThread { startVideoExport(overlay, width, height, fileName) }
            return "ok"
        }
    }
}
