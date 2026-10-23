package dev.tbox.bbx

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.zeekr.webview_flutter_geckoview.WebviewFlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.StorageController

/**
 * 百宝箱专属 GeckoView 扩展桥（MethodChannel）。
 *
 * 官方 webview_flutter 接口没有的能力放这里：
 *  - clearAllCache：清 GeckoView 全部缓存（HTTP 缓存 + DOM 存储 + Cookie）。
 *  - setColorScheme：网页配色（light/dark/system）→ 影响页面 prefers-color-scheme。
 *
 * 挂载点：WebviewFlutterPlugin.onAttachedToEngine（runtime 由插件持有）。
 */
object GeckoBridge {
    private const val TAG = "GeckoBridge"
    const val CHANNEL = "dev.tbox.bbx/gecko_bridge"

    /** 期望的网页配色；runtime 尚未创建时先记住，创建后补设。 */
    @Volatile
    private var desiredColorScheme: Int? = null

    /** 期望的内容语言；runtime 尚未创建时先记住，创建后补设。 */
    @Volatile
    private var desiredLocales: Array<String>? = null

    /** 给扩展端口发消息用的 id（取大值，避开插件 Dart 侧自增的 id）。 */
    private var bridgeMsgId = 1000000

    // ---- 文件上传（网页里 <input type=file>）--------------------------------
    const val REQ_FILE = 40231

    @Volatile
    private var activity: Activity? = null

    private var pendingFile: ((Array<Uri>?) -> Unit)? = null

    fun setActivity(a: Activity?) {
        activity = a
    }

    /** 把结果交回还在等待的 FilePrompt。 */
    fun handleFileResult(resultCode: Int, data: Intent?) {
        val cb = pendingFile ?: return
        pendingFile = null
        if (resultCode != Activity.RESULT_OK || data == null) {
            cb(null)
            return
        }
        val uris = ArrayList<Uri>()
        val clip = data.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
        } else {
            data.data?.let { uris.add(it) }
        }
        cb(if (uris.isEmpty()) null else uris.toTypedArray())
    }

    /** 拉系统文件选择器，让用户从手机里选文件交给网页。 */
    private fun handleFilePrompt(prompt: GeckoSession.PromptDelegate.FilePrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val act = activity
        if (act == null) {
            Log.w(TAG, "handleFilePrompt: no activity")
            return GeckoResult.fromValue(prompt.dismiss())
        }
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        Handler(Looper.getMainLooper()).post {
            pendingFile = { uris ->
                try {
                    if (uris == null || uris.isEmpty()) {
                        result.complete(prompt.dismiss())
                    } else if (prompt.type == FILE_TYPE_SINGLE) {
                        result.complete(prompt.confirm(act, uris[0]))
                    } else {
                        result.complete(prompt.confirm(act, uris))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "confirm file prompt failed", e)
                    result.complete(prompt.dismiss())
                }
            }
            try {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, prompt.type != FILE_TYPE_SINGLE)
                    val mimes = prompt.mimeTypes
                    if (mimes != null && mimes.isNotEmpty()) {
                        putExtra(Intent.EXTRA_MIME_TYPES, mimes)
                    }
                }
                act.startActivityForResult(
                    Intent.createChooser(intent, "选择文件"), REQ_FILE
                )
            } catch (e: Exception) {
                Log.e(TAG, "start file chooser failed", e)
                pendingFile = null
                result.complete(prompt.dismiss())
            }
        }
        return result
    }

    /**
     * 包装插件自带的 PromptDelegate：插件没实现文件选择（onFilePrompt），
     * 其余弹窗类型原样转交给插件自己的代理，行为不变。
     */
    fun wrapPrompts(inner: GeckoSession.PromptDelegate?): GeckoSession.PromptDelegate =
        object : GeckoSession.PromptDelegate {
            override fun onAlertPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.AlertPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
                inner?.onAlertPrompt(session, prompt) ?: super.onAlertPrompt(session, prompt)

            override fun onButtonPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.ButtonPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
                inner?.onButtonPrompt(session, prompt) ?: super.onButtonPrompt(session, prompt)

            override fun onTextPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.TextPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
                inner?.onTextPrompt(session, prompt) ?: super.onTextPrompt(session, prompt)

            override fun onAuthPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.AuthPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
                inner?.onAuthPrompt(session, prompt) ?: super.onAuthPrompt(session, prompt)

            override fun onFilePrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.FilePrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
                handleFilePrompt(prompt)
        }

    private const val FILE_TYPE_SINGLE = 0

    /**
     * 网页深色适配开关：以扩展端口消息 (category 0 / type 4) 下发给
     * background.js，它再广播给各页面里由它注册的 content script。
     * 大段脚本不走端口（端口对消息体量有限），所以 content script 从扩展自带文件加载。
     */
    private fun setWebDark(enabled: Boolean, result: MethodChannel.Result) {
        WebviewFlutterPlugin.webExtensionPort.accept({ port ->
            if (port == null) {
                result.error("NO_PORT", "webExtensionPort 为空", null)
                return@accept
            }
            Handler(Looper.getMainLooper()).post {
                try {
                    port.postMessage(
                        JSONObject()
                            .put("category", 0)
                            .put("type", 4)
                            .put("id", bridgeMsgId++)
                            .put("enabled", enabled)
                    )
                    result.success(true)
                } catch (e: Exception) {
                    Log.e(TAG, "setWebDark failed", e)
                    result.error("SET_WEB_DARK_FAILED", e.message, null)
                }
            }
        }, { e ->
            Log.e(TAG, "webExtensionPort unavailable", e)
            result.error("NO_PORT", e?.message, null)
        })
    }

    fun attach(messenger: BinaryMessenger, runtimeProvider: () -> GeckoRuntime?) {
        MethodChannel(messenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "clearAllCache" -> {
                    try {
                        val runtime = runtimeProvider()
                        if (runtime == null) {
                            result.error("NO_RUNTIME", "GeckoRuntime 未就绪", null)
                            return@setMethodCallHandler
                        }
                        runtime.storageController.clearData(StorageController.ClearFlags.ALL)
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "clearAllCache failed", e)
                        result.error("CLEAR_CACHE_FAILED", e.message, null)
                    }
                }

                "setColorScheme" -> {
                    val scheme = when (call.argument<String>("scheme")) {
                        "light" -> GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
                        "dark" -> GeckoRuntimeSettings.COLOR_SCHEME_DARK
                        else -> GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
                    }
                    desiredColorScheme = scheme
                    val runtime = runtimeProvider()
                    if (runtime == null) {
                        // runtime 还没建：记住即可，创建时会补设
                        result.success(false)
                        return@setMethodCallHandler
                    }
                    runtime.settings.setPreferredColorScheme(scheme)
                    result.success(true)
                }

                "setLocales" -> {
                    @Suppress("UNCHECKED_CAST")
                    val locales = (call.argument<List<String>>("locales")
                        ?: listOf("zh-CN", "zh", "en")).toTypedArray()
                    desiredLocales = locales
                    val runtime = runtimeProvider()
                    if (runtime == null) {
                        result.success(false)
                        return@setMethodCallHandler
                    }
                    runtime.settings.locales = locales
                    result.success(true)
                }

                "setWebDark" -> setWebDark(call.argument<Boolean>("enabled") ?: false, result)

                else -> result.notImplemented()
            }
        }
    }

    /** runtime 创建后调用，把此前记下的设置补上。 */
    fun applyPending(runtime: GeckoRuntime) {
        try {
            desiredColorScheme?.let { runtime.settings.setPreferredColorScheme(it) }
            desiredLocales?.let { runtime.settings.locales = it }
        } catch (e: Exception) {
            Log.e(TAG, "applyPending failed", e)
        }
    }
}
