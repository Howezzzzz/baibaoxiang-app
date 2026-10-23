# webview_flutter_geckoview

The Gecko implementation of [`webview_flutter`][1].

## Usage

This package is not an endorsed implementation of the `webview_flutter` plugin
yet, so it currently requires extra setup to use:

* [Add this package][3] as an explicit dependency of your project, in addition to depending on `webview_flutter`.

Once the step above is complete, you can use the [`webview_flutter`][1] APIs as you normally would.

## Missing features

The following APIs are not implemented yet.

* WebViewController.enableZoom
* WebViewController.setOnConsoleMessage
* NavigationDelegate.onHttpError
* NavigationDelegate.onWebResourceError
* NavigationDelegate.onHttpAuthRequest

## Display Mode

This plugin supports two different platform view display modes. The default display mode is subject
to change in the future, and will not be considered a breaking change, so if you want to ensure a
specific mode, you can set it explicitly.

### Texture Layer Hybrid Composition

This is the current default mode, and is the display mode used by most
plugins starting with Flutter 3.0. This is more performant than Hybrid Composition, but has some
limitations from using an Android [SurfaceTexture](https://developer.android.com/reference/android/graphics/SurfaceTexture).
See:
* https://github.com/flutter/flutter/issues/104889
* https://github.com/flutter/flutter/issues/116954

### Hybrid Composition

This ensures that the WebView will display and work as expected in the edge cases noted above, at
the cost of some performance. See:
* https://docs.flutter.dev/platform-integration/android/platform-views#performance

This can be configured with
`GeckoWebViewWidgetCreationParams.displayWithHybridComposition`. See https://pub.dev/packages/webview_flutter#platform-specific-features
for more details on setting platform-specific features in the main plugin.

[1]: https://pub.dev/packages/webview_flutter
[2]: https://flutter.dev/to/endorsed-federated-plugin
[3]: https://pub.dev/packages/webview_flutter_geckoview/install
