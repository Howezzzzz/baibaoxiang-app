package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.WebRequest
import java.nio.ByteBuffer

class WebRequestBuilderProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiWebRequestBuilder(pigeonRegistrar) {
    override fun pigeon_defaultConstructor(uri: String): WebRequest.Builder {
        return WebRequest.Builder(uri)
    }

    override fun addHeader(
        pigeon_instance: WebRequest.Builder, key: String, value: String
    ): WebRequest.Builder {
        return pigeon_instance.addHeader(key, value)
    }

    override fun beConservative(
        pigeon_instance: WebRequest.Builder, beConservative: Boolean
    ): WebRequest.Builder {
        return pigeon_instance.beConservative(beConservative)
    }

    override fun bodyString(
        pigeon_instance: WebRequest.Builder, bodyString: String?
    ): WebRequest.Builder {
        return pigeon_instance.body(bodyString)
    }

    override fun bodyBytes(
        pigeon_instance: WebRequest.Builder, bytes: ByteArray?
    ): WebRequest.Builder {
        val buffer = bytes?.let {
            ByteBuffer.allocateDirect(bytes.size).put(bytes)
        }
        return pigeon_instance.body(buffer)
    }

    override fun build(pigeon_instance: WebRequest.Builder): WebRequest {
        return pigeon_instance.build()
    }

    override fun cacheMode(
        pigeon_instance: WebRequest.Builder, mode: Long
    ): WebRequest.Builder {
        return pigeon_instance.cacheMode(mode.toInt())
    }

    override fun header(
        pigeon_instance: WebRequest.Builder, key: String, value: String
    ): WebRequest.Builder {
        return pigeon_instance.header(key, value)
    }

    override fun method(
        pigeon_instance: WebRequest.Builder, method: String
    ): WebRequest.Builder {
        return pigeon_instance.method(method)
    }

    override fun referrer(
        pigeon_instance: WebRequest.Builder, referrer: String
    ): WebRequest.Builder {
        return pigeon_instance.referrer(referrer)
    }

    override fun uri(
        pigeon_instance: WebRequest.Builder, uri: String
    ): WebRequest.Builder {
        return pigeon_instance.uri(uri)
    }
}