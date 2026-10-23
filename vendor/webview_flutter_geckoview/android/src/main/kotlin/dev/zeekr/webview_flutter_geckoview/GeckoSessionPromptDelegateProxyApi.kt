package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

class GeckoSessionPromptDelegateProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiGeckoSessionPromptDelegate(pigeonRegistrar) {
    override fun pigeon_defaultConstructor(): GeckoSession.PromptDelegate {
        return object : GeckoSession.PromptDelegate {
            override fun onAlertPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.AlertPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                this@GeckoSessionPromptDelegateProxyApi.onAlertPrompt(this, session, prompt) {
                    it.onSuccess { response -> result.complete(response) }
                        .onFailure { error -> result.completeExceptionally(error) }
                }
                return result
            }

            override fun onAuthPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.AuthPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                this@GeckoSessionPromptDelegateProxyApi.onAuthPrompt(this, session, prompt) {
                    it.onSuccess { response -> result.complete(response) }
                        .onFailure { error -> result.completeExceptionally(error) }
                }
                return result
            }

            override fun onButtonPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.ButtonPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                this@GeckoSessionPromptDelegateProxyApi.onButtonPrompt(this, session, prompt) {
                    it.onSuccess { response -> result.complete(response) }
                        .onFailure { error -> result.completeExceptionally(error) }
                }
                return result
            }

            override fun onTextPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.TextPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                this@GeckoSessionPromptDelegateProxyApi.onTextPrompt(this, session, prompt) {
                    it.onSuccess { response -> result.complete(response) }
                        .onFailure { error -> result.completeExceptionally(error) }
                }
                return result
            }
        }
    }
}