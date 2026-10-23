package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.GeckoSessionSettings

class GeckoSessionSettingsProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiGeckoSessionSettings(pigeonRegistrar) {
    override fun getAllowJavascript(pigeon_instance: GeckoSessionSettings): Boolean {
        return pigeon_instance.allowJavascript
    }

    override fun getChromeUri(pigeon_instance: GeckoSessionSettings): String? {
        return pigeon_instance.chromeUri
    }

    override fun getContextId(pigeon_instance: GeckoSessionSettings): String? {
        return pigeon_instance.contextId
    }

    override fun getDisplayMode(pigeon_instance: GeckoSessionSettings): Long {
        return pigeon_instance.displayMode.toLong()
    }

    override fun getFullAccessibilityTree(pigeon_instance: GeckoSessionSettings): Boolean {
        return pigeon_instance.fullAccessibilityTree
    }

    override fun getScreenId(pigeon_instance: GeckoSessionSettings): Long {
        return pigeon_instance.screenId.toLong()
    }

    override fun getSuspendMediaWhenInactive(pigeon_instance: GeckoSessionSettings): Boolean {
        return pigeon_instance.suspendMediaWhenInactive
    }

    override fun getUsePrivateMode(pigeon_instance: GeckoSessionSettings): Boolean {
        return pigeon_instance.usePrivateMode
    }

    override fun getUserAgentMode(pigeon_instance: GeckoSessionSettings): Long {
        return pigeon_instance.userAgentMode.toLong()
    }

    override fun getUserAgentOverride(pigeon_instance: GeckoSessionSettings): String? {
        return pigeon_instance.userAgentOverride
    }

    override fun getUserTrackingProtection(pigeon_instance: GeckoSessionSettings): Boolean {
        return pigeon_instance.useTrackingProtection
    }

    override fun getViewportMode(pigeon_instance: GeckoSessionSettings): Long {
        return pigeon_instance.viewportMode.toLong()
    }

    override fun setAllowJavascript(pigeon_instance: GeckoSessionSettings, value: Boolean) {
        pigeon_instance.allowJavascript = value
    }

    override fun setDisplayMode(pigeon_instance: GeckoSessionSettings, value: Long) {
        pigeon_instance.displayMode = value.toInt()
    }

    override fun setFullAccessibilityTree(pigeon_instance: GeckoSessionSettings, value: Boolean) {
        pigeon_instance.fullAccessibilityTree = value
    }

    override fun setSuspendMediaWhenInactive(pigeon_instance: GeckoSessionSettings, value: Boolean) {
        pigeon_instance.suspendMediaWhenInactive = value
    }

    override fun setUserAgentMode(pigeon_instance: GeckoSessionSettings, value: Long) {
        pigeon_instance.userAgentMode = value.toInt()
    }

    override fun setUserAgentOverride(pigeon_instance: GeckoSessionSettings, value: String?) {
        pigeon_instance.userAgentOverride = value
    }

    override fun setUseTrackingProtection(pigeon_instance: GeckoSessionSettings, value: Boolean) {
        pigeon_instance.useTrackingProtection = value
    }

    override fun setViewportMode(pigeon_instance: GeckoSessionSettings, value: Long) {
        pigeon_instance.viewportMode = value.toInt()
    }
}