package dev.zeekr.webview_flutter_geckoview

import android.os.Handler
import android.os.Looper
import dev.tbox.bbx.GeckoBridge
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.WebExtension

/**
 * Java platform implementation of the webview_flutter plugin.
 *
 * <p>Register this in an add to app scenario to gracefully handle activity and context changes.
 */
class WebviewFlutterPlugin : FlutterPlugin, ActivityAware {
    companion object {
        var runtime: GeckoRuntime? = null
        val webExtensionPort by lazy { GeckoResult<WebExtension.Port>() }
    }

    private var proxyApiRegistrar: ProxyApiRegistrar? = null

    /** 文件选择的结果回调只注册一次 */
    private var fileResultListenerAdded = false

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        val proxyApiRegistrar = ProxyApiRegistrar(
            binding.binaryMessenger,
            binding.applicationContext,
            runtime ?: GeckoRuntime.create(
                binding.applicationContext,
                // 性能取向：关掉这个壳用不到的子系统，减少每次加载的额外开销
                GeckoRuntimeSettings.Builder()
                    .consoleOutput(false)              // 不需要控制台输出
                    .aboutConfigEnabled(false)         // 不暴露 about:config
                    .loginAutofillEnabled(false)       // 不做登录自动填充
                    .translationsOfferPopup(false)     // 不做翻译弹窗
                    .inputAutoZoomEnabled(false)       // 输入框自动缩放会触发重排
                    .fontInflation(false)              // 字体膨胀会触发整页重排
                    .build()
            ).apply {
                // this.settings.consoleOutputEnabled = true
                this.webExtensionController.ensureBuiltIn(
                    "resource://android/assets/messaging/", "messaging@zeekr.dev"
                ).accept({
                    if (it == null) {
                        webExtensionPort.complete(null)
                    } else {
                        Handler(Looper.getMainLooper()).post {
                            it.setMessageDelegate(object : WebExtension.MessageDelegate {
                                override fun onConnect(webExtensionPort: WebExtension.Port) {
                                    super.onConnect(webExtensionPort)
                                    WebviewFlutterPlugin.webExtensionPort.complete(
                                        webExtensionPort
                                    )
                                }
                            }, "webview_flutter")
                        }
                    }
                }, {
                    val error = it ?: NullPointerException("throwable is null")
                    webExtensionPort.completeExceptionally(error)
                })
            }.also { runtime = it }.also {
                // 权威 DNS 解析即可；不做 DoH 自动选择（省掉一次额外的解析往返）
                try {
                    it.settings.dohAutoselectEnabled = false
                } catch (e: Exception) {
                    android.util.Log.w("BBX", "dohAutoselect off failed", e)
                }
                GeckoBridge.applyPending(it)
            },
            webExtensionPort,
            FlutterAssetManager.PluginBindingFlutterAssetManager(
                binding.applicationContext.assets, binding.flutterAssets
            )
        ).also { this.proxyApiRegistrar = it }
        binding.platformViewRegistry.registerViewFactory(
            "plugins.flutter.io/webview", FlutterViewFactory(proxyApiRegistrar.instanceManager)
        )
        proxyApiRegistrar.setUp()
        // 百宝箱扩展桥：清缓存等官方接口没有的能力
        GeckoBridge.attach(binding.binaryMessenger) { WebviewFlutterPlugin.runtime }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        val proxyApiRegistrar = this.proxyApiRegistrar ?: return
        proxyApiRegistrar.tearDown()
        proxyApiRegistrar.instanceManager.stopFinalizationListener()
        this.proxyApiRegistrar = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.proxyApiRegistrar?.setActivity(binding.activity)
        GeckoBridge.setActivity(binding.activity)
        if (!fileResultListenerAdded) {
            binding.addActivityResultListener { requestCode, resultCode, data ->
                if (requestCode == GeckoBridge.REQ_FILE) {
                    GeckoBridge.handleFileResult(resultCode, data)
                    true
                } else {
                    false
                }
            }
            fileResultListenerAdded = true
        }
    }

    override fun onDetachedFromActivityForConfigChanges() {
        this.proxyApiRegistrar?.setActivity(null)
        GeckoBridge.setActivity(null)
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        this.proxyApiRegistrar?.setActivity(binding.activity)
        GeckoBridge.setActivity(binding.activity)
    }

    override fun onDetachedFromActivity() {
        this.proxyApiRegistrar?.setActivity(null)
        GeckoBridge.setActivity(null)
    }
}
