package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.GeckoRuntimeSettings

class GeckoRuntimeSettingsProxyApi(piegonRegistrar: ProxyApiRegistrar) :
    PigeonApiGeckoRuntimeSettings(piegonRegistrar) {
    override fun getConsoleOutputEnabled(pigeon_instance: GeckoRuntimeSettings): Boolean {
        return pigeon_instance.consoleOutputEnabled
    }

    override fun setConsoleOutputEnabled(pigeon_instance: GeckoRuntimeSettings, value: Boolean): GeckoRuntimeSettings {
        return pigeon_instance.setConsoleOutputEnabled(value)
    }
}