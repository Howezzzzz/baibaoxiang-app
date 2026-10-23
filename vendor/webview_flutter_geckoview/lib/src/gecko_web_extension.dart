import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';

import 'gecko.g.dart' as gecko;
import 'weak_reference_utils.dart';

var _id = 0;
final _completers = <int, Completer>{};

class GeckoWebExtensionPort {
  static GeckoWebExtensionPort? _instance;

  final Completer<gecko.WebExtensionPort> _webExtensionPort;
  final _javascriptChannels = <String, GeckoJavascriptChannel>{};

  late final _webExtensioinPortDelegate = gecko.WebExtensionPortDelegate(
    onDisconnect: withWeakReferenceTo(
      this,
      (weakThis) => (_, webExtensionPort) {
        debugPrint('webExtensionPort disconnected');
      },
    ),
    onPortMessage: withWeakReferenceTo(
      this,
      (weakThis) => (_, message, webExtensionPort) {
        debugPrint('webExtensionPort received: $message');
        final target = weakThis.target;
        if (target == null) return;
        final object = json.decode(message) as Map<String, dynamic>;
        final category = object['category'] as int?;
        final type = object['type'] as int?;
        if (category == null || type == null) return;
        switch (category) {
          case 0: // command
            {
              break;
            }
          case 1: // reply
            {
              final id = object['id'] as int?;
              if (id == null) return;
              final completer = _completers.remove(id);
              if (completer == null) return;
              final error = object['error'];
              if (error == null) {
                final result = object['result'];
                completer.complete(result);
              } else {
                completer.completeError(error);
              }
              break;
            }
          case 2: // event
            {
              switch (type) {
                case 0: // javascriptChannel event
                  {
                    final javascriptChannelName = object['name'] as String?;
                    if (javascriptChannelName == null) return;
                    final javascriptChannelMessage =
                        object['message'] as String?;
                    if (javascriptChannelMessage == null) return;
                    final javascriptChannel =
                        target._javascriptChannels[javascriptChannelName];
                    if (javascriptChannel == null) return;
                    javascriptChannel.onMessageReceived(
                      javascriptChannelMessage,
                    );
                    break;
                  }
                default:
                  {
                    break;
                  }
              }
              break;
            }
          default:
            {
              break;
            }
        }
      },
    ),
  );

  GeckoWebExtensionPort._() : _webExtensionPort = Completer() {
    gecko.WebExtensionPort.getAsync()
        .then((e) => ArgumentError.checkNotNull(e))
        .then(
          withWeakReferenceTo(
            this,
            (weakThis) => (webExtensionPort) {
              final target = weakThis.target;
              if (target == null) return;
              webExtensionPort.setDelegate(target._webExtensioinPortDelegate);
              target._webExtensionPort.complete(webExtensionPort);
            },
          ),
          onError: withWeakReferenceTo(
            this,
            (weakThis) => (error) {
              weakThis.target?._webExtensionPort.completeError(error);
            },
          ),
        );
  }

  factory GeckoWebExtensionPort() {
    final instance = _instance;
    if (instance == null) {
      final webExtensionPort = GeckoWebExtensionPort._();
      _instance = webExtensionPort;
      return webExtensionPort;
    } else {
      return instance;
    }
  }

  Future<void> addJavascriptChannel(
    GeckoJavascriptChannel javascriptChannel,
  ) async {
    final javascriptChannelName = javascriptChannel.name;
    final webExtensionPort = await _webExtensionPort.future;
    final int id = _id++;
    await webExtensionPort.postMessage(
      json.encode({
        'category': 0,
        'type': 0,
        'id': id,
        'name': javascriptChannelName,
      }),
    );
    final completer = Completer<void>();
    _completers[id] = completer;
    await completer.future;
    _javascriptChannels[javascriptChannelName] = javascriptChannel;
  }

  Future<void> removeJavascriptChannel(String javascriptChannelName) async {
    final webExtensionPort = await _webExtensionPort.future;
    final int id = _id++;
    await webExtensionPort.postMessage(
      json.encode({
        'category': 0,
        'type': 1,
        'id': id,
        'name': javascriptChannelName,
      }),
    );
    final completer = Completer<void>();
    _completers[id] = completer;
    await completer.future;
    _javascriptChannels.remove(javascriptChannelName);
  }

  Future<Object?> runJavascript(String javascript) async {
    final webExtensionPort = await _webExtensionPort.future;
    final int id = _id++;
    await webExtensionPort.postMessage(
      json.encode({
        'category': 0,
        'type': 2,
        'id': id,
        'javascript': javascript,
      }),
    );
    final completer = Completer();
    _completers[id] = completer;
    final result = await completer.future;
    debugPrint('runJavaScript result: $result');
    return result;
  }

  Future<void> setCookie(GeckoCookieDetails details) async {
    final webExtensionPort = await _webExtensionPort.future;
    final int id = _id++;
    await webExtensionPort.postMessage(
      json.encode({
        'category': 0,
        'type': 3,
        'id': id,
        'details': {
          'url': details.url,
          'name': details.name,
          'value': details.value,
        },
      }),
    );
    final completer = Completer();
    _completers[id] = completer;
    final result = await completer.future;
    debugPrint('setCookie result: $result');
  }
}

class GeckoJavascriptChannel {
  final String name;
  final void Function(String message) onMessageReceived;

  GeckoJavascriptChannel({required this.name, required this.onMessageReceived});
}

class GeckoCookieDetails {
  final String domain;
  final String name;
  final String path;
  final String value;

  const GeckoCookieDetails({
    required this.domain,
    required this.name,
    required this.path,
    required this.value,
  });

  String get url => 'https://$domain$path';
}
