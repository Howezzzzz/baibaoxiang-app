plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "cn.tbox.baibaoxiang"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 固定发布签名（CI 从 secrets 落到临时文件后经 env 传入）。
    // 未提供时回退 debug 签名（本地构建用）。
    val releaseKeystorePath: String? = System.getenv("BBX_KEYSTORE")
    val hasReleaseKeystore = !releaseKeystorePath.isNullOrBlank() && file(releaseKeystorePath).exists()

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = System.getenv("BBX_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("BBX_KEY_ALIAS")
                keyPassword = System.getenv("BBX_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "cn.tbox.baibaoxiang"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        // Uses the version code from pubspec.yaml. When using split APKs, 1000 * ABI_VERSION
        // is added automatically by Flutter. (https://developer.android.com/studio/build/configure-apk-splits#configure-APK-versions)
        // You can force using the value of versionCode by specifying the `-P force-version-code-ignoring-abi=true`
        // flag during build.
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    buildTypes {
        release {
            // 固定发布签名 → 各版本可直接覆盖安装升级（历史上用 CI debug 签名，
            // 每次构建钥匙都不同，导致只能卸载重装）。无 keystore 时回退 debug（本地用）。
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            // AGP 9 起 release 默认开 R8 混淆；壳应用无混淆收益，
            // 且 GeckoView 内嵌 snakeyaml 引用桌面 JDK 的 java.beans 会缺类报错 → 关闭。
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}

dependencies {
    // 应用内安装更新包需要 FileProvider（androidx.core）
    implementation("androidx.core:core-ktx:1.15.0")
}

// GeckoView AAR 只发布在 maven.mozilla.org（:app 聚合解析插件依赖时需要；
// FlutterPlugin 给每个 project 注入的只有 flutter.io，AGP 默认只有 google/mavenCentral）
repositories {
    maven { url = uri("https://maven.mozilla.org/maven2/") }
}
