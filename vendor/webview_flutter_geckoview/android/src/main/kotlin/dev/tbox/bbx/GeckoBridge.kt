package dev.tbox.bbx

import android.util.Log
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.StorageController

/**
 * 百宝箱专属 GeckoView 扩展桥（MethodChannel）。
 *
 * 官方 webview_flutter 接口没有的能力放这里：
 *  - clearAllCache：清 GeckoView 全部缓存（HTTP 缓存 + DOM 存储 + Cookie）。
 *    Dart 侧 WebViewCookieManager 只清 Cookie，这里补全其余部分。
 *
 * 挂载点：WebviewFlutterPlugin.onAttachedToEngine（runtime 由插件持有）。
 */
object GeckoBridge {
    private const val TAG = "GeckoBridge"
    const val CHANNEL = "dev.tbox.bbx/gecko_bridge"

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
                else -> result.notImplemented()
            }
        }
    }
}