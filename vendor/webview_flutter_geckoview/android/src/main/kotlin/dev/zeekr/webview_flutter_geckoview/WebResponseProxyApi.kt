package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.WebResponse
import java.security.cert.X509Certificate

class WebResponseProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiWebResponse(pigeonRegistrar) {
    override fun headers(pigeon_instance: WebResponse): Map<String, String> {
        return pigeon_instance.headers
    }

    override fun uri(pigeon_instance: WebResponse): String {
        return pigeon_instance.uri
    }

    override fun body(pigeon_instance: WebResponse): ByteArray? {
        return pigeon_instance.body?.readBytes()
    }

    override fun certificate(pigeon_instance: WebResponse): X509Certificate? {
        return pigeon_instance.certificate
    }

    override fun isSecure(pigeon_instance: WebResponse): Boolean {
        return pigeon_instance.isSecure
    }

    override fun redirected(pigeon_instance: WebResponse): Boolean {
        return pigeon_instance.redirected
    }

    override fun requestExternalApp(pigeon_instance: WebResponse): Boolean {
        return pigeon_instance.requestExternalApp
    }

    override fun skipConfirmation(pigeon_instance: WebResponse): Boolean {
        return pigeon_instance.skipConfirmation
    }

    override fun statusCode(pigeon_instance: WebResponse): Long {
        return pigeon_instance.statusCode.toLong()
    }
}