package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.WebRequestError
import java.security.cert.X509Certificate

class WebRequestErrorProxyApi(pigeonRegistrar: ProxyApiRegistrar) : PigeonApiWebRequestError(pigeonRegistrar) {
    override fun category(pigeon_instance: WebRequestError): Long {
        return pigeon_instance.category.toLong()
    }

    override fun certificate(pigeon_instance: WebRequestError): X509Certificate? {
        return pigeon_instance.certificate
    }

    override fun code(pigeon_instance: WebRequestError): Long {
        return pigeon_instance.code.toLong()
    }
}