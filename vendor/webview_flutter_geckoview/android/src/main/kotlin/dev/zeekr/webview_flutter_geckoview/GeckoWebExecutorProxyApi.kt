package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import org.mozilla.geckoview.WebResponse

class GeckoWebExecutorProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiGeckoWebExecutor(pigeonRegistrar) {
    override fun pigeon_defaultConstructor(runtime: GeckoRuntime): GeckoWebExecutor {
        return GeckoWebExecutor(runtime)
    }

    override fun fetch(
        pigeon_instance: GeckoWebExecutor,
        request: WebRequest,
        flags: Long?,
        callback: (Result<WebResponse?>) -> Unit
    ) {
        if (flags == null) {
            pigeon_instance.fetch(request).accept(
                { ResultCompat.success(it, callback) },
                { ResultCompat.failure(it, callback) }
            )
        } else {
            pigeon_instance.fetch(request, flags.toInt()).accept(
                { ResultCompat.success(it, callback) },
                { ResultCompat.failure(it, callback) }
            )
        }
    }

    override fun speculativeConnect(pigeon_instance: GeckoWebExecutor, uri: String) {
        pigeon_instance.speculativeConnect(uri)
    }
}