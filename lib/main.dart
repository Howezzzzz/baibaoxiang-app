import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';

/// 百宝箱安卓壳 —— 把 https://m.tbox.cn/agents/home 装进独立 App。
/// 非官方、自用。站内跳转留在壳内，外站/外部 scheme 交系统。
const String kHomeUrl = 'https://m.tbox.cn/agents/home';

/// 站内域（含子域）：其余一律视为外链 → 系统浏览器。
const List<String> kInternalDomains = <String>['tbox.cn'];

/// 移动端 UA（部分站点按 UA 分流；这里对齐常见安卓浏览器 UA）。
const String kMobileUa =
    'Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 '
    '(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36';

/// 当前 App 版本（与 pubspec 同步；自更新比对用）。
const String kAppVersion = '1.0.0';

bool isInternalUrl(Uri uri) {
  final h = uri.host;
  if (h.isEmpty) return false;
  for (final d in kInternalDomains) {
    if (h == d || h.endsWith('.$d')) return true;
  }
  return false;
}

/// 逐段比较版本号：a > b 返回 true（自更新比对用）。
bool compareVersion(String a, String b) {
  final x = a.split('.').map(int.tryParse).toList();
  final y = b.split('.').map(int.tryParse).toList();
  for (var i = 0; i < 3; i++) {
    final ai = (i < x.length ? x[i] : 0) ?? 0;
    final bi = (i < y.length ? y[i] : 0) ?? 0;
    if (ai != bi) return ai > bi;
  }
  return false;
}

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final prefs = await SharedPreferences.getInstance();
  runApp(BaibaoxiangApp(prefs: prefs));
}

class BaibaoxiangApp extends StatefulWidget {
  const BaibaoxiangApp({super.key, required this.prefs});
  final SharedPreferences prefs;

  @override
  State<BaibaoxiangApp> createState() => _BaibaoxiangAppState();
}

class _BaibaoxiangAppState extends State<BaibaoxiangApp> {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '百宝箱',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorSchemeSeed: const Color(0xFF1A73E8),
        brightness: Brightness.light,
      ),
      darkTheme: ThemeData(
        colorSchemeSeed: const Color(0xFF1A73E8),
        brightness: Brightness.dark,
      ),
      // 深色模式跟随系统
      themeMode: ThemeMode.system,
      home: const ShellPage(),
    );
  }
}

class ShellPage extends StatefulWidget {
  const ShellPage({super.key});

  @override
  State<ShellPage> createState() => _ShellPageState();
}

class _ShellPageState extends State<ShellPage> {
  InAppWebViewController? _web;
  PullToRefreshController? _ptr;
  double _progress = 0;
  bool _loadError = false;
  String _errMsg = '';
  DateTime? _lastBackAt;

  @override
  void initState() {
    super.initState();
    _ptr = PullToRefreshController(
      settings: PullToRefreshSettings(color: const Color(0xFF1A73E8)),
      onRefresh: () async => _web?.reload(),
    );
  }

  Future<bool> _handleBack() async {
    final w = _web;
    if (w != null && await w.canGoBack()) {
      await w.goBack();
      return true;
    }
    // 到根页：再按一次退出
    final now = DateTime.now();
    if (_lastBackAt == null ||
        now.difference(_lastBackAt!) > const Duration(seconds: 2)) {
      _lastBackAt = now;
      _snack('再按一次退出');
      return true;
    }
    return false;
  }

  void _snack(String m) {
    final ctx = mounted ? context : null;
    if (ctx == null) return;
    ScaffoldMessenger.of(ctx)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(m), duration: const Duration(seconds: 2)));
  }

  Future<void> _openExternal(Uri uri) async {
    try {
      final ok = await launchUrl(uri, mode: LaunchMode.externalApplication);
      if (!ok) _snack('打不开该链接');
    } catch (_) {
      _snack('打不开该链接');
    }
  }

  Future<void> _openInBrowser() async {
    final w = _web;
    final u = await w?.getUrl();
    await _openExternal(u ?? Uri.parse(kHomeUrl));
  }

  Future<void> _clearCache() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('清除缓存'),
        content: const Text('会一并清除登录状态，需重新登录。继续？'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('取消')),
          FilledButton(onPressed: () => Navigator.pop(c, true), child: const Text('清除')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await InAppWebViewController.clearAllCache();
    } catch (_) {}
    try {
      await WebStorageManager.instance().deleteAllData();
    } catch (_) {}
    _snack('已清除，请下拉或重新进入');
  }

  Future<void> _checkUpdate() async {
    try {
      final req = await HttpClient().getUrl(Uri.parse(
          'https://api.github.com/repos/Howezzzzz/baibaoxiang-app/releases/latest'));
      final res = await req.close().timeout(const Duration(seconds: 12));
      if (res.statusCode != 200) {
        _snack('检查更新失败（HTTP ${res.statusCode}）');
        return;
      }
      final body = await res.transform(utf8.decoder).join();
      final tag = (jsonDecode(body) as Map)['tag_name']?.toString() ?? '';
      final latest = tag.replaceFirst('v', '');
      if (latest.isNotEmpty && compareVersion(latest, kAppVersion)) {
        if (!mounted) return;
        _showUpdateDialog(tag);
      } else {
        _snack('已是最新版本 v$kAppVersion');
      }
    } on TimeoutException {
      _snack('检查更新超时');
    } catch (_) {
      _snack('检查更新失败');
    }
  }

  void _showUpdateDialog(String tag) {
    showDialog<void>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('发现新版本'),
        content: Text('$tag（当前 v$kAppVersion）'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c), child: const Text('稍后')),
          FilledButton(
            onPressed: () {
              Navigator.pop(c);
              _openExternal(Uri.parse(
                  'https://github.com/Howezzzzz/baibaoxiang-app/releases/latest'));
            },
            child: const Text('去下载'),
          ),
        ],
      ),
    );
  }

  void _showAbout() {
    showAboutDialog(
      context: context,
      applicationName: '百宝箱',
      applicationVersion: 'v$kAppVersion',
      children: const [
        SizedBox(height: 8),
        Text('非官方客户端，仅供本人自用。内容与帐号均由 m.tbox.cn 提供，本应用不收集任何数据。'),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    // 字体缩放钳制，避免系统超大字号破版
    final mq = MediaQuery.of(context);
    final clamped = mq.textScaler.clamp(minScaleFactor: 0.8, maxScaleFactor: 1.3);
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) async {
        if (didPop) return;
        final keep = await _handleBack();
        if (!mounted) return;
        if (!keep) Navigator.maybeOf(this.context)?.maybePop();
      },
      child: MediaQuery(
        data: mq.copyWith(textScaler: clamped),
        child: Scaffold(
          appBar: AppBar(
            title: const Text('百宝箱'),
            actions: [
              PopupMenuButton<String>(
                onSelected: (v) {
                  switch (v) {
                    case 'reload':
                      _web?.reload();
                    case 'browser':
                      _openInBrowser();
                    case 'clear':
                      _clearCache();
                    case 'update':
                      _checkUpdate();
                    case 'about':
                      _showAbout();
                  }
                },
                itemBuilder: (_) => const [
                  PopupMenuItem(value: 'reload', child: Text('刷新')),
                  PopupMenuItem(value: 'browser', child: Text('在浏览器打开')),
                  PopupMenuItem(value: 'clear', child: Text('清除缓存')),
                  PopupMenuItem(value: 'update', child: Text('检查更新')),
                  PopupMenuItem(value: 'about', child: Text('关于')),
                ],
              ),
            ],
            bottom: _progress < 1.0 && _progress > 0
                ? PreferredSize(
                    preferredSize: const Size.fromHeight(2),
                    child: LinearProgressIndicator(value: _progress),
                  )
                : null,
          ),
          body: Stack(
            children: [
              InAppWebView(
                initialUrlRequest: URLRequest(url: WebUri(kHomeUrl)),
                initialSettings: InAppWebViewSettings(
                  userAgent: kMobileUa,
                  javaScriptEnabled: true,
                  domStorageEnabled: true,
                  databaseEnabled: true,
                  useShouldOverrideUrlLoading: true,
                  mediaPlaybackRequiresUserGesture: false,
                  supportZoom: true,
                  builtInZoomControls: false,
                  displayZoomControls: false,
                  verticalScrollBarEnabled: false,
                  cacheEnabled: true,
                  clearCache: false,
                  useHybridComposition: true,
                  allowsInlineMediaPlayback: true,
                  // 允许站内 https；明文一律拒绝（见 network_security_config）
                  mixedContentMode: MixedContentMode.MIXED_CONTENT_NEVER_ALLOW,
                  // false：让 target=_blank 也在同一个 WebView 里打开（站内不出壳）
                  supportMultipleWindows: false,
                  javaScriptCanOpenWindowsAutomatically: true,
                ),
                pullToRefreshController: _ptr,
                onWebViewCreated: (c) => _web = c,
                onLoadStart: (c, url) {
                  if (mounted) setState(() => _loadError = false);
                },
                onLoadStop: (c, url) async {
                  await _ptr?.endRefreshing();
                  if (mounted) setState(() => _progress = 1.0);
                },
                onProgressChanged: (c, p) {
                  if (mounted) setState(() => _progress = p / 100.0);
                },
                onReceivedError: (c, req, err) async {
                  await _ptr?.endRefreshing();
                  if (!req.isForMainFrame!) return;
                  if (mounted) {
                    setState(() {
                      _loadError = true;
                      _errMsg = err.description;
                    });
                  }
                },
                shouldOverrideUrlLoading: (c, nav) async {
                  final wu = nav.request.url;
                  if (wu == null) return NavigationActionPolicy.ALLOW;
                  final uri = Uri.parse(wu.toString());
                  if (uri.scheme == 'http' || uri.scheme == 'https') {
                    if (isInternalUrl(uri)) {
                      return NavigationActionPolicy.ALLOW;
                    }
                    await _openExternal(uri);
                    return NavigationActionPolicy.CANCEL;
                  }
                  // tel: / mailto: / alipay: / weixin: 等交给系统
                  await _openExternal(uri);
                  return NavigationActionPolicy.CANCEL;
                },
                onDownloadStartRequest: (c, req) async {
                  await _openExternal(Uri.parse(req.url.toString()));
                },
                onPermissionRequest: (c, req) async {
                  return PermissionResponse(
                    resources: req.resources,
                    action: PermissionResponseAction.GRANT,
                  );
                },
              ),
              if (_loadError)
                Positioned.fill(
                  child: ColoredBox(
                    color: Theme.of(context).colorScheme.surface,
                    child: Center(
                      child: Padding(
                        padding: const EdgeInsets.all(32),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const Icon(Icons.wifi_off, size: 56),
                            const SizedBox(height: 16),
                            const Text('加载失败，请检查网络',
                                style: TextStyle(fontSize: 16)),
                            const SizedBox(height: 8),
                            Text(_errMsg,
                                textAlign: TextAlign.center,
                                style: Theme.of(context).textTheme.bodySmall),
                            const SizedBox(height: 20),
                            FilledButton(
                              onPressed: () {
                                setState(() => _loadError = false);
                                _web?.loadUrl(urlRequest:
                                    URLRequest(url: WebUri(kHomeUrl)));
                              },
                              child: const Text('重试'),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
