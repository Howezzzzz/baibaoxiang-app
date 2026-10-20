import 'package:baibaoxiang/main.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('isInternalUrl（站内/外链分流；站内不出壳，外链交系统）', () {
    test('tbox.cn 及其子域 → 站内', () {
      expect(isInternalUrl(Uri.parse('https://m.tbox.cn/agents/home')), isTrue);
      expect(isInternalUrl(Uri.parse('https://tbox.cn/')), isTrue);
      expect(isInternalUrl(Uri.parse('https://passport.tbox.cn/x')), isTrue);
    });

    test('外站 / 空 host → 外链', () {
      expect(isInternalUrl(Uri.parse('https://www.baidu.com')), isFalse);
      expect(isInternalUrl(Uri.parse('https://github.com/')), isFalse);
      expect(isInternalUrl(Uri.parse('about:blank')), isFalse);
    });

    test('防后缀拼接误判（eviltbox.cn / tbox.cn.evil.com 都不算站内）', () {
      expect(isInternalUrl(Uri.parse('https://eviltbox.cn')), isFalse);
      expect(isInternalUrl(Uri.parse('https://tbox.cn.evil.com')), isFalse);
    });
  });

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
}
