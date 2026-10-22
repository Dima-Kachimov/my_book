package com.mybook.sanctify.ui

import android.annotation.SuppressLint
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.mybook.sanctify.AppViewModel
import com.mybook.sanctify.data.Media
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONTokener

private class WebHolder { var view: WebView? = null }

private fun hex(c: Color) = String.format("#%06X", c.toArgb() and 0xFFFFFF)

private fun themeJson(p: ReaderPalette): String =
    "{bg:'${hex(p.bg)}',card:'${hex(p.card)}',text:'${hex(p.text)}',muted:'${hex(p.muted)}'," +
        "accent:'${hex(p.heading)}',line:'${hex(p.line)}',soft:'${hex(p.soft)}'}"

/** Управление редактором снаружи: вставка картинки, импорт файла, экспорт, печать в PDF. */
class RichEditorController {
    internal var web: WebView? = null

    fun insertImage(url: String) {
        web?.evaluateJavascript("Editor.insertImage(${JSONObject.quote(url)})", null)
    }

    fun getCaret(callback: (Int) -> Unit) {
        val w = web ?: return callback(-1)
        w.evaluateJavascript("Editor.getCaret()") { callback(it?.trim()?.toIntOrNull() ?: -1) }
    }

    fun insertQuote(text: String, ref: String, index: Int) {
        web?.evaluateJavascript("Editor.insertQuote(${JSONObject.quote(text)}, ${JSONObject.quote(ref)}, $index)", null)
    }

    fun importDocx(url: String) {
        web?.evaluateJavascript(
            "Editor.importDocx(${JSONObject.quote(url)}).catch(function(e){Android.onError('Не удалось открыть документ: ' + e)})", null,
        )
    }

    fun importText(text: String) {
        web?.evaluateJavascript("Editor.importText(${JSONObject.quote(text)})", null)
    }

    fun importHtml(html: String) {
        web?.evaluateJavascript("Editor.importHtml(${JSONObject.quote(html)})", null)
    }

    fun getHtml(callback: (String) -> Unit) {
        web?.evaluateJavascript("Editor.getHtml()") { raw ->
            callback((JSONTokener(raw).nextValue() as? String).orEmpty())
        }
    }

    fun print(name: String) {
        val w = web ?: return
        val pm = w.context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        pm.print(name, w.createPrintDocumentAdapter(name), PrintAttributes.Builder().build())
    }
}

/**
 * Выбор картинки для вставки в текст: возвращает функцию «открыть выбор».
 * Выбранная картинка копируется в хранилище и вставляется в редактор.
 */
@Composable
fun rememberImageInserter(vm: AppViewModel, controller: RichEditorController): () -> Unit {
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { vm.importInlineImage(uri)?.let { controller.insertImage(it) } }
    }
    return { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/**
 * Редактор текста, максимально близкий к Google Документам: шрифты и размеры, заголовки, начертания, цвета,
 * выравнивание, межстрочный интервал, списки (в т. ч. задач), отступы, таблицы, картинки, ссылки, цитаты, код,
 * поиск и замена, отмена/повтор. Работает без интернета (Quill, лежит в assets).
 * Содержимое хранится в формате Quill Delta (JSON); [onChange] получает его и простой текст (для поиска и превью).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RichTextEditor(
    initialRich: String,
    initialPlain: String,
    fontSize: Float,
    controller: RichEditorController,
    onChange: (rich: String, text: String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onPickImage: () -> Unit,
    onPickBible: () -> Unit = {},
    modifier: Modifier = Modifier,
    onMessage: (String) -> Unit = {},
    onReady: () -> Unit = {},
) {
    val pal = LocalPalette.current
    val change by rememberUpdatedState(onChange)
    val focus by rememberUpdatedState(onFocusChange)
    val pick by rememberUpdatedState(onPickImage)
    val pickBible by rememberUpdatedState(onPickBible)
    val message by rememberUpdatedState(onMessage)
    val readyCb by rememberUpdatedState(onReady)
    var ready by remember { mutableStateOf(false) }

    // Тема и размер шрифта обновляются на лету
    LaunchedEffect(ready, pal, fontSize) {
        if (ready) controller.web?.evaluateJavascript("Editor.setTheme(${themeJson(pal)}, ${fontSize.toInt()})", null)
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val loader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                .addPathHandler("/media/", WebViewAssetLoader.InternalStoragePathHandler(ctx, Media.dir(ctx)))
                .build()
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
                        post { ready = true; readyCb() }
                    }

                    @JavascriptInterface
                    fun onError(text: String) {
                        post { message(text) }
                    }

                    @JavascriptInterface
                    fun pickImage() {
                        post { pick() }
                    }

                    @JavascriptInterface
                    fun pickBible() {
                        post { pickBible() }
                    }

                    /** Картинки из импортируемого .docx: сохраняются в хранилище, возвращается адрес. */
                    @JavascriptInterface
                    fun saveImage(base64: String, mime: String): String = Media.saveBase64Image(ctx, base64, mime)
                }, "Android")
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        loader.shouldInterceptRequest(request.url)

                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(
                            "Editor.init(${JSONObject.quote(initialRich)}, ${JSONObject.quote(initialPlain)}, ${themeJson(pal)}, ${fontSize.toInt()})",
                            null,
                        )
                    }
                }
                loadUrl("${Media.HOST}/assets/editor/index.html")
                controller.web = this
            }
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            controller.web?.let { w ->
                w.evaluateJavascript("Editor.flush()", null)
                w.postDelayed({ w.destroy() }, 700)
            }
            controller.web = null
        }
    }
}
