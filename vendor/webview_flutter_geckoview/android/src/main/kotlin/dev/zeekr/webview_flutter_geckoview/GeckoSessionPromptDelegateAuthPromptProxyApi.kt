package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.GeckoSession

class GeckoSessionPromptDelegateAuthPromptProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiGeckoSessionPromptDelegateAuthPrompt(pigeonRegistrar) {
    override fun title(pigeon_instance: GeckoSession.PromptDelegate.AuthPrompt): String? {
        return pigeon_instance.title
    }

    override fun authOptions(pigeon_instance: GeckoSession.PromptDelegate.AuthPrompt): GeckoSession.PromptDelegate.AuthPrompt.AuthOptions {
        return pigeon_instance.authOptions
    }

    override fun message(pigeon_instance: GeckoSession.PromptDelegate.AuthPrompt): String? {
        return pigeon_instance.message
    }

    override fun confirmWithPassword(
        pigeon_instance: GeckoSession.PromptDelegate.AuthPrompt,
        password: String
    ): GeckoSession.PromptDelegate.PromptResponse {
        return pigeon_instance.confirm(password)
    }

    override fun confirmWithUsernameAndPassword(
        pigeon_instance: GeckoSession.PromptDelegate.AuthPrompt,
        username: String,
        password: String
    ): GeckoSession.PromptDelegate.PromptResponse {
        return pigeon_instance.confirm(username, password)
    }
}