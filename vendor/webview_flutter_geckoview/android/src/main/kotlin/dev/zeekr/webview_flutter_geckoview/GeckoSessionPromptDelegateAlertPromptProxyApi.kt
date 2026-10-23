package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.GeckoSession

class GeckoSessionPromptDelegateAlertPromptProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiGeckoSessionPromptDelegateAlertPrompt(pigeonRegistrar) {
    override fun title(pigeon_instance: GeckoSession.PromptDelegate.AlertPrompt): String? {
        return pigeon_instance.title
    }

    override fun message(pigeon_instance: GeckoSession.PromptDelegate.AlertPrompt): String? {
        return pigeon_instance.message
    }
}