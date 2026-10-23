package dev.zeekr.webview_flutter_geckoview

import android.content.res.AssetManager
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterAssets
import java.io.IOException

/** Provides access to the assets registered as part of the App bundle.  */
abstract class FlutterAssetManager(val assetManager: AssetManager) {
    /**
     * Gets the relative file path to the Flutter asset with the given name, including the file's
     * extension, e.g., "myImage.jpg".
     *
     *
     * The returned file path is relative to the Android app's standard asset's directory.
     * Therefore, the returned path is appropriate to pass to Android's AssetManager, but the path is
     * not appropriate to load as an absolute path.
     */
    abstract fun getAssetFilePathByName(name: String): String

    /**
     * Returns a String array of all the assets at the given path.
     *
     * @param path A relative path within the assets, i.e., "docs/home.html". This value cannot be
     * null.
     * @return String[] Array of strings, one for each asset. These file names are relative to 'path'.
     * This value may be null.
     * @throws IOException Throws an IOException in case I/O operations were interrupted.
     */
    @Throws(IOException::class)
    fun list(path: String): Array<String>? {
        return assetManager.list(path)
    }

    /**
     * Provides access to assets using the [FlutterPlugin.FlutterAssets] for looking up file
     * paths to Flutter assets.
     */
    internal class PluginBindingFlutterAssetManager(
        assetManager: AssetManager, val flutterAssets: FlutterAssets
    ) : FlutterAssetManager(assetManager) {
        override fun getAssetFilePathByName(name: String): String {
            return flutterAssets.getAssetFilePathByName(name)
        }
    }
}
