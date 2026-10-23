package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.WebRequest

class WebRequestProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiWebRequest(pigeonRegistrar) {
    override fun headers(pigeon_instance: WebRequest): Map<String, String> {
        return pigeon_instance.headers
    }

    override fun uri(pigeon_instance: WebRequest): String {
        return pigeon_instance.uri
    }

    override fun beConservative(pigeon_instance: WebRequest): Boolean {
        return pigeon_instance.beConservative
    }

    override fun body(pigeon_instance: WebRequest): ByteArray? {
        return pigeon_instance.body?.let {
            val size = it.remaining()
            val dst = ByteArray(size)
            it.get(dst)
            return dst
        }
    }

    override fun cacheMode(pigeon_instance: WebRequest): Long {
        return pigeon_instance.cacheMode.toLong()
    }

    override fun method(pigeon_instance: WebRequest): String {
        return pigeon_instance.method
    }

    override fun referrer(pigeon_instance: WebRequest): String? {
        return pigeon_instance.referrer
    }
}