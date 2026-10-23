package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.GeckoSession

class GeckoSessionPromptDelegateButtonPromptProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiGeckoSessionPromptDelegateButtonPrompt(pigeonRegistrar) {
    override fun title(pigeon_instance: GeckoSession.PromptDelegate.ButtonPrompt): String? {
        return pigeon_instance.title
    }

    override fun message(pigeon_instance: GeckoSession.PromptDelegate.ButtonPrompt): String? {
        return pigeon_instance.message
    }

    override fun confirm(
        pigeon_instance: GeckoSession.PromptDelegate.ButtonPrompt,
        selection: Long
    ): GeckoSession.PromptDelegate.PromptResponse {
        return pigeon_instance.confirm(selection.toInt())
    }
}