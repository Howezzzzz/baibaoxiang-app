import 'package:baibaoxiang/main.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('_versionGreater 语义（自更新比对）', () {
    test('逐段比较', () {
      expect(compareVersion('1.0.1', '1.0.0'), isTrue);
      expect(compareVersion('1.1.0', '1.0.9'), isTrue);
      expect(compareVersion('2.0.0', '1.9.9'), isTrue);
      expect(compareVersion('1.0.0', '1.0.0'), isFalse);
      expect(compareVersion('0.9.9', '1.0.0'), isFalse);
      expect(compareVersion('1.0', '1.0.0'), isFalse);
    });
  });

  group('navActionFor 导航策略（回归：任何网页导航都不得自动弹出系统浏览器）', () {
    test('子框架一律壳内加载（哪怕跨域）', () {
      expect(
        navActionFor('https://cdn.example.com/hub.html', isMainFrame: false),
        NavAction.navigate,
      );
      expect(
        navActionFor('https://other-site.com/x', isMainFrame: false),
        NavAction.navigate,
      );
    });

    test('主框架 http(s)：站内站外都留壳内（不再自动外跳）', () {
      expect(
        navActionFor('https://www.tbox.cn/agents/home', isMainFrame: true),
        NavAction.navigate,
      );
      expect(
        navActionFor('https://m.tbox.cn/x', isMainFrame: true),
        NavAction.navigate,
      );
      expect(
        navActionFor('https://www.baidu.com/', isMainFrame: true),
        NavAction.navigate,
      );
    });

    test('主框架：用户可见 scheme 交系统，其余拦下', () {
      expect(navActionFor('tel:10086', isMainFrame: true), NavAction.external);
      expect(
        navActionFor('mailto:a@b.com', isMainFrame: true),
        NavAction.external,
      );
      expect(navActionFor('weixin://x', isMainFrame: true), NavAction.external);
      expect(
        navActionFor('moz-extension://abc/page.html', isMainFrame: true),
        NavAction.block,
      );
      expect(navActionFor('about:blank', isMainFrame: true), NavAction.block);
      expect(navActionFor('intent://x', isMainFrame: true), NavAction.block);
    });
  });
}
