package dev.zeekr.webview_flutter_geckoview

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.ChecksSdkIntAtLeast
import io.flutter.plugin.common.BinaryMessenger
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension


class ProxyApiRegistrar(
    binaryMessenger: BinaryMessenger,
    private val applicationContext: Context,
    val runtime: GeckoRuntime,
    val webExtensionPort: GeckoResult<WebExtension.Port>,
    val flutterAssetManager: FlutterAssetManager
) : GeckoLibraryPigeonProxyApiRegistrar(binaryMessenger) {
    private var activity: Activity? = null

    val context get() = this.activity ?: this.applicationContext

    fun setActivity(activity: Activity?) {
        this.activity = activity
    }

    // Interface for an injectable SDK version checker.
    @ChecksSdkIntAtLeast(parameter = 0)
    fun sdkIsAtLeast(version: Int): Boolean {
        return Build.VERSION.SDK_INT >= version
    }

    // Added to be overridden for tests. The test implementation calls `callback` immediately, instead
    // of waiting for the main thread to run it.
    fun runOnMainThread(runnable: Runnable) {
        val context = this.context
        if (context is Activity) {
            context.runOnUiThread(runnable)
        } else {
            Handler(Looper.getMainLooper()).post(runnable)
        }
    }

    // For logging exception received from Host -> Dart message calls.
    fun logError(tag: String?, exception: Throwable) {
        Log.e(
            tag,
            exception.javaClass.getSimpleName() + ", Message: " + exception.message + ", Stacktrace: " + Log.getStackTraceString(
                exception
            )
        )
    }

    /** Creates an exception when the `unknown` enum value is passed to a host method.  */
    fun createUnknownEnumException(enumValue: Any): IllegalArgumentException {
        return IllegalArgumentException("$enumValue doesn't represent a native value.")
    }

    /** Creates the error message when a method is called on an unsupported version.  */
    fun createUnsupportedVersionMessage(method: String, versionRequirements: String): String {
        return "$method requires $versionRequirements."
    }

    override fun getPigeonApiGeckoRuntime(): PigeonApiGeckoRuntime {
        return GeckoRuntimeProxyApi(this)
    }

    override fun getPigeonApiGeckoRuntimeSettings(): PigeonApiGeckoRuntimeSettings {
        return GeckoRuntimeSettingsProxyApi(this)
    }

    override fun getPigeonApiStorageController(): PigeonApiStorageController {
        return StorageControllerProxyApi(this)
    }

    override fun getPigeonApiWebExtensionController(): PigeonApiWebExtensionController {
        return WebExtensionControllerProxyApi(this)
    }

    override fun getPigeonApiWebExtension(): PigeonApiWebExtension {
        return WebExtensionProxyApi(this)
    }

    override fun getPigeonApiWebExtensionMessageDelegate(): PigeonApiWebExtensionMessageDelegate {
        return WebExtensionMessageDelegateProxyApi(this)
    }

    override fun getPigeonApiWebExtensionTabDelegate(): PigeonApiWebExtensionTabDelegate {
        return WebExtensionTabDelegateProxyApi(this)
    }

    override fun getPigeonApiWebExtensionCreateTabDetails(): PigeonApiWebExtensionCreateTabDetails {
        return WebExtensionCreateTabDetailsProxyApi(this)
    }

    override fun getPigeonApiWebExtensionPort(): PigeonApiWebExtensionPort {
        return WebExtensionPortProxyApi(this)
    }

    override fun getPigeonApiWebExtensionPortDelegate(): PigeonApiWebExtensionPortDelegate {
        return WebExtensionPortDelegateProxyApi(this)
    }

    override fun getPigeonApiWebExtensionSessionController(): PigeonApiWebExtensionSessionController {
        return WebExtensionSessionControllerProxyApi(this)
    }

    override fun getPigeonApiWebExtensionSessionTabDelegate(): PigeonApiWebExtensionSessionTabDelegate {
        return WebExtensionSessionTabDelegateProxyApi(this)
    }

    override fun getPigeonApiWebExtensionUpdateTabDetails(): PigeonApiWebExtensionUpdateTabDetails {
        return WebExtensionUpdateTabDetailsProxyApi(this)
    }

    override fun getPigeonApiGeckoSession(): PigeonApiGeckoSession {
        return GeckoSessionProxyApi(this)
    }

    override fun getPigeonApiGeckoSessionLoader(): PigeonApiGeckoSessionLoader {
        return GeckoSessionLoaderProxyApi(this)
    }

    override fun getPigeonApiGeckoSessionSettings(): PigeonApiGeckoSessionSettings {
        return GeckoSessionSettingsProxyApi(this)
    }

    override fun getPigeonApiGeckoSessionContentDelegate(): PigeonApiGeckoSessionContentDelegate {
        return GeckoSessionContentDelegateProxyApi(this)
    }

    override fun getPigeonApiGeckoSessionProgressDelegate(): PigeonApiGeckoSessionProgressDelegate {
        return GeckoSessionProgressDelegateProxyApi(this)
    }

    override fun getPigeonApiGeckoSessionScrollDelegate(): PigeonApiGeckoSessionScrollDelegate {
        return GeckoSessionScrollDelegateProxyApi(this)
    }

    override fun getPigeonApiGeckoSessionNavigationDelegate(): PigeonApiGeckoSessionNavigationDelegate {
        return GeckoSessionNavigationDelegateProxyApi(this)
    }

    override fun getPigeonApiGeckoSessionNavigationDelegateLoadRequest(): PigeonApiGeckoSessionNavigationDelegateLoadRequest {
        return GeckoSessionNavigationDelegateLoadRequestProxyApi(this)
    }

    override fun getPigeonApiWebRequestError(): PigeonApiWebRequestError {
        return WebRequestErrorProxyApi(this)
    }

    override fun getPigeonApiGeckoSessionPermissionDelegate(): PigeonApiGeckoSessionPermissionDelegate {
        return GeckoSessionPermissionDelegateProxyApi(this)
    }

    override fun getPigeonApiGeckoSessionPermissionDelegateCallback(): PigeonApiGeckoSessionPermissionDelegateCallback {
        return GeckoSessionPermissionDelegateCallbackProxyApi(this)
    }

    override fun getPigeonApiGeckoSessionPermissionDelegateContentPermission(): PigeonApiGeckoSessionPermissionDelegateContentPermission {
        return GeckoSessionPermissionDelegateContentPermissionProxyApi(this)
    }

    override fun getPigeonApiGeckoSessionPromptDelegate(): PigeonApiGeckoSessionPromptDelegate {
        return GeckoSessionPromptDelegateProxyApi(this)
    }

    override fun getPigeonApiGeckoSessionPromptDelegateBasePrompt(): PigeonApiGeckoSessionPromptDelegateBasePrompt {
        return GeckoSessionPromptDelegateBasePromptProxyApi(this)
    }

    override fun getPigeonApiGeckoSessionPromptDelegateAlertPrompt(): PigeonApiGeckoSessionPromptDelegateAlertPrompt {
        return GeckoSessionPromptDelegateAlertPromptProxyApi(this)
    }

    override fun getPigeonApiGeckoSessionPromptDelegateAuthPrompt(): PigeonApiGeckoSessionPromptDelegateAuthPrompt {
        return GeckoSessionPromptDelegateAuthPromptProxyApi(this)
    }

    override fun getPigeonApiGeckoSessionPromptDelegateAuthPromptAuthOptions(): PigeonApiGeckoSessionPromptDelegateAuthPromptAuthOptions {
        return GeckoSessionPromptDelegateAuthPromptAuthOptionsProxyApi(this)
    }

    override fun getPigeonApiGeckoSessionPromptDelegateButtonPrompt(): PigeonApiGeckoSessionPromptDelegateButtonPrompt {
        return GeckoSessionPromptDelegateButtonPromptProxyApi(this)
    }

    override fun getPigeonApiGeckoSessionPromptDelegateTextPrompt(): PigeonApiGeckoSessionPromptDelegateTextPrompt {
        return GeckoSessionPromptDelegateTextPromptProxyApi(this)
    }

    override fun getPigeonApiGeckoView(): PigeonApiGeckoView {
        return GeckoViewProxyApi(this)
    }

    override fun getPigeonApiPanZoomController(): PigeonApiPanZoomController {
        return PanZoomControllerProxyApi(this)
    }

    override fun getPigeonApiScreenLength(): PigeonApiScreenLength {
        return ScreenLengthProxyApi(this)
    }

    override fun getPigeonApiGeckoWebExecutor(): PigeonApiGeckoWebExecutor {
        return GeckoWebExecutorProxyApi(this)
    }

    override fun getPigeonApiWebRequest(): PigeonApiWebRequest {
        return WebRequestProxyApi(this)
    }

    override fun getPigeonApiWebRequestBuilder(): PigeonApiWebRequestBuilder {
        return WebRequestBuilderProxyApi(this)
    }

    override fun getPigeonApiWebResponse(): PigeonApiWebResponse {
        return WebResponseProxyApi(this)
    }

    override fun getPigeonApiView(): PigeonApiView {
        return ViewProxyApi(this)
    }

    override fun getPigeonApiCertificate(): PigeonApiCertificate {
        return CertificateProxyApi(this)
    }

    override fun getPigeonApiGeckoViewPoint(): PigeonApiGeckoViewPoint {
        return GeckoViewPointProxyApi(this)
    }

    override fun getPigeonApiFlutterAssetManager(): PigeonApiFlutterAssetManager {
        return FlutterAssetManagerProxyApi(this)
    }
}