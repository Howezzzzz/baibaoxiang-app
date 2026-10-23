package dev.zeekr.webview_flutter_geckoview

import android.os.Handler
import android.os.Looper
import dev.tbox.bbx.GeckoBridge
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
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

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        val proxyApiRegistrar = ProxyApiRegistrar(
            binding.binaryMessenger,
            binding.applicationContext,
            runtime ?: GeckoRuntime.create(binding.applicationContext).apply {
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
            }.also { runtime = it }.also { GeckoBridge.applyPendingColorScheme(it) },
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
    }

    override fun onDetachedFromActivityForConfigChanges() {
        this.proxyApiRegistrar?.setActivity(null)
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        this.proxyApiRegistrar?.setActivity(binding.activity)
    }

    override fun onDetachedFromActivity() {
        this.proxyApiRegistrar?.setActivity(null)
    }
}
