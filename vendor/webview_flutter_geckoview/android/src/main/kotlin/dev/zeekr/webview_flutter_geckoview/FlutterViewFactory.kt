package dev.zeekr.webview_flutter_geckoview

import android.content.Context
import android.view.View
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

class FlutterViewFactory(val instanceManager: GeckoLibraryPigeonInstanceManager) :
    PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val identifier = if (args is Int) args.toLong()
        else throw IllegalStateException("An identifier is required to retrieve a View instance.")
        return when (val instance = instanceManager.getInstance<Any>(identifier)) {
            is PlatformView -> instance
            is View -> object : PlatformView {
                override fun getView(): View = instance
                override fun dispose() {}
            }

            else -> throw IllegalStateException("Unable to find a PlatformView or View instance: $args, $instance")
        }
    }
}