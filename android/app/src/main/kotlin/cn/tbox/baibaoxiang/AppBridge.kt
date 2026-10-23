package cn.tbox.baibaoxiang

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import java.io.File

/**
 * App 自身能力桥：机型 ABI、缓存目录、应用内安装 APK、安装权限/设置入口。
 *
 * channel: dev.tbox.bbx/app_bridge
 */
object AppBridge {
    private const val TAG = "AppBridge"
    private const val CHANNEL = "dev.tbox.bbx/app_bridge"

    private var activity: MainActivity? = null

    fun register(messenger: BinaryMessenger, act: MainActivity) {
        activity = act
        MethodChannel(messenger, CHANNEL).setMethodCallHandler { call, result ->
            val act = activity
            val ctx = act?.applicationContext
            if (act == null || ctx == null) {
                result.error("NO_ACTIVITY", "Activity 不可用", null)
                return@setMethodCallHandler
            }
            try {
                when (call.method) {
                    // 首选 ABI（用于选 arm64 / armeabi-v7a 包）
                    "getAbi" -> {
                        var best = ""
                        for (a in Build.SUPPORTED_ABIS) {
                            if (a == "arm64-v8a") { best = a; break }
                            if (a == "armeabi-v7a" && best.isEmpty()) best = a
                        }
                        result.success(best)
                    }

                    // App 缓存目录（下载安装包用）
                    "getCacheDir" -> result.success(ctx.cacheDir?.absolutePath ?: "")

                    // 是否已允许「安装未知应用」
                    "canInstallPackages" -> result.success(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            ctx.packageManager.canRequestPackageInstalls()
                        } else true
                    )

                    // 跳到本应用的「安装未知应用」授权页
                    "openInstallSettings" -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            act.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:${ctx.packageName}")
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                            result.success(true)
                        } else {
                            result.success(false)
                        }
                    }

                    // 拉起系统安装界面安装指定 APK
                    "installApk" -> {
                        val path = call.argument<String>("path")
                        if (path.isNullOrEmpty() || !File(path).exists()) {
                            result.error("BAD_PATH", "安装包不存在: $path", null)
                            return@setMethodCallHandler
                        }
                        val uri = FileProvider.getUriForFile(
                            ctx, "${ctx.packageName}.fileprovider", File(path)
                        )
                        act.startActivity(
                            Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/vnd.android.package-archive")
                                addFlags(
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        Intent.FLAG_ACTIVITY_NEW_TASK
                                )
                            }
                        )
                        result.success(true)
                    }

                    else -> result.notImplemented()
                }
            } catch (e: Exception) {
                Log.e(TAG, "handle ${call.method} failed", e)
                result.error("NATIVE_ERROR", e.message ?: e.toString(), null)
            }
        }
    }
}
