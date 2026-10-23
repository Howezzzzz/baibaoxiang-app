package dev.zeekr.webview_flutter_geckoview

import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.PanZoomController
import org.mozilla.geckoview.WebExtension
import java.security.cert.X509Certificate

class GeckoSessionProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiGeckoSession(pigeonRegistrar) {
    override fun pigeon_defaultConstructor(): GeckoSession {
        return GeckoSession()
    }

    override fun createAsync(): GeckoSession {
        return GeckoSession()
    }

    override fun getDefaultUserAgent(): String {
        return GeckoSession.getDefaultUserAgent()
    }

    override fun panZoomController(pigeon_instance: GeckoSession): PanZoomController {
        return pigeon_instance.panZoomController
    }

    override fun settings(pigeon_instance: GeckoSession): GeckoSessionSettings {
        return pigeon_instance.settings
    }

    override fun webExtensionController(pigeon_instance: GeckoSession): WebExtension.SessionController {
        return pigeon_instance.webExtensionController
    }

    override fun close(pigeon_instance: GeckoSession) {
        pigeon_instance.close()
    }

    override fun containsFormData(
        pigeon_instance: GeckoSession, callback: (Result<Boolean?>) -> Unit
    ) {
        pigeon_instance.containsFormData()
            .accept({ ResultCompat.success(it, callback) }, { ResultCompat.failure(it, callback) })
    }

    override fun didPrintPageContent(
        pigeon_instance: GeckoSession, callback: (Result<Boolean?>) -> Unit
    ) {
        pigeon_instance.didPrintPageContent()
            .accept({ ResultCompat.success(it, callback) }, { ResultCompat.failure(it, callback) })
    }

    override fun exitFullScreen(pigeon_instance: GeckoSession) {
        pigeon_instance.exitFullScreen()
    }

    override fun flushSessionState(pigeon_instance: GeckoSession) {
        pigeon_instance.flushSessionState()
    }

    override fun getContentDelegate(pigeon_instance: GeckoSession): GeckoSession.ContentDelegate? {
        return pigeon_instance.contentDelegate
    }

    override fun getNavigationDelegate(pigeon_instance: GeckoSession): GeckoSession.NavigationDelegate? {
        return pigeon_instance.navigationDelegate
    }

    override fun getPermissionDelegate(pigeon_instance: GeckoSession): GeckoSession.PermissionDelegate? {
        return pigeon_instance.permissionDelegate
    }

    override fun getProgressDelegate(pigeon_instance: GeckoSession): GeckoSession.ProgressDelegate? {
        return pigeon_instance.progressDelegate
    }

    override fun getPromptDelegate(pigeon_instance: GeckoSession): GeckoSession.PromptDelegate? {
        return pigeon_instance.promptDelegate
    }

    override fun getScrollDelegate(pigeon_instance: GeckoSession): GeckoSession.ScrollDelegate? {
        return pigeon_instance.scrollDelegate
    }

    override fun open(pigeon_instance: GeckoSession, runtime: GeckoRuntime) {
        pigeon_instance.open(runtime)
    }

    override fun printPageContent(pigeon_instance: GeckoSession) {
        pigeon_instance.printPageContent()
    }

    override fun purgeHistory(pigeon_instance: GeckoSession) {
        pigeon_instance.purgeHistory()
    }

    override fun setContentDelegate(pigeon_instance: GeckoSession, delegate: GeckoSession.ContentDelegate?) {
        pigeon_instance.contentDelegate = delegate
    }

    override fun setFocused(pigeon_instance: GeckoSession, focused: Boolean) {
        pigeon_instance.setFocused(focused)
    }

    override fun setProgressDelegate(pigeon_instance: GeckoSession, delegate: GeckoSession.ProgressDelegate?) {
        pigeon_instance.progressDelegate = delegate
    }

    override fun setPromptDelegate(pigeon_instance: GeckoSession, delegate: GeckoSession.PromptDelegate?) {
        pigeon_instance.promptDelegate = delegate
    }

    override fun setScrollDelegate(pigeon_instance: GeckoSession, delegate: GeckoSession.ScrollDelegate?) {
        pigeon_instance.scrollDelegate = delegate
    }

    override fun stop(pigeon_instance: GeckoSession) {
        pigeon_instance.stop()
    }

    override fun setNavigationDelegate(pigeon_instance: GeckoSession, delegate: GeckoSession.NavigationDelegate?) {
        pigeon_instance.navigationDelegate = delegate
    }

    override fun setPermissionDelegate(pigeon_instance: GeckoSession, delegate: GeckoSession.PermissionDelegate?) {
        pigeon_instance.permissionDelegate = delegate
    }

    override fun setPriorityHint(pigeon_instance: GeckoSession, priorityHint: Long) {
        pigeon_instance.setPriorityHint(priorityHint.toInt())
    }

    override fun loadUri(pigeon_instance: GeckoSession, uri: String) {
        pigeon_instance.loadUri(uri)
    }

    override fun goBack(pigeon_instance: GeckoSession) {
        pigeon_instance.goBack()
    }

    override fun goForward(pigeon_instance: GeckoSession) {
        pigeon_instance.goForward()
    }

    override fun gotoHistoryIndex(pigeon_instance: GeckoSession, index: Long) {
        pigeon_instance.gotoHistoryIndex(index.toInt())
    }

    override fun hasCookieBannerRuleForBrowsingContextTree(
        pigeon_instance: GeckoSession,
        callback: (Result<Boolean?>) -> Unit
    ) {
        pigeon_instance.hasCookieBannerRuleForBrowsingContextTree().accept(
            { ResultCompat.success(it, callback) },
            { ResultCompat.failure(it, callback) }
        )
    }

    override fun isOpen(pigeon_instance: GeckoSession): Boolean {
        return pigeon_instance.isOpen
    }

    override fun isPdfJs(pigeon_instance: GeckoSession, callback: (Result<Boolean?>) -> Unit) {
        pigeon_instance.isPdfJs.accept(
            { ResultCompat.success(it, callback) },
            { ResultCompat.failure(it, callback) }
        )
    }

    override fun load(pigeon_instance: GeckoSession, request: GeckoSession.Loader) {
        pigeon_instance.load(request)
    }

    override fun reload(pigeon_instance: GeckoSession) {
        pigeon_instance.reload()
    }

    override fun sendMoreWebCompatInfo(pigeon_instance: GeckoSession, info: String, callback: (Result<Unit>) -> Unit) {
        pigeon_instance.sendMoreWebCompatInfo(JSONObject(info)).accept(
            { ResultCompat.success(Unit, callback) },
            { ResultCompat.failure(it, callback) }
        )
    }

    override fun setActive(pigeon_instance: GeckoSession, active: Boolean) {
        pigeon_instance.setActive(active)
    }

    override fun getUserAgent(pigeon_instance: GeckoSession, callback: (Result<String?>) -> Unit) {
        pigeon_instance.userAgent.accept(
            { ResultCompat.success(it, callback) },
            { ResultCompat.failure(it, callback) }
        )
    }

    override fun getWebCompatInfo(pigeon_instance: GeckoSession, callback: (Result<String?>) -> Unit) {
        pigeon_instance.webCompatInfo.accept(
            { ResultCompat.success(it?.toString(), callback) },
            { ResultCompat.failure(it, callback) }
        )
    }
}