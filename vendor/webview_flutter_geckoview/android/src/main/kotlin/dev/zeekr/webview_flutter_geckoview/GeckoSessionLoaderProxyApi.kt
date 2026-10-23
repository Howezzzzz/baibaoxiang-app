package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.GeckoSession

class GeckoSessionLoaderProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiGeckoSessionLoader(pigeonRegistrar) {
    override fun pigeon_defaultConstructor(): GeckoSession.Loader {
        return GeckoSession.Loader()
    }

    override fun additionalHeaders(
        pigeon_instance: GeckoSession.Loader, headers: Map<String, String>
    ): GeckoSession.Loader {
        return pigeon_instance.additionalHeaders(headers)
    }

    override fun dataBytes(
        pigeon_instance: GeckoSession.Loader, bytes: ByteArray, mimeType: String?
    ): GeckoSession.Loader {
        return pigeon_instance.data(bytes, mimeType)
    }

    override fun dataString(
        pigeon_instance: GeckoSession.Loader, data: String, mimeType: String?
    ): GeckoSession.Loader {
        return pigeon_instance.data(data, mimeType)
    }

    override fun flags(
        pigeon_instance: GeckoSession.Loader, flags: Long
    ): GeckoSession.Loader {
        return pigeon_instance.flags(flags.toInt())
    }

    override fun headerFilter(
        pigeon_instance: GeckoSession.Loader, filter: Long
    ): GeckoSession.Loader {
        return pigeon_instance.headerFilter(filter.toInt())
    }

    override fun originalInput(
        pigeon_instance: GeckoSession.Loader, originalInput: String?
    ): GeckoSession.Loader {
        return pigeon_instance.originalInput(originalInput)
    }

    override fun referrerUri(
        pigeon_instance: GeckoSession.Loader, referrerUri: String
    ): GeckoSession.Loader {
        return pigeon_instance.referrer(referrerUri)
    }

    override fun referrer(
        pigeon_instance: GeckoSession.Loader, referrer: GeckoSession
    ): GeckoSession.Loader {
        return pigeon_instance.referrer(referrer)
    }

    override fun textDirectiveUserActivation(
        pigeon_instance: GeckoSession.Loader, textDirectiveUserActivation: Boolean
    ): GeckoSession.Loader {
        return pigeon_instance.textDirectiveUserActivation(textDirectiveUserActivation)
    }

    override fun uri(pigeon_instance: GeckoSession.Loader, uri: String): GeckoSession.Loader {
        return pigeon_instance.uri(uri)
    }
}