import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart' show kDebugMode;
import 'package:flutter/services.dart';

import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:webview_flutter_geckoview/webview_flutter_geckoview.dart';

/// 百宝箱安卓壳 —— 把 https://www.tbox.cn/agents/home 装进独立 App。
/// 非官方、自用。站内跳转留在壳内，外站/外部 scheme 交系统。
///
/// v2.0.0：渲染内核自 webview_flutter（系统 WebView）换成
/// vendor/webview_flutter_geckoview（内置 Firefox GeckoView 内核），
/// 不再依赖系统 WebView 版本——老设备（系统 WebView 停在 Chromium ~80）
/// 解析不了页面 ES2021 语法的整屏白屏问题根治。
const String kHomeUrl = 'https://www.tbox.cn/agents/home';

/// 站内域（含子域）：其余一律视为外链 → 系统浏览器。
const List<String> kInternalDomains = <String>['tbox.cn'];

/// 移动端 UA（部分站点按 UA 分流；这里对齐常见安卓浏览器 UA）。
const String kMobileUa =
    'Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 '
    '(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36';

/// 当前 App 版本（与 pubspec 同步；自更新比对用）。
const String kAppVersion = '2.0.3';

/// GitHub 访问镜像前缀（前缀 + 原始完整 URL；'' = 直连）。
/// 更新检测与安装包下载都会并发测速，谁快用谁。
const List<String> kGithubMirrorPrefixes = <String>[
  '',
  'https://gh-proxy.com/',
  'https://ghfast.top/',
  'https://ghproxy.net/',
  'https://ghproxy.cc/',
];

/// 更新检测（GitHub API）候选源：实测仅直连与 gh-proxy 能代理 api.github.com。
const List<String> kGithubApiPrefixes = <String>['', 'https://gh-proxy.com/'];

/// 最新 release 的 API（不含镜像前缀）。
const String kLatestReleaseApi =
    'https://api.github.com/repos/Howezzzzz/baibaoxiang-app/releases/latest';

/// App 自身能力桥（ABI / 缓存目录 / 应用内安装），见 AppBridge.kt。
const MethodChannel kAppBridge = MethodChannel('dev.tbox.bbx/app_bridge');

/// GeckoView 扩展桥（清缓存等官方接口没有的能力）。
const MethodChannel kGeckoBridge = MethodChannel('dev.tbox.bbx/gecko_bridge');

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

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  // 显式指定 GeckoView 实现（webview_flutter_geckoview 非官方背书，
  // 注册顺序不确定时可能被 webview_flutter_android 默认实现覆盖）
  WebViewPlatform.instance = GeckoWebViewPlatform();
  // 任何构建错误直接显示成可见文字，不再是无解白屏
  ErrorWidget.builder = (details) => Material(
    color: const Color(0xFF1A73E8),
    child: Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Text(
          '页面出错了，请把这个提示告诉开发者：\n${details.exception}',
          style: const TextStyle(color: Colors.white, fontSize: 14),
        ),
      ),
    ),
  );
  runApp(const BaibaoxiangApp());
}

class BaibaoxiangApp extends StatefulWidget {
  const BaibaoxiangApp({super.key});

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
  WebViewController? _controller;
  bool _loading = false;
  bool _pageBlank = false;
  bool _webReady = false;
  bool _firstPageReady = false;

  /// 空白判定时的诊断快照（URL/标题/节点量/JS错误/内核探针），显示在空白提示页上。
  String _diag = '';

  /// 加载代际计数：每次新导航自增；空白检测回调用它丢弃过期结果（防竞态误报）。
  int _loadGen = 0;
  DateTime? _lastBackAt;
  Timer? _blankTimer;
  Timer? _readyTimer;

  /// 页面内 JS 运行时错误（window.onerror 注入收集），配合空白诊断展示。
  /// GeckoView 没有原生的 console 监听 API，用页面内注入替代（JS 语法错误
  /// 在 GeckoView 新内核下不会再出现，这里主要兜运行时错误）。
  final List<String> _jsErrors = <String>[];

  /// 本机首选 ABI（arm64-v8a / armeabi-v7a），用于选对应架构的更新包。
  String _deviceAbi = 'arm64-v8a';

  @override
  void initState() {
    super.initState();
    _initController();
    _initAbi();
    // WebView 延后到首帧之后再挂载：平台视图在首帧里挂载，个别设备上会卡住整帧。
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _readyTimer = Timer(const Duration(milliseconds: 300), () {
        if (mounted) setState(() => _webReady = true);
      });
    });
  }

  @override
  void dispose() {
    _blankTimer?.cancel();
    _readyTimer?.cancel();
    super.dispose();
  }

  void _initController() {
    final controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setUserAgent(kMobileUa)
      ..setNavigationDelegate(
        NavigationDelegate(
          onNavigationRequest: (request) async {
            final uri = Uri.parse(request.url);
            if (uri.scheme == 'http' || uri.scheme == 'https') {
              if (isInternalUrl(uri)) return NavigationDecision.navigate;
              await _openExternal(uri);
              return NavigationDecision.prevent;
            }
            // tel: / mailto: / alipay: / weixin: 等交给系统
            await _openExternal(uri);
            return NavigationDecision.prevent;
          },
          onPageStarted: (url) {
            _loadGen++;
            _jsErrors.clear();
            if (mounted) {
              setState(() {
                _loading = true;
                _pageBlank = false;
                _diag = '';
              });
            }
            _blankTimer?.cancel();
            // 尽早注入 window.onerror 收集 JS 运行时错误（供空白诊断用）
            _installErrorCollector();
          },
          onPageFinished: (url) async {
            _installErrorCollector(); // SPA 路由切换后再兜一次
            if (mounted) {
              setState(() {
                _loading = false;
                _firstPageReady = true;
              });
            }
            _scheduleBlankCheck();
          },
          // 注意：webview_flutter_geckoview（vendored）未实现 setOnWebResourceError
          // （调用即抛 UnimplementedError），因此这里不能再传该回调。
          // 网络错误由 GeckoView 内置错误页展示（同 Firefox「无法连接」页），
          // 页面空白则由下方空白检测兜底诊断。
        ),
      );
    controller.loadRequest(Uri.parse(kHomeUrl));
    _controller = controller;
  }

  /// 注入页面级错误收集器：页面 JS 运行时错误留底（诊断用）。
  Future<void> _installErrorCollector() async {
    final c = _controller;
    if (c == null) return;
    try {
      await c.runJavaScript('''
        (function() {
          if (window.__bbxErrInstalled) return;
          window.__bbxErrInstalled = true;
          window.__bbxErrors = window.__bbxErrors || [];
          window.addEventListener('error', function(e) {
            var m = (e && e.message || 'unknown error');
            var loc = '';
            if (e && e.filename) { loc = ' @ ' + e.filename + ':' + (e.lineno || 0); }
            window.__bbxErrors.push(m + loc);
            if (window.__bbxErrors.length > 20) window.__bbxErrors.shift();
          });
        })();
      ''');
    } catch (_) {}
  }

  /// 读取页面内收集到的 JS 错误（无则返回空串）。
  Future<String> _readJsErrors() async {
    final c = _controller;
    if (c == null) return '';
    try {
      final r = (await c.runJavaScriptReturningResult(
        'JSON.stringify(window.__bbxErrors || [])',
      )) as String;
      final arr = jsonDecode(_unwrapQuoted(r)) as List;
      return arr.cast<String>().join('\n');
    } catch (_) {
      return '';
    }
  }

  /// 页面加载完成后延迟检测「JS 跑了但页面空白」：
  /// 单页应用若在 WebView 里因 JS 错误/容器兼容挂了，body 会一直空着。
  /// 判定时同时抓一份诊断快照（URL/标题/节点量/JS错误/内核探针）。
  void _scheduleBlankCheck() {
    _blankTimer?.cancel();
    final gen = _loadGen; // 捕获本次导航的代际，回调结果过期则丢弃
    _blankTimer = Timer(const Duration(seconds: 8), () async {
      final c = _controller;
      if (c == null || !mounted) return;
      try {
        final r = (await c.runJavaScriptReturningResult('''
          (function() {
            var rm = document.getElementById('root-master');
            var rootChildren = rm ? rm.children.length : -1;
            var bodyText = (document.body && document.body.innerText || '').length;
            var rootText = (rm && rm.innerText || '').length;
            return JSON.stringify({rootChildren: rootChildren, bodyText: bodyText, rootText: rootText});
          })()
        '''));
        // runJavaScriptReturningResult 返回 JSON 编码字符串（可能再包一层引号）
        final raw = (r as String).trim();
        final decoded = raw.startsWith('{') ? raw : _unwrapQuoted(raw);
        final info = jsonDecode(decoded) as Map;
        final rootChildren = (info['rootChildren'] as num?)?.toInt() ?? -1;
        final bodyText = (info['bodyText'] as num?)?.toInt() ?? -1;
        final rootText = (info['rootText'] as num?)?.toInt() ?? -1;
        final blank = rootChildren == 0 && bodyText == 0;
        if (blank) {
          var url = '';
          var title = '';
          try {
            url = (await c.currentUrl()) ?? '';
            title = (await c.getTitle()) ?? '';
          } catch (_) {}
          if (!mounted) return;
          if (gen != _loadGen) return;
          final jsErrors = await _readJsErrors();
          if (!mounted) return;
          if (gen != _loadGen) return;
          final kernel = await _probeKernel();
          if (!mounted) return;
          if (gen != _loadGen) return;
          setState(() {
            _pageBlank = true;
            _diag =
                'URL: $url\n标题: $title\n'
                'Android: ${Platform.operatingSystemVersion}\n'
                'rootChildren: $rootChildren / bodyText: $bodyText / rootText: $rootText\n'
                '内核探针: $kernel\n'
                '页面JS错误:\n${jsErrors.isEmpty ? '（无）' : jsErrors}';
          });
        }
      } catch (_) {}
    });
  }

  /// WebView 内核能力探针：直接 eval 现代 JS 语法，测引擎支持度。
  /// v1.0.x 时代用于定位「系统 WebView 内核过旧」白屏；v2.0.0 换 GeckoView
  /// 内核后探针理论全 OK，保留作为内核佐证 + 未来诊断锚点。
  Future<String> _probeKernel() async {
    final c = _controller;
    if (c == null) return '（WebView 未就绪）';
    try {
      final r = (await c.runJavaScriptReturningResult(r'''
        (function() {
          var out = {};
          function t(k, s) {
            try { eval(s); out[k] = 'OK'; }
            catch (e) { out[k] = 'FAIL: ' + String(e && e.message || e); }
          }
          t('??=', 'var a; a ??= 1');
          t('||=', 'var a; a ||= 1');
          t('&&=', 'var a = 1; a &&= 2');
          t('??', 'var a = (null ?? 1)');
          t('?.', 'var a = {}; a.b?.c;');
          t('private#', 'class A { #x = 1; }');
          t('static{}', 'class A { static { } }');
          t('flat', '[].flat()');
          t('fromEntries', 'Object.fromEntries([])');
          t('replaceAll', "''.replaceAll('a','b')");
          t('allSettled', 'Promise.allSettled([])');
          t('globalThis', 'typeof globalThis');
          return JSON.stringify(out);
        })()
      '''));
      Map decoded;
      try {
        decoded = jsonDecode(r as String) as Map;
      } catch (_) {
        decoded = jsonDecode(_unwrapQuoted(r as String)) as Map;
      }
      return decoded.entries.map((e) => '${e.key}=${e.value}').join(' ');
    } catch (e) {
      return '（探针异常: $e）';
    }
  }

  static String _unwrapQuoted(String s) {
    var t = s.trim();
    while (t.length >= 2 &&
        ((t.startsWith('"') && t.endsWith('"')) ||
            (t.startsWith("'") && t.endsWith("'")))) {
      t = t.substring(1, t.length - 1);
    }
    return t;
  }

  Future<bool> _handleBack() async {
    final c = _controller;
    if (c != null && await c.canGoBack()) {
      await c.goBack();
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
      ..showSnackBar(
        SnackBar(content: Text(m), duration: const Duration(seconds: 2)),
      );
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
    final c = _controller;
    final u = await c?.currentUrl();
    await _openExternal(Uri.parse(u ?? kHomeUrl));
  }

  Future<void> _clearCache() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('清除缓存'),
        content: const Text('会一并清除登录状态，需重新登录。继续？'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(c, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(c, true),
            child: const Text('清除'),
          ),
        ],
      ),
    );
    if (ok != true) return;
    // GeckoView 扩展桥：HTTP 缓存 + Cookie + DOM 存储一次清干净
    // （ClearFlags.ALL）。插件的官方 WebViewCookieManager 未实现 clearCookies，
    // 不能走那条路。
    try {
      await kGeckoBridge.invokeMethod<void>('clearAllCache');
    } catch (e) {
      if (kDebugMode) debugPrint('[gecko-bridge] clearAllCache: $e');
    }
    _snack('已清除，请下拉或重新进入');
  }

  Future<void> _initAbi() async {
    try {
      final abi = await kAppBridge.invokeMethod<String>('getAbi');
      if (abi != null && abi.isNotEmpty) _deviceAbi = abi;
    } catch (_) {}
  }

  // ---------- 更新：多源并发检测 → 择优下载 → 应用内安装 ----------

  Future<void> _startUpdateFlow() async {
    if (!mounted) return;
    showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (_) => const AlertDialog(
        content: Row(
          children: [
            SizedBox(
              width: 22,
              height: 22,
              child: CircularProgressIndicator(strokeWidth: 2.5),
            ),
            SizedBox(width: 16),
            Text('正在检查更新…'),
          ],
        ),
      ),
    );
    final release = await _fetchLatestRelease();
    if (!mounted) return;
    Navigator.of(context, rootNavigator: true).pop();
    if (release == null) {
      _snack('检查更新失败：网络与镜像都不通');
      return;
    }
    final tag = release['tag_name']?.toString() ?? '';
    final latest = tag.replaceFirst('v', '');
    if (!compareVersion(latest, kAppVersion)) {
      _snack('已是最新版本 v$kAppVersion');
      return;
    }
    final url = _pickApkUrl(release);
    if (url == null) {
      _snack('新版本 $tag 里没有匹配本机（$_deviceAbi）的安装包');
      return;
    }
    _showUpdateDialog(tag, url);
  }

  /// 并发向各候选源取 release 信息，谁先成功用谁。
  Future<Map<String, dynamic>?> _fetchLatestRelease() async {
    final completer = Completer<Map<String, dynamic>?>();
    var pending = kGithubApiPrefixes.length;
    for (final prefix in kGithubApiPrefixes) {
      _fetchReleaseVia(prefix).then(
        (v) {
          if (v != null) {
            if (!completer.isCompleted) completer.complete(v);
          } else {
            pending--;
            if (pending == 0 && !completer.isCompleted) {
              completer.complete(null);
            }
          }
        },
        onError: (_) {
          pending--;
          if (pending == 0 && !completer.isCompleted) completer.complete(null);
        },
      );
    }
    return completer.future.timeout(
      const Duration(seconds: 15),
      onTimeout: () => null,
    );
  }

  Future<Map<String, dynamic>?> _fetchReleaseVia(String prefix) async {
    final client = HttpClient()..connectionTimeout = const Duration(seconds: 6);
    try {
      final req = await client.getUrl(Uri.parse('$prefix$kLatestReleaseApi'));
      req.headers.set(HttpHeaders.acceptHeader, 'application/vnd.github+json');
      final res = await req.close().timeout(const Duration(seconds: 10));
      if (res.statusCode != 200) return null;
      final body = await res.transform(utf8.decoder).join();
      final j = jsonDecode(body);
      if (j is Map && j['tag_name'] != null) {
        return j.cast<String, dynamic>();
      }
    } catch (_) {
      // 该源不可用，交给下一候选
    } finally {
      client.close(force: true);
    }
    return null;
  }

  /// 按本机 ABI 选对应安装包地址。
  String? _pickApkUrl(Map<String, dynamic> release) {
    final assets = (release['assets'] as List?) ?? const [];
    String? arm64;
    String? v7a;
    for (final a in assets) {
      if (a is! Map) continue;
      final name = (a['name'] ?? '').toString();
      final url = (a['browser_download_url'] ?? '').toString();
      if (!name.endsWith('.apk') || url.isEmpty) continue;
      if (name.contains('arm64')) {
        arm64 = url;
      } else if (name.contains('armeabi-v7a') || name.contains('armv7')) {
        v7a = url;
      }
    }
    if (_deviceAbi.contains('64')) return arm64 ?? v7a;
    return v7a ?? arm64;
  }

  void _showUpdateDialog(String tag, String apkUrl) {
    showDialog<void>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('发现新版本'),
        content: Text(
          '$tag（当前 v$kAppVersion）\n\n可在应用内直接下载安装，下载源会自动择优（直连 / 镜像）。',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(c),
            child: const Text('稍后'),
          ),
          TextButton(
            onPressed: () {
              Navigator.pop(c);
              _openExternal(Uri.parse('${kGithubMirrorPrefixes[1]}$apkUrl'));
            },
            child: const Text('浏览器下载'),
          ),
          FilledButton(
            onPressed: () {
              Navigator.pop(c);
              _downloadAndInstall(apkUrl);
            },
            child: const Text('立即更新'),
          ),
        ],
      ),
    );
  }

  /// 内置下载（并发测速择源 + 断点续传），完成后提示安装。
  Future<void> _downloadAndInstall(String apkUrl) async {
    final progress = ValueNotifier<double>(0);
    final status = ValueNotifier<String>('测速中…');
    var cancelled = false;
    final dialog = showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (_) => AlertDialog(
        title: const Text('下载更新'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            ValueListenableBuilder<double>(
              valueListenable: progress,
              builder: (_, v, _) =>
                  LinearProgressIndicator(value: v > 0 && v < 1 ? v : null),
            ),
            const SizedBox(height: 12),
            ValueListenableBuilder<String>(
              valueListenable: status,
              builder: (_, s, _) =>
                  Text(s, style: const TextStyle(fontSize: 12)),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => cancelled = true,
            child: const Text('取消'),
          ),
        ],
      ),
    );
    final file = await _downloadApk(
      apkUrl,
      progress: progress,
      status: status,
      isCancelled: () => cancelled,
    );
    if (mounted) Navigator.of(context, rootNavigator: true).pop();
    await dialog;
    progress.dispose();
    status.dispose();
    if (!mounted) return;
    if (file == null) {
      if (!cancelled) _showDownloadFailedDialog(apkUrl);
      return;
    }
    _showInstallPrompt(file.path);
  }

  /// 下载 APK：先并发测速各源，按快慢依次尝试；失败换源并断点续传。
  Future<File?> _downloadApk(
    String apkUrl, {
    required ValueNotifier<double> progress,
    required ValueNotifier<String> status,
    required bool Function() isCancelled,
  }) async {
    try {
      final cacheDir = await kAppBridge.invokeMethod<String>('getCacheDir');
      if (cacheDir == null || cacheDir.isEmpty) return null;
      final dir = Directory('$cacheDir/update');
      if (!dir.existsSync()) dir.createSync(recursive: true);
      final name = Uri.parse(apkUrl).pathSegments.last;
      final file = File('${dir.path}/$name');
      // 清掉旧版本残留，避免缓存目录堆积
      for (final e in dir.listSync()) {
        if (e is File && !e.path.endsWith('/$name')) {
          try {
            e.deleteSync();
          } catch (_) {}
        }
      }

      status.value = '测速中…';
      final sources = await _rankSources(apkUrl);
      if (isCancelled()) return null;
      if (sources.isEmpty) return null;
      final total = sources.first.total;
      // 已经是完整包（同版本重复检查）直接复用
      if (total > 0 && file.existsSync() && file.lengthSync() == total) {
        progress.value = 1;
        return file;
      }

      for (final s in sources) {
        if (isCancelled()) return null;
        final label = s.prefix.isEmpty ? '直连' : Uri.parse(s.prefix).host;
        status.value = '下载中（$label）';
        try {
          await _downloadOne(
            '${s.prefix}$apkUrl',
            file,
            total: total,
            progress: progress,
            isCancelled: isCancelled,
          );
          if (_apkLooksValid(file)) return file;
        } on _Cancelled {
          return null;
        } catch (_) {
          // 该源失败：换下一个（保留已下字节，续传）
        }
      }
      return null;
    } catch (_) {
      return null;
    }
  }

  /// 并发探测各源首个 256KB 的吞吐，按快慢排序（慢/不可用的被过滤）。
  Future<List<_Source>> _rankSources(String apkUrl) async {
    final probes = kGithubMirrorPrefixes.map((prefix) async {
      final sw = Stopwatch()..start();
      final client = HttpClient()
        ..connectionTimeout = const Duration(seconds: 8);
      try {
        final req = await client.getUrl(Uri.parse('$prefix$apkUrl'));
        req.headers.set(HttpHeaders.rangeHeader, 'bytes=0-262143');
        final res = await req.close().timeout(const Duration(seconds: 12));
        if (res.statusCode != 200 && res.statusCode != 206) return null;
        var total = res.contentLength;
        if (total < 0) total = 0; // -1 = 未提供 Content-Length
        final cr = res.headers.value('content-range');
        if (cr != null && cr.contains('/')) {
          total = int.tryParse(cr.split('/').last) ?? total;
        }
        var got = 0;
        await for (final chunk in res.timeout(const Duration(seconds: 12))) {
          got += chunk.length;
        }
        sw.stop();
        if (got < 4096) return null;
        return _Source(prefix, got / (sw.elapsedMicroseconds / 1e6), total);
      } catch (_) {
        return null;
      } finally {
        client.close(force: true);
      }
    }).toList();
    final ok = (await Future.wait(probes)).whereType<_Source>().toList();
    ok.sort((a, b) => b.bps.compareTo(a.bps));
    return ok;
  }

  Future<void> _downloadOne(
    String url,
    File file, {
    required int total,
    required ValueNotifier<double> progress,
    required bool Function() isCancelled,
  }) async {
    var existing = file.existsSync() ? file.lengthSync() : 0;
    final client = HttpClient()
      ..connectionTimeout = const Duration(seconds: 10);
    try {
      final req = await client.getUrl(Uri.parse(url));
      if (existing > 0) {
        req.headers.set(HttpHeaders.rangeHeader, 'bytes=$existing-');
      }
      final res = await req.close().timeout(const Duration(seconds: 20));
      var size = total;
      if (existing > 0 && res.statusCode == 206) {
        final cr = res.headers.value('content-range');
        if (cr != null && cr.contains('/')) {
          size = int.tryParse(cr.split('/').last) ?? size;
        }
      } else if (res.statusCode == 200) {
        existing = 0; // 服务端不支持续传 → 从头下
        if (res.contentLength > 0) size = res.contentLength;
      } else {
        throw HttpException('HTTP ${res.statusCode}');
      }
      final sink = file.openWrite(
        mode: existing > 0 ? FileMode.append : FileMode.write,
      );
      var done = existing;
      try {
        await for (final chunk in res.timeout(const Duration(seconds: 30))) {
          if (isCancelled()) throw const _Cancelled();
          sink.add(chunk);
          done += chunk.length;
          if (size > 0) progress.value = (done / size).clamp(0, 1).toDouble();
        }
        await sink.flush();
      } finally {
        await sink.close();
      }
      if (size > 0 && done < size) {
        throw const HttpException('下载不完整');
      }
    } finally {
      client.close(force: true);
    }
  }

  bool _apkLooksValid(File f) {
    try {
      if (f.lengthSync() < 1024 * 1024) return false;
      final raf = f.openSync();
      final head = raf.readSync(2);
      raf.closeSync();
      return head.length == 2 && head[0] == 0x50 && head[1] == 0x4B;
    } catch (_) {
      return false;
    }
  }

  void _showDownloadFailedDialog(String apkUrl) {
    showDialog<void>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('下载失败'),
        content: const Text('所有下载源都没成功。可以重试，或改用浏览器下载（走镜像）。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(c),
            child: const Text('稍后'),
          ),
          TextButton(
            onPressed: () {
              Navigator.pop(c);
              _openExternal(Uri.parse('${kGithubMirrorPrefixes[1]}$apkUrl'));
            },
            child: const Text('浏览器下载'),
          ),
          FilledButton(
            onPressed: () {
              Navigator.pop(c);
              _downloadAndInstall(apkUrl);
            },
            child: const Text('重试'),
          ),
        ],
      ),
    );
  }

  void _showInstallPrompt(String path) {
    showDialog<void>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('下载完成'),
        content: const Text('点「立即安装」会弹出系统安装界面。首次可能需允许「安装未知应用」，允许后回来再点一次即可。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(c),
            child: const Text('稍后'),
          ),
          FilledButton(
            onPressed: () => _installApk(c, path),
            child: const Text('立即安装'),
          ),
        ],
      ),
    );
  }

  Future<void> _installApk(BuildContext dialogCtx, String path) async {
    try {
      final can =
          await kAppBridge.invokeMethod<bool>('canInstallPackages') ?? true;
      if (!can) {
        await kAppBridge.invokeMethod('openInstallSettings');
        _snack('请在系统里允许「百宝箱」安装应用，回来后点「立即安装」');
        return; // 保留对话框，便于允许后重试
      }
      await kAppBridge.invokeMethod('installApk', {'path': path});
      if (dialogCtx.mounted) Navigator.pop(dialogCtx);
    } catch (e) {
      _snack('调起安装失败：$e');
    }
  }

  void _showAbout() {
    showAboutDialog(
      context: context,
      applicationName: '百宝箱',
      applicationVersion: 'v$kAppVersion',
      children: const [
        SizedBox(height: 8),
        Text('非官方客户端，仅供本人自用。内容与帐号均由 www.tbox.cn 提供，本应用不收集任何数据。'),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    // 字体缩放钳制，避免系统超大字号破版
    final mq = MediaQuery.of(context);
    final clamped = mq.textScaler.clamp(
      minScaleFactor: 0.8,
      maxScaleFactor: 1.3,
    );
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
                      _controller?.reload();
                    case 'browser':
                      _openInBrowser();
                    case 'clear':
                      _clearCache();
                    case 'update':
                      _startUpdateFlow();
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
            bottom: _loading
                ? const PreferredSize(
                    preferredSize: Size.fromHeight(2),
                    child: LinearProgressIndicator(),
                  )
                : null,
          ),
          body: Stack(
            children: [
              if (!_webReady)
                const _LaunchingView()
              else if (_controller != null)
                WebViewWidget(controller: _controller!),
              // 首次页面就绪前盖一层启动视图（GeckoView 首次初始化比系统 WebView 慢）
              if (_webReady && !_firstPageReady && !_pageBlank)
                const Positioned.fill(child: _LaunchingView()),
              if (_pageBlank)
                Positioned.fill(
                  child: ColoredBox(
                    color: Theme.of(context).colorScheme.surface,
                    child: Center(
                      child: Padding(
                        padding: const EdgeInsets.all(32),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const Icon(
                              Icons.browser_not_supported_outlined,
                              size: 56,
                            ),
                            const SizedBox(height: 16),
                            const Text(
                              '页面空白（可能是网页在 WebView 里没跑起来）',
                              style: TextStyle(fontSize: 16),
                            ),
                            const SizedBox(height: 8),
                            const Text(
                              '可重试，或直接用手机浏览器打开',
                              style: TextStyle(fontSize: 13),
                            ),
                            if (_diag.isNotEmpty) ...[
                              const SizedBox(height: 12),
                              ConstrainedBox(
                                constraints: const BoxConstraints(
                                  maxHeight: 150,
                                ),
                                child: SingleChildScrollView(
                                  child: SelectableText(
                                    _diag,
                                    style: TextStyle(
                                      fontSize: 11,
                                      fontFamily: 'monospace',
                                      color: Theme.of(context)
                                          .colorScheme
                                          .onSurfaceVariant,
                                    ),
                                  ),
                                ),
                              ),
                            ],
                            const SizedBox(height: 20),
                            Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                FilledButton(
                                  onPressed: () {
                                    setState(() => _pageBlank = false);
                                    _controller?.loadRequest(
                                      Uri.parse(kHomeUrl),
                                    );
                                  },
                                  child: const Text('重试'),
                                ),
                                const SizedBox(width: 12),
                                OutlinedButton(
                                  onPressed: _openInBrowser,
                                  child: const Text('用浏览器打开'),
                                ),
                              ],
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

/// 启动占位：首帧先渲染它，WebView 延后挂载。
/// 颜色刻意不用纯白——如果用户看到这个浅色页，说明 Flutter UI 正常、问题在 WebView。
class _LaunchingView extends StatelessWidget {
  const _LaunchingView();

  @override
  Widget build(BuildContext context) {
    return const ColoredBox(
      color: Color(0xFFF2F5FA),
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.widgets_outlined, size: 56, color: Color(0xFF1A73E8)),
            SizedBox(height: 16),
            Text('正在启动百宝箱…', style: TextStyle(fontSize: 15)),
          ],
        ),
      ),
    );
  }
}

/// 下载源测速结果。
class _Source {
  const _Source(this.prefix, this.bps, this.total);

  /// 镜像前缀（'' = 直连）。
  final String prefix;

  /// 实测吞吐（字节/秒）。
  final double bps;

  /// 文件总大小（字节，0 = 未知）。
  final int total;
}

/// 用户主动取消下载。
class _Cancelled implements Exception {
  const _Cancelled();
}
