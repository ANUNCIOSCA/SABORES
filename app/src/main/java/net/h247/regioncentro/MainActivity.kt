package net.h247.regioncentro

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.File

class MainActivity : Activity() {

    companion object {
        const val HOME = "https://app.247hrs.net/"
        const val HOST = "app.247hrs.net"
        private const val REQ_FILE = 10
        private const val REQ_STORAGE = 11
    }

    private lateinit var web: WebView
    private lateinit var progress: ProgressBar
    private lateinit var offline: View
    private var bridgeJs = ""
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var cameraUri: Uri? = null
    private var pendingSave: Triple<ByteArray, String, String>? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bridgeJs = assets.open("bridge.js").bufferedReader().use { it.readText() }

        val root = FrameLayout(this)
        web = WebView(this)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        offline = buildOfflineView()
        root.addView(web, FrameLayout.LayoutParams(-1, -1))
        root.addView(progress, FrameLayout.LayoutParams(-1, dp(3)))
        root.addView(offline, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            allowFileAccess = false
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = false
            userAgentString = "$userAgentString RegionCentroApp/1.0"
        }
        web.addJavascriptInterface(Bridge(), "RCAndroid")
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, bridgeJs, setOf("https://$HOST"))
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                return openOutside(request.url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (Uri.parse(url).host == HOST) view.evaluateJavascript(bridgeJs, null)
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) offline.visibility = View.VISIBLE
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                recreate()
                return true
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }

            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                val pick = params.createIntent().addCategory(Intent.CATEGORY_OPENABLE)
                if (params.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                val chooser = Intent.createChooser(pick, "Elegir archivo")
                val wantsImage = params.acceptTypes.any { it.isBlank() || it.startsWith("image") }
                if (wantsImage) cameraIntent()?.let { chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(it)) }
                return try {
                    startActivityForResult(chooser, REQ_FILE)
                    true
                } catch (e: ActivityNotFoundException) {
                    fileCallback = null
                    false
                }
            }
        }

        web.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                web.evaluateJavascript("window.__rcGrab && window.__rcGrab(${jsStr(url)}, ${jsStr(name)})", null)
            } else {
                downloadHttp(url, userAgent, mimeType, name)
            }
        }

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState)
        } else {
            val start = intent?.data?.takeIf { it.host == HOST }?.toString() ?: HOME
            web.loadUrl(start)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.takeIf { it.host == HOST }?.let { web.loadUrl(it.toString()) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (offline.visibility == View.VISIBLE && web.canGoBack()) {
            offline.visibility = View.GONE
        }
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    // ---------- Navegación externa (WhatsApp, teléfono, otros sitios) ----------

    private fun openOutside(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase()
        if ((scheme == "https" || scheme == "http") && uri.host == HOST) return false
        try {
            val i = if (scheme == "intent") {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    component = null
                    selector = null
                }
            } else {
                Intent(Intent.ACTION_VIEW, uri)
            }
            startActivity(i)
        } catch (e: Exception) {
            toast("No hay una app para abrir este enlace")
        }
        return true
    }

    // ---------- Archivos: elegir foto / cámara ----------

    private fun cameraIntent(): Intent? {
        return try {
            val dir = File(cacheDir, "shared").apply { mkdirs() }
            val f = File(dir, "foto_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
            cameraUri = uri
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            null
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQ_FILE) {
            super.onActivityResult(requestCode, resultCode, data)
            return
        }
        val cb = fileCallback ?: return
        fileCallback = null
        var result: Array<Uri>? = null
        if (resultCode == RESULT_OK) {
            val clip = data?.clipData
            result = when {
                clip != null -> Array(clip.itemCount) { clip.getItemAt(it).uri }
                data?.data != null -> arrayOf(data.data!!)
                else -> cameraUri?.takeIf { cameraFileHasData(it) }?.let { arrayOf(it) }
            }
        }
        cb.onReceiveValue(result)
    }

    private fun cameraFileHasData(uri: Uri): Boolean = try {
        contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize > 0 } ?: false
    } catch (e: Exception) {
        false
    }

    // ---------- Guardar y compartir (llamado desde bridge.js) ----------

    inner class Bridge {
        @JavascriptInterface
        fun saveFile(b64: String, name: String, mime: String) {
            val bytes = decode(b64) ?: return
            val m = fixMime(mime, name)
            val n = fixName(name, m)
            runOnUiThread { saveToDownloads(bytes, n, m) }
        }

        @JavascriptInterface
        fun shareFile(b64: String, name: String, mime: String, title: String, text: String) {
            val bytes = decode(b64) ?: return
            val m = fixMime(mime, name)
            val n = fixName(name, m)
            runOnUiThread { shareBytes(bytes, n, m, title, text) }
        }

        @JavascriptInterface
        fun shareText(title: String, text: String) {
            runOnUiThread {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(i, "Compartir"))
            }
        }

        @JavascriptInterface
        fun toast(msg: String) {
            runOnUiThread { this@MainActivity.toast(msg) }
        }

        private fun decode(b64: String): ByteArray? = try {
            Base64.decode(b64, Base64.DEFAULT)
        } catch (e: Exception) {
            runOnUiThread { this@MainActivity.toast("Archivo inválido") }
            null
        }
    }

    private fun saveToDownloads(bytes: ByteArray, name: String, mime: String) {
        try {
            val uri: Uri
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/RegionCentro")
                }
                uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("No se pudo crear el archivo")
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            } else {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    pendingSave = Triple(bytes, name, mime)
                    requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_STORAGE)
                    return
                }
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "RegionCentro")
                dir.mkdirs()
                val f = uniqueFile(dir, name)
                f.writeBytes(bytes)
                uri = FileProvider.getUriForFile(this, "$packageName.files", copyToShareCache(bytes, f.name))
            }
            AlertDialog.Builder(this)
                .setTitle("Archivo guardado")
                .setMessage("$name\n\nEn: Descargas/RegionCentro")
                .setPositiveButton("Abrir") { _, _ -> openUri(uri, mime) }
                .setNeutralButton("Compartir") { _, _ -> shareBytes(bytes, name, mime, "", "") }
                .setNegativeButton("Cerrar", null)
                .show()
        } catch (e: Exception) {
            toast("No se pudo guardar: ${e.message}")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_STORAGE) return
        val p = pendingSave ?: return
        pendingSave = null
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            saveToDownloads(p.first, p.second, p.third)
        } else {
            shareBytes(p.first, p.second, p.third, "", "")
        }
    }

    private fun shareBytes(bytes: ByteArray, name: String, mime: String, title: String, text: String) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.files", copyToShareCache(bytes, name))
            val i = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                if (title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, title)
                if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(i, "Compartir").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: Exception) {
            toast("No se pudo compartir: ${e.message}")
        }
    }

    private fun copyToShareCache(bytes: ByteArray, name: String): File {
        val dir = File(cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
        return File(dir, name).apply { writeBytes(bytes) }
    }

    private fun openUri(uri: Uri, mime: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: ActivityNotFoundException) {
            toast("No hay una app para abrir este archivo")
        }
    }

    private fun downloadHttp(url: String, userAgent: String, mimeType: String?, name: String) {
        try {
            val req = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                addRequestHeader("User-Agent", userAgent)
                setTitle(name)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "RegionCentro/$name")
            }
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            toast("Descargando $name")
        } catch (e: Exception) {
            openOutside(Uri.parse(url))
        }
    }

    // ---------- Utilidades ----------

    private fun fixMime(mime: String, name: String): String {
        if (mime.isNotBlank() && mime != "application/octet-stream") return mime.substringBefore(';')
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun fixName(name: String, mime: String): String {
        var n = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "archivo" }
        if (!n.contains('.')) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { n += ".$it" }
        }
        return n
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        var k = 1
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        while (f.exists()) {
            f = File(dir, if (ext.isEmpty()) "$base ($k)" else "$base ($k).$ext")
            k++
        }
        return f
    }

    private fun jsStr(s: String) = "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun buildOfflineView(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(dp(32), dp(32), dp(32), dp(32))
            visibility = View.GONE
            isClickable = true
        }
        box.addView(TextView(this).apply {
            text = "Sin conexión"
            textSize = 22f
            setTextColor(Color.parseColor("#0F1C46"))
            gravity = Gravity.CENTER
        })
        box.addView(TextView(this).apply {
            text = "No se pudo cargar el sistema Región Centro.\nRevisa tu conexión a internet."
            textSize = 15f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(24))
        })
        box.addView(Button(this).apply {
            text = "Reintentar"
            setOnClickListener {
                box.visibility = View.GONE
                if (web.url.isNullOrBlank()) web.loadUrl(HOME) else web.reload()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return box
    }
}
