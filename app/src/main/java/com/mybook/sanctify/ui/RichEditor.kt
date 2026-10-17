package com.mybook.sanctify.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

private class WebHolder { var view: WebView? = null }

private fun hex(c: Color) = String.format("#%06X", c.toArgb() and 0xFFFFFF)

private fun themeJson(p: ReaderPalette): String =
    "{bg:'${hex(p.bg)}',card:'${hex(p.card)}',text:'${hex(p.text)}',muted:'${hex(p.muted)}'," +
        "accent:'${hex(p.heading)}',line:'${hex(p.line)}',soft:'${hex(p.soft)}'}"

/**
 * Редактор текста как в Google Документах: шрифты, размеры, заголовки, жирный/курсив/подчёркнутый/зачёркнутый,
 * цвет текста и выделение, выравнивание, списки (в т. ч. задач), отступы, цитата, код, ссылка, разделитель,
 * отмена/повтор, очистка форматирования. Работает без интернета (Quill, лежит в assets).
 * Содержимое хранится в формате Quill Delta (JSON) — [onChange] получает его и простой текст (для поиска и превью).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RichTextEditor(
    initialRich: String,
    initialPlain: String,
    fontSize: Float,
    onChange: (rich: String, text: String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pal = LocalPalette.current
    val change by rememberUpdatedState(onChange)
    val focus by rememberUpdatedState(onFocusChange)
    var ready by remember { mutableStateOf(false) }
    val web = remember { WebHolder() }

    // Тема и размер шрифта обновляются на лету
    LaunchedEffect(ready, pal, fontSize) {
        if (ready) web.view?.evaluateJavascript("Editor.setTheme(${themeJson(pal)}, ${fontSize.toInt()})", null)
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.textZoom = 100
                setBackgroundColor(pal.bg.toArgb())
                overScrollMode = android.view.View.OVER_SCROLL_NEVER
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onChange(rich: String, text: String) {
                        post { change(rich, text) }
                    }

                    @JavascriptInterface
                    fun onFocus(focused: Boolean) {
                        post { focus(focused) }
                    }

                    @JavascriptInterface
                    fun onReady() {
                        post { ready = true }
                    }
                }, "Android")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(
                            "Editor.init(${JSONObject.quote(initialRich)}, ${JSONObject.quote(initialPlain)}, ${themeJson(pal)}, ${fontSize.toInt()})",
                            null,
                        )
                    }
                }
                loadUrl("file:///android_asset/editor/index.html")
                web.view = this
            }
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            web.view?.let { w ->
                w.evaluateJavascript("Editor.flush()", null)
                w.postDelayed({ w.destroy() }, 700)
            }
        }
    }
}
