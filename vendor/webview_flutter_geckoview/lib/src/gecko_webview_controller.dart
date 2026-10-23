import 'dart:async';

import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:webview_flutter_platform_interface/webview_flutter_platform_interface.dart';

import 'gecko.g.dart' as gecko;
import 'gecko_constants.dart';
import 'gecko_web_extension.dart';
import 'platform_views_service_proxy.dart';
import 'weak_reference_utils.dart';

/// Object specifying parameters for loading a local file in a
/// [GeckoWebViewController].
@immutable
base class GeckoLoadFileParams extends LoadFileParams {
  /// Constructs a [GeckoLoadFileParams], the subclass of a [LoadFileParams].
  GeckoLoadFileParams({
    required String absoluteFilePath,
    this.headers = const <String, String>{},
  }) : super(
         absoluteFilePath: absoluteFilePath.startsWith('file://')
             ? absoluteFilePath
             : Uri.file(absoluteFilePath).toString(),
       );

  /// Constructs a [GeckoLoadFileParams] using a [LoadFileParams].
  factory GeckoLoadFileParams.fromLoadFileParams(
    LoadFileParams params, {
    Map<String, String> headers = const <String, String>{},
  }) {
    return GeckoLoadFileParams(
      absoluteFilePath: params.absoluteFilePath,
      headers: headers,
    );
  }

  /// Additional HTTP headers to be included when loading the local file.
  ///
  /// If not provided at initialization time, doesn't add any additional headers.
  ///
  /// On Gecko, WebView supports adding headers when loading local or remote
  /// content. This can be useful for scenarios like authentication,
  /// content-type overrides, or custom request context.
  final Map<String, String> headers;
}

/// Object specifying creation parameters for creating a [GeckoWebViewController].
///
/// When adding additional fields make sure they can be null or have a default
/// value to avoid breaking changes. See [PlatformWebViewControllerCreationParams] for
/// more information.
@immutable
class GeckoWebViewControllerCreationParams
    extends PlatformWebViewControllerCreationParams {
  /// Creates a new [GeckoWebViewControllerCreationParams] instance.
  const GeckoWebViewControllerCreationParams() : super();

  /// Creates a [GeckoWebViewControllerCreationParams] instance based on [PlatformWebViewControllerCreationParams].
  factory GeckoWebViewControllerCreationParams.fromPlatformWebViewControllerCreationParams(
    // Recommended placeholder to prevent being broken by platform interface.
    // ignore: avoid_unused_constructor_parameters
    PlatformWebViewControllerCreationParams params,
  ) {
    return GeckoWebViewControllerCreationParams();
  }
}

/// Implementation of the [PlatformWebViewController] with the GeckoView API.
class GeckoWebViewController extends PlatformWebViewController {
  final gecko.GeckoRuntime _runtime;
  final GeckoWebExtensionPort _webExtensionPort;
  final gecko.FlutterAssetManager _flutterAssetManager;
  final gecko.GeckoView _view;
  final gecko.GeckoSession _session;
  final Map<String, GeckoJavaScriptChannelParams> _javaScriptChannelParams;

  late final _webExecutor = gecko.GeckoWebExecutor(runtime: _runtime);

  late final _sessionContentDelegate = gecko.GeckoSessionContentDelegate(
    onTitleChange: withWeakReferenceTo(
      this,
      (weakThis) => (_, session, title) {
        weakThis.target?._title = title;
      },
    ),
  );
  late final _sessionNavigationDelegate = gecko.GeckoSessionNavigationDelegate(
    onCanGoBack: withWeakReferenceTo(
      this,
      (weakThis) => (_, session, canGoBack) {
        weakThis.target?._canGoBack = canGoBack;
      },
    ),
    onCanGoForward: withWeakReferenceTo(
      this,
      (weakThis) => (_, session, canGoForward) {
        weakThis.target?._canGoForward = canGoForward;
      },
    ),
    onLoadError: withWeakReferenceTo(
      this,
      (weakThis) => (_, session, uri, error) {
        return null;
      },
    ),
    onLoadRequest: withWeakReferenceTo(
      this,
      (weakThis) => (_, session, request) async {
        final onNavigationRequest =
            weakThis.target?._currentNavigationDelegate?._onNavigationRequest;
        if (onNavigationRequest == null) return .allow;
        final decision = await onNavigationRequest(
          NavigationRequest(url: request.uri, isMainFrame: true),
        );
        switch (decision) {
          case .navigate:
            return .allow;
          case .prevent:
            return .deny;
        }
      },
    ),
    onLocationChange: withWeakReferenceTo(
      this,
      (weakThis) => (_, session, url, perms, hasUserGesture) {
        weakThis.target?._currentUrl = url;
        weakThis.target?._currentNavigationDelegate?._onUrlChange?.call(
          GeckoUrlChange(url: url, hasUserGesture: hasUserGesture),
        );
      },
    ),
    onSubframeLoadRequest: withWeakReferenceTo(
      this,
      (weakThis) => (_, session, request) async {
        final onNavigationRequest =
            weakThis.target?._currentNavigationDelegate?._onNavigationRequest;
        if (onNavigationRequest == null) return .allow;
        final decision = await onNavigationRequest(
          NavigationRequest(url: request.uri, isMainFrame: false),
        );
        switch (decision) {
          case .navigate:
            return .allow;
          case .prevent:
            return .deny;
        }
      },
    ),
  );
  late final _sessionPermissionDelegate = gecko.GeckoSessionPermissionDelegate(
    onAndroidPermissionsRequest: withWeakReferenceTo(
      this,
      (weakThis) => (_, session, permissioins, callback) async {
        debugPrint('onAndroidPermissionsRequest: $permissioins');
        final onPermissionRequestCallback =
            weakThis.target?._onPermissionRequest;
        if (onPermissionRequestCallback == null) {
          return callback.reject();
        } else {
          final types = permissioins
              .map<WebViewPermissionResourceType?>((String type) {
                switch (type) {
                  case PermissionRequestConstants.camera:
                    return WebViewPermissionResourceType.camera;
                  case PermissionRequestConstants.recordAudio:
                    return WebViewPermissionResourceType.microphone;
                }

                // Type not supported.
                return null;
              })
              .whereType<WebViewPermissionResourceType>()
              .toSet();

          // If the request didn't contain any permissions recognized by the
          // implementation, deny by default.
          if (types.isEmpty) {
            return callback.reject();
          }

          onPermissionRequestCallback(
            GeckoWebViewPermissionRequest._(types: types, callback: callback),
          );
        }
      },
    ),
  );
  late final _sessionProgressDelegate = gecko.GeckoSessionProgressDelegate(
    onPageStart: withWeakReferenceTo(
      this,
      (weakThis) => (_, session, url) {
        weakThis.target?._currentNavigationDelegate?._onPageStarted?.call(url);
      },
    ),
    onPageStop: withWeakReferenceTo(
      this,
      (weakThis) => (_, session, success) {
        final url = weakThis.target?._currentUrl ?? '';
        weakThis.target?._currentNavigationDelegate?._onPageFinished?.call(url);
      },
    ),
    onProgressChange: withWeakReferenceTo(
      this,
      (weakThis) => (_, session, progress) {
        weakThis.target?._currentNavigationDelegate?._onProgress?.call(
          progress,
        );
      },
    ),
  );
  late final _sessionPromptDelegate = gecko.GeckoSessionPromptDelegate(
    onAlertPrompt: withWeakReferenceTo(
      this,
      (weakThis) => (_, session, prompt) async {
        final onJavaScriptAlert = weakThis.target?._onJavaScriptAlert;
        if (onJavaScriptAlert != null) {
          final request = JavaScriptAlertDialogRequest(
            message: prompt.message ?? '',
            url: '',
          );
          await onJavaScriptAlert(request);
        }
        return prompt.dismiss();
      },
    ),
    onAuthPrompt: withWeakReferenceTo(
      this,
      (weakThis) => (_, session, prompt) async {
        final onHttpAuthRequest =
            weakThis.target?._currentNavigationDelegate?._onHttpAuthRequest;
        if (onHttpAuthRequest == null) {
          return prompt.dismiss();
        } else {
          final completer =
              Completer<gecko.GeckoSessionPromptDelegatePromptResponse>();
          final uri = prompt.authOptions.uri;
          final host = uri == null ? null : Uri.parse(uri).host;
          onHttpAuthRequest(
            HttpAuthRequest(
              onProceed: (credential) async {
                final response = await prompt.confirmWithUsernameAndPassword(
                  credential.user,
                  credential.password,
                );
                completer.complete(response);
              },
              onCancel: () async {
                final response = await prompt.dismiss();
                completer.complete(response);
              },
              host: host ?? '',
              realm: null,
            ),
          );
          return completer.future;
        }
      },
    ),
    onButtonPrompt: withWeakReferenceTo(
      this,
      (weakThis) => (_, session, prompt) async {
        final onJavaScriptConfirm = weakThis.target?._onJavaScriptConfirm;
        if (onJavaScriptConfirm == null) {
          return prompt.dismiss();
        } else {
          final request = JavaScriptConfirmDialogRequest(
            message: prompt.message ?? '',
            url: '',
          );
          final result = await onJavaScriptConfirm(request);
          final selection = result
              ? GeckoSessionPromptDelegateButtonPromptType.positive
              : GeckoSessionPromptDelegateButtonPromptType.negative;
          return prompt.confirm(selection);
        }
      },
    ),
    onTextPrompt: withWeakReferenceTo(
      this,
      (weakThis) => (_, session, prompt) async {
        final onJavaScriptPrompt = weakThis.target?._onJavaScriptPrompt;
        if (onJavaScriptPrompt == null) {
          return prompt.dismiss();
        } else {
          final request = JavaScriptTextInputDialogRequest(
            message: prompt.message ?? '',
            url: '',
            defaultText: prompt.defaultValue,
          );
          final result = await onJavaScriptPrompt(request);
          return prompt.confirm(result);
        }
      },
    ),
  );
  late final _sessionScrollDelegate = gecko.GeckoSessionScrollDelegate(
    onScrollChanged: withWeakReferenceTo(
      this,
      (weakThis) => (_, session, scrollX, scrollY) {
        final x = scrollX.toDouble();
        final y = scrollY.toDouble();
        weakThis.target?._scrollPosition = Offset(x, y);
        weakThis.target?._onScrollPositionChange?.call(
          ScrollPositionChange(x, y),
        );
      },
    ),
  );

  var _canGoBack = false;
  var _canGoForward = false;

  String? _currentUrl;
  String? _title;
  Offset _scrollPosition;

  GeckoNavigationDelegate? _currentNavigationDelegate;
  void Function(PlatformWebViewPermissionRequest)? _onPermissionRequest;
  void Function(ScrollPositionChange)? _onScrollPositionChange;
  Future<void> Function(JavaScriptAlertDialogRequest request)?
  _onJavaScriptAlert;
  Future<bool> Function(JavaScriptConfirmDialogRequest request)?
  _onJavaScriptConfirm;
  Future<String> Function(JavaScriptTextInputDialogRequest request)?
  _onJavaScriptPrompt;

  GeckoWebViewController(PlatformWebViewControllerCreationParams params)
    : _runtime = gecko.GeckoRuntime.instance,
      _webExtensionPort = GeckoWebExtensionPort(),
      _flutterAssetManager = gecko.FlutterAssetManager.instance,
      _view = gecko.GeckoView(),
      _session = gecko.GeckoSession(),
      _javaScriptChannelParams = {},
      _scrollPosition = Offset.zero,
      super.implementation(
        params is GeckoWebViewControllerCreationParams
            ? params
            : GeckoWebViewControllerCreationParams.fromPlatformWebViewControllerCreationParams(
                params,
              ),
      ) {
    _session.settings.setAllowJavascript(true);
    // Workaround for Bug 1758212
    _session.setContentDelegate(_sessionContentDelegate);
    _session.setNavigationDelegate(_sessionNavigationDelegate);
    _session.setPermissionDelegate(_sessionPermissionDelegate);
    _session.setProgressDelegate(_sessionProgressDelegate);
    _session.setPromptDelegate(_sessionPromptDelegate);
    _session.open(_runtime);
    _session.setActive(true);
    _session.setFocused(true);
    _runtime.webExtensionController.setTabActive(_session, true);
    _view.setSession(_session);
  }

  @override
  Future<void> loadFile(String absoluteFilePath) {
    return loadFileWithParams(
      GeckoLoadFileParams(absoluteFilePath: absoluteFilePath),
    );
  }

  @override
  Future<void> loadFileWithParams(LoadFileParams params) async {
    switch (params) {
      case final GeckoLoadFileParams params:
        final request = gecko.GeckoSessionLoader()
          ..uri(params.absoluteFilePath)
          ..additionalHeaders(params.headers);
        await _session.load(request);
        break;
      default:
        await loadFileWithParams(
          GeckoLoadFileParams.fromLoadFileParams(params),
        );
    }
  }

  @override
  Future<void> loadFlutterAsset(String key) async {
    final assetFilePath = await _flutterAssetManager.getAssetFilePathByName(
      key,
    );
    final pathElements = assetFilePath.split('/');
    final fileName = pathElements.removeLast();
    final paths = await _flutterAssetManager.list(pathElements.join('/'));

    if (!paths.contains(fileName)) {
      throw ArgumentError('Asset for key "$key" not found.', 'key');
    }

    return _session.loadUri('resource://android/assets/$assetFilePath');
  }

  @override
  Future<void> loadHtmlString(String html, {String? baseUrl}) {
    final request = gecko.GeckoSessionLoader()..dataString(html, 'text/html');
    if (baseUrl != null) {
      request.uri(baseUrl);
    }
    return _session.load(request);
  }

  @override
  Future<void> loadRequest(LoadRequestParams params) async {
    if (!params.uri.hasScheme) {
      throw ArgumentError('WebViewRequest#uri is required to have a scheme.');
    }
    switch (params.method) {
      case LoadRequestMethod.get:
        final loader = gecko.GeckoSessionLoader()
          ..uri(params.uri.toString())
          ..additionalHeaders(params.headers);
        return _session.load(loader);
      case LoadRequestMethod.post:
        final builder = gecko.WebRequestBuilder(uri: params.uri.toString())
          ..method(params.method.name.toUpperCase())
          ..bodyBytes(params.body);
        for (final header in params.headers.entries) {
          builder.header(header.key, header.value);
        }
        final request = await builder.build();
        final response = await _webExecutor
            .fetch(request, null)
            .then((e) => ArgumentError.checkNotNull(e));
        final loader = gecko.GeckoSessionLoader()..uri(response.uri);
        final bytes = response.body;
        final mimeType = response.headers['content-type'];
        if (bytes != null) {
          loader.dataBytes(bytes, mimeType);
        }
        return _session.load(loader);
    }
    // The enum comes from a different package, which could get a new value at
    // any time, so a fallback case is necessary. Since there is no reasonable
    // default behavior, throw to alert the client that they need an updated
    // version. This is deliberately outside the switch rather than a `default`
    // so that the linter will flag the switch as needing an update.
    // ignore: dead_code
    throw UnimplementedError(
      'This version of `GeckoWebViewController` currently has no '
      'implementation for HTTP method ${params.method.serialize()} in '
      'loadRequest.',
    );
  }

  @override
  Future<String?> currentUrl() => Future.value(_currentUrl);

  @override
  Future<bool> canGoBack() => Future.value(_canGoBack);

  @override
  Future<bool> canGoForward() => Future.value(_canGoForward);

  @override
  Future<void> goBack() => _session.goBack();

  @override
  Future<void> goForward() => _session.goForward();

  @override
  Future<void> reload() => _session.reload();

  @override
  Future<void> clearCache() => _runtime.storageController.clearData(
    StorageControllerClearFlags.allCaches,
  );

  @override
  Future<void> clearLocalStorage() =>
      _runtime.storageController.clearData(StorageControllerClearFlags.all);

  @override
  Future<void> setPlatformNavigationDelegate(
    covariant GeckoNavigationDelegate handler,
  ) async {
    _currentNavigationDelegate = handler;
  }

  @override
  Future<void> addJavaScriptChannel(
    JavaScriptChannelParams javaScriptChannelParams,
  ) async {
    final geckoJavaScriptChannelParams =
        javaScriptChannelParams is GeckoJavaScriptChannelParams
        ? javaScriptChannelParams
        : GeckoJavaScriptChannelParams.fromJavaScriptChannelParams(
            javaScriptChannelParams,
          );
    final javaScriptChannelName = geckoJavaScriptChannelParams.name;
    if (_javaScriptChannelParams.containsKey(javaScriptChannelName)) {
      throw ArgumentError(
        'A JavaScriptChannel with name `$javaScriptChannelName` already exists.',
      );
    }
    _javaScriptChannelParams[javaScriptChannelName] =
        geckoJavaScriptChannelParams;
    final javascriptChannel = GeckoJavascriptChannel(
      name: javaScriptChannelName,
      onMessageReceived: withWeakReferenceTo(
        this,
        (weakThis) => (message) {
          weakThis.target?._javaScriptChannelParams[javaScriptChannelName]
              ?.onMessageReceived(JavaScriptMessage(message: message));
        },
      ),
    );
    await _webExtensionPort.addJavascriptChannel(javascriptChannel);
  }

  @override
  Future<void> removeJavaScriptChannel(String javaScriptChannelName) async {
    assert(javaScriptChannelName.isNotEmpty);
    if (!_javaScriptChannelParams.containsKey(javaScriptChannelName)) {
      return;
    }
    await _webExtensionPort.removeJavascriptChannel(javaScriptChannelName);
    _javaScriptChannelParams.remove(javaScriptChannelName);
  }

  @override
  Future<void> runJavaScript(String javaScript) =>
      runJavaScriptReturningResult(javaScript);

  @override
  Future<Object> runJavaScriptReturningResult(String javaScript) async {
    final result = await _webExtensionPort.runJavascript(javaScript);
    return result ?? '';
  }

  @override
  Future<String?> getTitle() => Future.value(_title);

  @override
  Future<void> scrollTo(int x, int y) {
    final width = gecko.ScreenLength.fromPixels(value: x.toDouble());
    final height = gecko.ScreenLength.fromPixels(value: y.toDouble());
    return _view.panZoomController.scrollTo(width, height, null);
  }

  @override
  Future<void> scrollBy(int x, int y) {
    final width = gecko.ScreenLength.fromPixels(value: x.toDouble());
    final height = gecko.ScreenLength.fromPixels(value: y.toDouble());
    return _view.panZoomController.scrollBy(width, height, null);
  }

  @override
  Future<Offset> getScrollPosition() => Future.value(_scrollPosition);

  @override
  Future<void> enableZoom(bool enabled) {
    // TODO: implement enableZoom
    return super.enableZoom(enabled);
  }

  @override
  Future<void> setBackgroundColor(Color color) =>
      _view.setBackgroundColor(color.toARGB32());

  @override
  Future<void> setJavaScriptMode(JavaScriptMode javaScriptMode) =>
      _session.settings.setAllowJavascript(javaScriptMode == .unrestricted);

  @override
  Future<String?> getUserAgent() => _session.getUserAgent();

  @override
  Future<void> setUserAgent(String? userAgent) =>
      _session.settings.setUserAgentOverride(userAgent);

  @override
  Future<void> setOnPlatformPermissionRequest(
    void Function(PlatformWebViewPermissionRequest request) onPermissionRequest,
  ) async {
    _onPermissionRequest = onPermissionRequest;
  }

  @override
  Future<void> setOnScrollPositionChange(
    void Function(ScrollPositionChange scrollPositionChange)?
    onScrollPositionChange,
  ) async {
    _onScrollPositionChange = onScrollPositionChange;
    if (onScrollPositionChange != null) {
      return _session.setScrollDelegate(_sessionScrollDelegate);
    } else {
      return _session.setScrollDelegate(null);
    }
  }

  @override
  Future<void> setOnConsoleMessage(
    void Function(JavaScriptConsoleMessage consoleMessage) onConsoleMessage,
  ) {
    // TODO: implement setOnConsoleMessage
    return super.setOnConsoleMessage(onConsoleMessage);
  }

  @override
  Future<void> setOnJavaScriptAlertDialog(
    Future<void> Function(JavaScriptAlertDialogRequest request)
    onJavaScriptAlertDialog,
  ) async {
    _onJavaScriptAlert = onJavaScriptAlertDialog;
  }

  @override
  Future<void> setOnJavaScriptConfirmDialog(
    Future<bool> Function(JavaScriptConfirmDialogRequest request)
    onJavaScriptConfirmDialog,
  ) async {
    _onJavaScriptConfirm = onJavaScriptConfirmDialog;
  }

  @override
  Future<void> setOnJavaScriptTextInputDialog(
    Future<String> Function(JavaScriptTextInputDialogRequest request)
    onJavaScriptTextInputDialog,
  ) async {
    _onJavaScriptPrompt = onJavaScriptTextInputDialog;
  }

  @override
  Future<void> setVerticalScrollBarEnabled(bool enabled) =>
      _view.setVerticalScrollBarEnabled(enabled);

  @override
  Future<void> setHorizontalScrollBarEnabled(bool enabled) =>
      _view.setHorizontalScrollBarEnabled(enabled);

  @override
  bool supportsSetScrollBarsEnabled() => true;

  @override
  Future<void> setOverScrollMode(WebViewOverScrollMode mode) => switch (mode) {
    .always => _view.setOverScrollMode(.always),
    .ifContentScrolls => _view.setOverScrollMode(.ifContentScrolls),
    .never => _view.setOverScrollMode(.never),
    // This prevents future additions from causing a breaking change.
    // ignore: unreachable_switch_case
    _ => throw UnsupportedError('Android does not support $mode.'),
  };

  // TODO(yanshouwang): Make this override once the method is added to the platform interface.
  // See https://github.com/flutter/flutter/issues/119616
  Future<void> dispose() async {
    await _view.releaseSession();
    await _session.close();
  }
}

/// Object specifying creation parameters for creating a [GeckoWebViewWidget].
///
/// When adding additional fields make sure they can be null or have a default
/// value to avoid breaking changes. See [PlatformWebViewWidgetCreationParams] for
/// more information.
@immutable
class GeckoWebViewWidgetCreationParams
    extends PlatformWebViewWidgetCreationParams {
  /// Creates [GeckoWebViewWidgetCreationParams].
  const GeckoWebViewWidgetCreationParams({
    super.key,
    required super.controller,
    super.layoutDirection,
    super.gestureRecognizers,
    this.displayWithHybridComposition = false,
    @visibleForTesting
    this.platformViewsServiceProxy = const PlatformViewsServiceProxy(),
  });

  /// Constructs a [GeckoWebViewWidgetCreationParams] using a
  /// [PlatformWebViewWidgetCreationParams].
  GeckoWebViewWidgetCreationParams.fromPlatformWebViewWidgetCreationParams(
    PlatformWebViewWidgetCreationParams params, {
    bool displayWithHybridComposition = false,
    @visibleForTesting
    PlatformViewsServiceProxy platformViewsServiceProxy =
        const PlatformViewsServiceProxy(),
  }) : this(
         key: params.key,
         controller: params.controller,
         layoutDirection: params.layoutDirection,
         gestureRecognizers: params.gestureRecognizers,
         displayWithHybridComposition: displayWithHybridComposition,
         platformViewsServiceProxy: platformViewsServiceProxy,
       );

  /// Proxy that provides access to the platform views service.
  ///
  /// This service allows creating and controlling platform-specific views.
  @visibleForTesting
  final PlatformViewsServiceProxy platformViewsServiceProxy;

  /// Whether the [WebView] will be displayed using the Hybrid Composition
  /// PlatformView implementation.
  ///
  /// For most use cases, this flag should be set to false. Hybrid Composition
  /// can have performance costs but doesn't have the limitation of rendering to
  /// an Android SurfaceTexture. See
  /// * https://docs.flutter.dev/platform-integration/android/platform-views#performance
  /// * https://github.com/flutter/flutter/issues/104889
  /// * https://github.com/flutter/flutter/issues/116954
  ///
  /// Defaults to false.
  final bool displayWithHybridComposition;

  @override
  int get hashCode => Object.hash(
    controller,
    layoutDirection,
    displayWithHybridComposition,
    platformViewsServiceProxy,
  );

  @override
  bool operator ==(Object other) {
    return other is GeckoWebViewWidgetCreationParams &&
        controller == other.controller &&
        layoutDirection == other.layoutDirection &&
        displayWithHybridComposition == other.displayWithHybridComposition &&
        platformViewsServiceProxy == other.platformViewsServiceProxy;
  }
}

/// An implementation of [PlatformWebViewWidget] with the Gecko WebView API.
class GeckoWebViewWidget extends PlatformWebViewWidget {
  /// Constructs a [GeckoWebViewWidget].
  GeckoWebViewWidget(PlatformWebViewWidgetCreationParams params)
    : super.implementation(
        params is GeckoWebViewWidgetCreationParams
            ? params
            : GeckoWebViewWidgetCreationParams.fromPlatformWebViewWidgetCreationParams(
                params,
              ),
      );

  GeckoWebViewWidgetCreationParams get _geckoParams =>
      params as GeckoWebViewWidgetCreationParams;

  @override
  Widget build(BuildContext context) {
    // Setting a default key using `params` ensures the `PlatformViewLink`
    // recreates the PlatformView when changes are made.
    final key =
        _geckoParams.key ??
        ValueKey<GeckoWebViewWidgetCreationParams>(
          params as GeckoWebViewWidgetCreationParams,
        );
    return PlatformViewLink(
      key: key,
      viewType: 'plugins.flutter.io/webview',
      surfaceFactory: (context, controller) {
        return AndroidViewSurface(
          controller: controller as AndroidViewController,
          gestureRecognizers: _geckoParams.gestureRecognizers,
          hitTestBehavior: PlatformViewHitTestBehavior.opaque,
        );
      },
      onCreatePlatformView: (params) {
        return _initAndroidView(
            params,
            displayWithHybridComposition:
                _geckoParams.displayWithHybridComposition,
            platformViewsServiceProxy: _geckoParams.platformViewsServiceProxy,
            view: (_geckoParams.controller as GeckoWebViewController)._view,
            layoutDirection: _geckoParams.layoutDirection,
          )
          ..addOnPlatformViewCreatedListener(params.onPlatformViewCreated)
          ..create();
      },
    );
  }
}

/// Object specifying creation parameters for creating a [GeckoNavigationDelegate].
///
/// When adding additional fields make sure they can be null or have a default
/// value to avoid breaking changes. See [PlatformNavigationDelegateCreationParams] for
/// more information.
@immutable
class GeckoNavigationDelegateCreationParams
    extends PlatformNavigationDelegateCreationParams {
  /// Creates a new [GeckoNavigationDelegateCreationParams] instance.
  const GeckoNavigationDelegateCreationParams._() : super();

  /// Creates a [GeckoNavigationDelegateCreationParams] instance based on [PlatformNavigationDelegateCreationParams].
  factory GeckoNavigationDelegateCreationParams.fromPlatformNavigationDelegateCreationParams(
    // Recommended placeholder to prevent being broken by platform interface.
    // ignore: avoid_unused_constructor_parameters
    PlatformNavigationDelegateCreationParams params,
  ) {
    return const GeckoNavigationDelegateCreationParams._();
  }
}

/// A place to register callback methods responsible to handle navigation events
/// triggered by the [gecko.GeckoView].
class GeckoNavigationDelegate extends PlatformNavigationDelegate {
  /// Creates a new [GeckoNavigationDelegate].
  GeckoNavigationDelegate(PlatformNavigationDelegateCreationParams params)
    : super.implementation(
        params is GeckoNavigationDelegateCreationParams
            ? params
            : GeckoNavigationDelegateCreationParams.fromPlatformNavigationDelegateCreationParams(
                params,
              ),
      );

  PageEventCallback? _onPageStarted;
  PageEventCallback? _onPageFinished;
  ProgressCallback? _onProgress;
  UrlChangeCallback? _onUrlChange;
  NavigationRequestCallback? _onNavigationRequest;
  HttpAuthRequestCallback? _onHttpAuthRequest;

  @override
  Future<void> setOnPageStarted(PageEventCallback onPageStarted) async {
    _onPageStarted = onPageStarted;
  }

  @override
  Future<void> setOnPageFinished(PageEventCallback onPageFinished) async {
    _onPageFinished = onPageFinished;
  }

  @override
  Future<void> setOnProgress(ProgressCallback onProgress) async {
    _onProgress = onProgress;
  }

  @override
  Future<void> setOnUrlChange(UrlChangeCallback onUrlChange) async {
    _onUrlChange = onUrlChange;
  }

  @override
  Future<void> setOnNavigationRequest(
    NavigationRequestCallback onNavigationRequest,
  ) async {
    _onNavigationRequest = onNavigationRequest;
  }

  @override
  Future<void> setOnHttpAuthRequest(
    HttpAuthRequestCallback onHttpAuthRequest,
  ) async {
    _onHttpAuthRequest = onHttpAuthRequest;
  }

  @override
  Future<void> setOnHttpError(HttpResponseErrorCallback onHttpError) {
    // TODO: implement setOnHttpError
    return super.setOnHttpError(onHttpError);
  }

  @override
  Future<void> setOnWebResourceError(
    WebResourceErrorCallback onWebResourceError,
  ) {
    // TODO: implement setOnWebResourceError
    return super.setOnWebResourceError(onWebResourceError);
  }

  @override
  Future<void> setOnSSlAuthError(SslAuthErrorCallback onSslAuthError) {
    // TODO: implement setOnSSlAuthError
    return super.setOnSSlAuthError(onSslAuthError);
  }
}

/// Gecko details of the change to a web view's url.
class GeckoUrlChange extends UrlChange {
  /// Constructs an [GeckoUrlChange].
  const GeckoUrlChange({required super.url, required this.hasUserGesture});

  /// Whether or not there was an active user gesture when the location change was requested.
  final bool hasUserGesture;
}

/// Gecko implementation of [PlatformWebViewPermissionRequest].
class GeckoWebViewPermissionRequest extends PlatformWebViewPermissionRequest {
  const GeckoWebViewPermissionRequest._({
    required super.types,
    required gecko.GeckoSessionPermissionDelegateCallback callback,
  }) : _callback = callback;

  final gecko.GeckoSessionPermissionDelegateCallback _callback;

  @override
  Future<void> grant() {
    return _callback.grant();
  }

  @override
  Future<void> deny() {
    return _callback.reject();
  }
}

/// An implementation of [JavaScriptChannelParams] with the GeckoView API.
@immutable
class GeckoJavaScriptChannelParams extends JavaScriptChannelParams {
  /// Constructs a [GeckoJavaScriptChannelParams].
  GeckoJavaScriptChannelParams({
    required super.name,
    required super.onMessageReceived,
  }) : assert(name.isNotEmpty);

  /// Constructs a [GeckoJavaScriptChannelParams] using a
  /// [JavaScriptChannelParams].
  GeckoJavaScriptChannelParams.fromJavaScriptChannelParams(
    JavaScriptChannelParams params,
  ) : this(name: params.name, onMessageReceived: params.onMessageReceived);
}

AndroidViewController _initAndroidView(
  PlatformViewCreationParams params, {
  required bool displayWithHybridComposition,
  required PlatformViewsServiceProxy platformViewsServiceProxy,
  required gecko.View view,
  TextDirection layoutDirection = TextDirection.ltr,
}) {
  final int identifier = gecko.PigeonInstanceManager.instance.getIdentifier(
    view,
  )!;

  if (displayWithHybridComposition) {
    return platformViewsServiceProxy.initExpensiveAndroidView(
      id: params.id,
      viewType: 'plugins.flutter.io/webview',
      layoutDirection: layoutDirection,
      creationParams: identifier,
      creationParamsCodec: const StandardMessageCodec(),
    );
  } else {
    return platformViewsServiceProxy.initSurfaceAndroidView(
      id: params.id,
      viewType: 'plugins.flutter.io/webview',
      layoutDirection: layoutDirection,
      creationParams: identifier,
      creationParamsCodec: const StandardMessageCodec(),
    );
  }
}
