package cn.tbox.baibaoxiang

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        // App 自身能力（ABI/缓存目录/应用内安装）
        AppBridge.register(flutterEngine.dartExecutor.binaryMessenger, this)
    }
}
