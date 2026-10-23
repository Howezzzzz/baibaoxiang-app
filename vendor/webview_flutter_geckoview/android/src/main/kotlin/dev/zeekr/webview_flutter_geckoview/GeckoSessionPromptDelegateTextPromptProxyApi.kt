package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.GeckoSession

class GeckoSessionPromptDelegateTextPromptProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiGeckoSessionPromptDelegateTextPrompt(pigeonRegistrar) {
    override fun title(pigeon_instance: GeckoSession.PromptDelegate.TextPrompt): String? {
        return pigeon_instance.title
    }

    override fun defaultValue(pigeon_instance: GeckoSession.PromptDelegate.TextPrompt): String? {
        return pigeon_instance.defaultValue
    }

    override fun message(pigeon_instance: GeckoSession.PromptDelegate.TextPrompt): String? {
        return pigeon_instance.message
    }

    override fun confirm(
        pigeon_instance: GeckoSession.PromptDelegate.TextPrompt,
        text: String
    ): GeckoSession.PromptDelegate.PromptResponse {
        return pigeon_instance.confirm(text)
    }
}