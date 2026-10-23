// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'gecko.g.dart';

class GeckoSessionPromptDelegateButtonPromptType {
  /// Index of positive response button (eg, "Yes", "OK")
  static const int positive = 0;

  /// Index of negative response button (eg, "No", "Cancel")
  static const int negative = 2;
}

class GeckoViewBackend {
  static const int surfaceView = 1;
  static const int textureView = 2;
}

class PanZoomControllerScrollBehavior {
  static const int smooth = 0;
  static const int auto = 1;
}

/// Class constants for [GeckoSessionPermissionDelegateCallback].
///
/// Since the Dart [GeckoSessionPermissionDelegateCallback] is generated, the constants for the class
/// are added here.
class PermissionRequestConstants {
  static const String camera = 'android.permission.CAMERA';
  static const String recordAudio = 'android.permission.RECORD_AUDIO';
}

class StorageControllerClearFlags {
  static const int cookies = 1;
  static const int networkCache = 2;
  static const int imageCache = 4;
  static const int domStorages = 16;
  static const int authSessions = 32;
  static const int permissions = 64;
  static const int allCaches = 6;
  static const int siteSettings = 192;
  static const int siteData = 471;
  static const int all = 512;
}

class WebRequestErrorCategory {
  static const int unknown = 1;
  static const int security = 2;
  static const int network = 3;
  static const int content = 4;
  static const int uri = 5;
  static const int proxy = 6;
  static const int safebrowsing = 7;
}

class WebRequestErrorCode {
  static const int unknown = 17;
  static const int securitySsl = 34;
  static const int securityBadCert = 50;
  static const int netInterrupt = 35;
  static const int netTimeout = 51;
  static const int connectionRefused = 67;
  static const int unknownSocketType = 83;
  static const int redirectLoop = 99;
  static const int offline = 115;
  static const int portBlocked = 131;
  static const int netReset = 147;
  static const int httpsOnly = 163;
  static const int badHstsCert = 179;
  static const int unsafeContentType = 36;
  static const int corruptedContent = 52;
  static const int contentCrashed = 68;
  static const int invalidContentEncoding = 84;
  static const int unkonwnHost = 37;
  static const int malformedUri = 53;
  static const int unknownProtocol = 69;
  static const int fileNotFound = 85;
  static const int fileAccessDenied = 101;
  static const int dataUriTooLong = 117;
  static const int proxyConnectionRefused = 38;
  static const int unknownProxyHost = 54;
  static const int safebrowsingMalwareUri = 39;
  static const int safebrowsingUnwantedUri = 55;
  static const int safebrowsingHarmfulUri = 71;
  static const int safebrowsingPhishingUri = 87;
}
