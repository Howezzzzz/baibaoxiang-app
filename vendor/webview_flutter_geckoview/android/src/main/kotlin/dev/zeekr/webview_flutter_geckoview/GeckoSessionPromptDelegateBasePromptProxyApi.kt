package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.GeckoSession

class GeckoSessionPromptDelegateBasePromptProxyApi(pigeonRegistrar: GeckoLibraryPigeonProxyApiRegistrar) :
    PigeonApiGeckoSessionPromptDelegateBasePrompt(pigeonRegistrar) {
    override fun dismiss(pigeon_instance: GeckoSession.PromptDelegate.BasePrompt): GeckoSession.PromptDelegate.PromptResponse {
        return pigeon_instance.dismiss()
    }

    override fun isComplete(pigeon_instance: GeckoSession.PromptDelegate.BasePrompt): Boolean {
        return pigeon_instance.isComplete
    }
}