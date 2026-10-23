//
// Generated file. Do not edit.
// This file is generated from template in file `flutter_tools/lib/src/flutter_plugins.dart`.
//

// @dart = 3.13

import 'dart:io'; // flutter_ignore: dart_io_import.
import 'package:url_launcher_android/url_launcher_android.dart' as url_launcher_android;
import 'package:webview_flutter_geckoview/webview_flutter_geckoview.dart' as webview_flutter_geckoview;
import 'package:url_launcher_ios/url_launcher_ios.dart' as url_launcher_ios;
import 'package:webview_flutter_wkwebview/webview_flutter_wkwebview.dart' as webview_flutter_wkwebview;
import 'package:url_launcher_linux/url_launcher_linux.dart' as url_launcher_linux;
import 'package:url_launcher_macos/url_launcher_macos.dart' as url_launcher_macos;
import 'package:webview_flutter_wkwebview/webview_flutter_wkwebview.dart' as webview_flutter_wkwebview;
import 'package:url_launcher_windows/url_launcher_windows.dart' as url_launcher_windows;

@pragma('vm:entry-point')
class _PluginRegistrant {

  @pragma('vm:entry-point')
  static void register() {
    if (Platform.isAndroid) {
      try {
        url_launcher_android.UrlLauncherAndroid.registerWith();
      } catch (err) {
        print(
          '`url_launcher_android` threw an error: $err. '
          'The app may not function as expected until you remove this plugin from pubspec.yaml'
        );
      }

      try {
        webview_flutter_geckoview.GeckoWebViewPlatform.registerWith();
      } catch (err) {
        print(
          '`webview_flutter_geckoview` threw an error: $err. '
          'The app may not function as expected until you remove this plugin from pubspec.yaml'
        );
      }

    } else if (Platform.isIOS) {
      try {
        url_launcher_ios.UrlLauncherIOS.registerWith();
      } catch (err) {
        print(
          '`url_launcher_ios` threw an error: $err. '
          'The app may not function as expected until you remove this plugin from pubspec.yaml'
        );
      }

      try {
        webview_flutter_wkwebview.WebKitWebViewPlatform.registerWith();
      } catch (err) {
        print(
          '`webview_flutter_wkwebview` threw an error: $err. '
          'The app may not function as expected until you remove this plugin from pubspec.yaml'
        );
      }

    } else if (Platform.isLinux) {
      try {
        url_launcher_linux.UrlLauncherLinux.registerWith();
      } catch (err) {
        print(
          '`url_launcher_linux` threw an error: $err. '
          'The app may not function as expected until you remove this plugin from pubspec.yaml'
        );
      }

    } else if (Platform.isMacOS) {
      try {
        url_launcher_macos.UrlLauncherMacOS.registerWith();
      } catch (err) {
        print(
          '`url_launcher_macos` threw an error: $err. '
          'The app may not function as expected until you remove this plugin from pubspec.yaml'
        );
      }

      try {
        webview_flutter_wkwebview.WebKitWebViewPlatform.registerWith();
      } catch (err) {
        print(
          '`webview_flutter_wkwebview` threw an error: $err. '
          'The app may not function as expected until you remove this plugin from pubspec.yaml'
        );
      }

    } else if (Platform.isWindows) {
      try {
        url_launcher_windows.UrlLauncherWindows.registerWith();
      } catch (err) {
        print(
          '`url_launcher_windows` threw an error: $err. '
          'The app may not function as expected until you remove this plugin from pubspec.yaml'
        );
      }

    }
  }
}
