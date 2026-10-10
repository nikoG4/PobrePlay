package com.pobreplay.tv

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject

data class Fuente(val nombre: String, val url: String)

class MainActivity : ComponentActivity() {
    private val bg = Color(0xFF0D1B2E)
    private val card = Color(0xFF1A2D46)
    private val accent = Color(0xFF99CDFF)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = accent, surface = card, background = bg
            )) {
                Pantalla()
            }
        }
    }

    private fun fuentesGuardadas(): List<Fuente> {
        val ejemplo = listOf(
            Fuente("Video de demostracion", "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
            Fuente("Enlace de prueba", "https://la18hd.su/vivo/canal.php?stream=disney18")
        )
        val pref = getSharedPreferences("pobreplay", Context.MODE_PRIVATE)
        val texto = pref.getString("fuentes", null) ?: return ejemplo
        return try {
            val arr = JSONArray(texto)
            (0 until arr.length()).mapNotNull {
                val obj = arr.getJSONObject(it)
                val url = obj.optString("url")
                if (validar(url) != null) Fuente(obj.optString("nombre"), url) else null
            }
        } catch (_: Exception) { ejemplo }
    }

    private fun guardar(fuentes: List<Fuente>) {
        val arr = JSONArray()
        fuentes.forEach {
            arr.put(JSONObject().put("nombre", it.nombre).put("url", it.url))
        }
        getSharedPreferences("pobreplay", Context.MODE_PRIVATE).edit()
            .putString("fuentes", arr.toString()).apply()
    }

    @Composable
    private fun Pantalla() {
        val fuentes = remember { mutableStateListOf<Fuente>().apply { addAll(fuentesGuardadas()) } }
        var seleccion by remember { mutableStateOf<Fuente?>(null) }
        var vistaTv by remember { mutableStateOf(false) }
        DisposableEffect(vistaTv) {
            requestedOrientation = if (vistaTv) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            onDispose {}
        }
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            if (seleccion == null) {
                Inicio(fuentes, vistaTv, alternarTv = { vistaTv = !vistaTv },
                    reproducir = { seleccion = it }, quitar = {
                        fuentes.remove(it); guardar(fuentes)
                    }, agregar = {
                        fuentes.add(it); guardar(fuentes)
                    })
            } else {
                Reproductor(seleccion!!, volver = { seleccion = null })
            }
        }
    }

    @Composable
    private fun Inicio(
        fuentes: List<Fuente>, vistaTv: Boolean, alternarTv: () -> Unit,
        reproducir: (Fuente) -> Unit, quitar: (Fuente) -> Unit, agregar: (Fuente) -> Unit
    ) {
        var nombre by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("▶ PobrePlay", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall)
                OutlinedButton(onClick = alternarTv) {
                    Text(if (vistaTv) "Salir TV" else "Vista TV")
                }
            }
            Text("Fuentes (" + fuentes.size + ")", style = MaterialTheme.typography.titleLarge)
            fuentes.forEach { fuente ->
                Card(onClick = { reproducir(fuente) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("▶  ", color = accent)
                        Column(Modifier.weight(1f)) {
                            Text(fuente.nombre, style = MaterialTheme.typography.titleMedium)
                            Text(fuente.url, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = { quitar(fuente) }) { Text("Quitar") }
                    }
                }
            }
            HorizontalDivider()
            Text("Agregar reproductor", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(nombre, onValueChange = { nombre = it }, label = {
                Text("Nombre")
            }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(url, onValueChange = { url = it }, label = {
                Text("URL HTTPS o codigo iframe")
            }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            if (error.isNotEmpty()) Text(error, color = Color.Red)
            Button(onClick = {
                val direccion = validar(url)
                if (direccion == null) error = "URL HTTPS no valida."
                else {
                    agregar(Fuente(nombre.ifBlank { Uri.parse(direccion).host ?: "Fuente" }, direccion))
                    nombre = ""; url = ""; error = ""
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Guardar fuente") }
            Text("Version 0.1. Sin scraping ni elusion DRM. Proba fuentes que puedas utilizar legalmente.",
                style = MaterialTheme.typography.bodySmall)
        }
    }

    @Composable
    private fun Reproductor(fuente: Fuente, volver: () -> Unit) {
        var web by remember(fuente.url) { mutableStateOf<WebView?>(null) }
        var chrome by remember(fuente.url) { mutableStateOf<VideoChrome?>(null) }
        var bloquear by remember { mutableStateOf(true) }
        var controles by remember { mutableStateOf(true) }
        var cargando by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf("") }

        DisposableEffect(fuente.url) {
            val insets = WindowCompat.getInsetsController(window, window.decorView)
            insets.hide(WindowInsetsCompat.Type.systemBars())
            onDispose { insets.show(WindowInsetsCompat.Type.systemBars()) }
        }
        BackHandler {
            if (chrome?.salir() != true) {
                if (!controles) controles = true else volver()
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
                val videoChrome = VideoChrome(this@MainActivity)
                chrome = videoChrome
                WebView(ctx).apply {
                    setBackgroundColor(AColor.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.setSupportMultipleWindows(true)
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    isFocusable = true
                    isFocusableInTouchMode = true
                    webChromeClient = videoChrome
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView, request: WebResourceRequest
                        ): Boolean = request.url.scheme != "https" ||
                            (bloquear && request.isForMainFrame && request.hasGesture())

                        override fun onPageFinished(view: WebView, url: String) { cargando = false }
                        override fun onReceivedError(
                            view: WebView, request: WebResourceRequest, err: WebResourceError
                        ) {
                            if (request.isForMainFrame) {
                                cargando = false
                                error = "Error al cargar la pagina."
                            }
                        }
                    }
                    loadUrl(fuente.url)
                }.also { web = it }
            }, onRelease = { item ->
                chrome?.salir()
                item.stopLoading()
                item.loadUrl("about:blank")
                item.destroy()
                web = null
            })
            if (cargando) CircularProgressIndicator(Modifier.align(Alignment.Center))
            if (error.isNotEmpty()) {
                Text(error, modifier = Modifier.align(Alignment.Center).background(bg).padding(16.dp))
            }
            if (controles) {
                Column(Modifier.fillMaxWidth().background(Color(0xE60D1B2E)).padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = volver) { Text("←") }
                        Text(fuente.nombre, modifier = Modifier.weight(1f).padding(8.dp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        OutlinedButton(onClick = {
                            error = ""; cargando = true; web?.reload()
                        }) { Text("↻") }
                        OutlinedButton(onClick = { controles = false }) { Text("Ocultar") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Bloquear saltos de pagina")
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = bloquear, onCheckedChange = { bloquear = it })
                    }
                }
            } else {
                OutlinedButton(onClick = { controles = true },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) { Text("☰") }
            }
        }
    }
}

private fun validar(valor: String): String? {
    val match = Regex("""(?i)\bsrc\s*=\s*["']([^"']+)["']""").find(valor)
    val raw = (match?.groupValues?.get(1) ?: valor.trim()).replace("&amp;", "&")
    return try {
        val uri = Uri.parse(raw)
        if (uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
            && !raw.contains(' ') && raw.length < 2048
        ) raw else null
    } catch (_: Exception) { null }
}

private class VideoChrome(val activity: ComponentActivity) : WebChromeClient() {
    private var vista: View? = null
    private var callback: CustomViewCallback? = null
    override fun onShowCustomView(view: View, cb: CustomViewCallback) {
        val root = activity.window.decorView as? FrameLayout ?: run {
            cb.onCustomViewHidden(); return
        }
        if (vista != null) { cb.onCustomViewHidden(); return }
        vista = view
        callback = cb
        root.addView(view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }
    override fun onHideCustomView() { salir() }
    fun salir(): Boolean {
        val original = vista ?: return false
        (original.parent as? ViewGroup)?.removeView(original)
        vista = null
        callback?.onCustomViewHidden()
        callback = null
        return true
    }
    override fun onCreateWindow(
        view: WebView?, dialog: Boolean, gesture: Boolean, msg: android.os.Message?
    ): Boolean = false
    override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
}
