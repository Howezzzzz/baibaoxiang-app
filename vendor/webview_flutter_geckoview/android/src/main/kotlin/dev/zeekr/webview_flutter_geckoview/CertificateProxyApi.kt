package dev.zeekr.webview_flutter_geckoview

import java.security.cert.Certificate

class CertificateProxyApi(pigeonRegistrar: ProxyApiRegistrar) : PigeonApiCertificate(pigeonRegistrar) {
    override fun getEncoded(pigeon_instance: Certificate): ByteArray {
        return pigeon_instance.encoded
    }
}