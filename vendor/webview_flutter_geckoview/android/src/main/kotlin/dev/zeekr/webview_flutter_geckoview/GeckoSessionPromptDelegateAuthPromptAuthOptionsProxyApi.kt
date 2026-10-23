package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.GeckoSession

class GeckoSessionPromptDelegateAuthPromptAuthOptionsProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiGeckoSessionPromptDelegateAuthPromptAuthOptions(pigeonRegistrar) {
    override fun flags(pigeon_instance: GeckoSession.PromptDelegate.AuthPrompt.AuthOptions): Long {
        return pigeon_instance.flags.toLong()
    }

    override fun level(pigeon_instance: GeckoSession.PromptDelegate.AuthPrompt.AuthOptions): Long {
        return pigeon_instance.level.toLong()
    }

    override fun password(pigeon_instance: GeckoSession.PromptDelegate.AuthPrompt.AuthOptions): String? {
        return pigeon_instance.password
    }

    override fun uri(pigeon_instance: GeckoSession.PromptDelegate.AuthPrompt.AuthOptions): String? {
        return pigeon_instance.uri
    }

    override fun username(pigeon_instance: GeckoSession.PromptDelegate.AuthPrompt.AuthOptions): String? {
        return pigeon_instance.username
    }
}