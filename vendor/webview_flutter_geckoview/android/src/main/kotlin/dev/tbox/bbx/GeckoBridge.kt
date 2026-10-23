package dev.tbox.bbx

import android.util.Log
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
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

                else -> result.notImplemented()
            }
        }
    }

    /** runtime 创建后调用，把此前记下的配色补设上。 */
    fun applyPendingColorScheme(runtime: GeckoRuntime) {
        desiredColorScheme?.let {
            try {
                runtime.settings.setPreferredColorScheme(it)
            } catch (e: Exception) {
                Log.e(TAG, "applyPendingColorScheme failed", e)
            }
        }
    }
}
