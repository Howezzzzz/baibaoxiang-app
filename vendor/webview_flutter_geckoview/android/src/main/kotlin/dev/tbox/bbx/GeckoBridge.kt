package dev.tbox.bbx

import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.zeekr.webview_flutter_geckoview.WebviewFlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
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

    /**
     * 网页深色适配开关：以扩展端口消息 (category 0 / type 4) 下发给
     * background.js，它再广播给各页面里由它注册的 content script。
     * 大段脚本不走端口（端口对消息体量有限），所以 content script 从扩展自带文件加载。
     */
    private fun setWebDark(enabled: Boolean, result: MethodChannel.Result) {
        WebviewFlutterPlugin.webExtensionPort.accept({ port ->
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
