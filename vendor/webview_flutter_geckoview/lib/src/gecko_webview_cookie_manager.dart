import 'package:meta/meta.dart';
import 'package:webview_flutter_platform_interface/webview_flutter_platform_interface.dart';

import 'gecko.g.dart' as gecko;
import 'gecko_constants.dart';
import 'gecko_web_extension.dart';

/// Object specifying creation parameters for creating a [GeckoWebViewCookieManager].
///
/// When adding additional fields make sure they can be null or have a default
/// value to avoid breaking changes. See [PlatformWebViewCookieManagerCreationParams] for
/// more information.
@immutable
class GeckoWebViewCookieManagerCreationParams
    extends PlatformWebViewCookieManagerCreationParams {
  /// Creates a new [GeckoWebViewCookieManagerCreationParams] instance.
  const GeckoWebViewCookieManagerCreationParams._(
    // This parameter prevents breaking changes later.
    // ignore: avoid_unused_constructor_parameters
    PlatformWebViewCookieManagerCreationParams params,
  ) : super();

  /// Creates a [GeckoWebViewCookieManagerCreationParams] instance based on [PlatformWebViewCookieManagerCreationParams].
  factory GeckoWebViewCookieManagerCreationParams.fromPlatformWebViewCookieManagerCreationParams(
    PlatformWebViewCookieManagerCreationParams params,
  ) {
    return GeckoWebViewCookieManagerCreationParams._(params);
  }
}

/// Handles all cookie operations for the GeckoView.
class GeckoWebViewCookieManager extends PlatformWebViewCookieManager {
  final gecko.GeckoRuntime _runtime;
  final GeckoWebExtensionPort _webExtensionPort;

  late final _storageController = _runtime.storageController;

  /// Creates a new [GeckoWebViewCookieManager].
  GeckoWebViewCookieManager(PlatformWebViewCookieManagerCreationParams params)
    : _runtime = gecko.GeckoRuntime.instance,
      _webExtensionPort = GeckoWebExtensionPort(),
      super.implementation(
        params is GeckoWebViewCookieManagerCreationParams
            ? params
            : GeckoWebViewCookieManagerCreationParams.fromPlatformWebViewCookieManagerCreationParams(
                params,
              ),
      );

  @override
  Future<bool> clearCookies() async {
    try {
      await _storageController.clearData(StorageControllerClearFlags.cookies);
      return true;
    } catch (e) {
      return false;
    }
  }

  @override
  Future<void> setCookie(WebViewCookie cookie) async {
    final details = GeckoCookieDetails(
      domain: cookie.domain,
      name: cookie.name,
      path: cookie.path,
      value: cookie.value,
    );
    await _webExtensionPort.setCookie(details);
  }
}
