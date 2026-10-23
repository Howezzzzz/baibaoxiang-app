package dev.zeekr.webview_flutter_geckoview

import java.io.IOException

class FlutterAssetManagerProxyApi(override val pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiFlutterAssetManager(pigeonRegistrar) {
    override fun instance(): FlutterAssetManager {
        return pigeonRegistrar.flutterAssetManager
    }

    override fun list(pigeon_instance: FlutterAssetManager, path: String): List<String> {
        try {
            val paths = pigeon_instance.list(path) ?: return listOf()
            return paths.toList()
        } catch (ex: IOException) {
            throw RuntimeException(ex.message)
        }
    }

    override fun getAssetFilePathByName(
        pigeon_instance: FlutterAssetManager, name: String
    ): String {
        return pigeon_instance.getAssetFilePathByName(name)
    }
}