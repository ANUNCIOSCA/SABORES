package com.reportalo24.generador

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
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
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
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
                pickImage.launch("image/*")
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

    /** Funciones que el HTML puede llamar como window.R24Android.xxx() */
    inner class Bridge {

        /** Guarda un PNG (data URL) en la galería, carpeta Pictures/Reportalo24. Devuelve "ok" o el error. */
        @JavascriptInterface
        fun savePng(dataUrl: String, fileName: String): String {
            return try {
                val bytes = Base64.decode(dataUrl.substringAfter(","), Base64.DEFAULT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Reportalo24")
                    }
                    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: return "No se pudo crear el archivo"
                    contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: return "No se pudo escribir el archivo"
                } else {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        runOnUiThread { requestStorage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
                        return "Da permiso de almacenamiento y vuelve a tocar el botón"
                    }
                    @Suppress("DEPRECATION")
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Reportalo24")
                    dir.mkdirs()
                    val file = File(dir, fileName)
                    FileOutputStream(file).use { it.write(bytes) }
                    MediaScannerConnection.scanFile(this@MainActivity, arrayOf(file.absolutePath), arrayOf("image/png"), null)
                }
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
    }
}
