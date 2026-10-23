package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.StorageController

class StorageControllerProxyApi(pigeonRegistrar: ProxyApiRegistrar) : PigeonApiStorageController(pigeonRegistrar) {
    override fun clearData(
        pigeon_instance: StorageController,
        flags: Long,
        callback: (Result<Unit>) -> Unit
    ) {
        StorageController.ClearFlags.ALL
        pigeon_instance.clearData(flags).accept(
            { ResultCompat.success(Unit, callback) },
            { ResultCompat.failure(it, callback) }
        )
    }

    override fun clearDataForSessionContext(
        pigeon_instance: StorageController,
        contextId: String
    ) {
        pigeon_instance.clearDataForSessionContext(contextId)
    }

    override fun clearDataFromBaseDomain(
        pigeon_instance: StorageController,
        baseDomain: String,
        flags: Long,
        callback: (Result<Unit>) -> Unit
    ) {
        pigeon_instance.clearDataFromBaseDomain(baseDomain, flags).accept(
            { ResultCompat.success(Unit, callback) },
            { ResultCompat.failure(it, callback) }
        )
    }

    override fun clearDataFromHost(
        pigeon_instance: StorageController,
        host: String,
        flags: Long,
        callback: (Result<Unit>) -> Unit
    ) {
        pigeon_instance.clearDataFromHost(host, flags).accept(
            { ResultCompat.success(Unit, callback) },
            { ResultCompat.failure(it, callback) }
        )
    }

    override fun getCookieBannerModeForDomain(
        pigeon_instance: StorageController,
        uri: String,
        isPrivateBrowsing: Boolean,
        callback: (Result<Long?>) -> Unit
    ) {
        pigeon_instance.getCookieBannerModeForDomain(uri, isPrivateBrowsing).accept(
            { ResultCompat.success(it?.toLong(), callback) },
            { ResultCompat.failure(it, callback) }
        )
    }

    override fun removeCookieBannerModeForDomain(
        pigeon_instance: StorageController,
        uri: String,
        isPrivateBrowsing: Boolean,
        callback: (Result<Unit>) -> Unit
    ) {
        pigeon_instance.removeCookieBannerModeForDomain(uri, isPrivateBrowsing).accept(
            { ResultCompat.success(Unit, callback) },
            { ResultCompat.failure(it, callback) }
        )
    }

    override fun setCookieBannerModeAndPersistInPrivateBrowsingForDomain(
        pigeon_instance: StorageController,
        uri: String,
        mode: Long,
        callback: (Result<Unit>) -> Unit
    ) {
        pigeon_instance.setCookieBannerModeAndPersistInPrivateBrowsingForDomain(uri, mode.toInt()).accept(
            { ResultCompat.success(Unit, callback) },
            { ResultCompat.failure(it, callback) }
        )
    }

    override fun setCookieBannerModeForDomain(
        pigeon_instance: StorageController,
        uri: String,
        mode: Long,
        isPrivateBrowsing: Boolean,
        callback: (Result<Unit>) -> Unit
    ) {
        pigeon_instance.setCookieBannerModeForDomain(uri, mode.toInt(), isPrivateBrowsing).accept(
            { ResultCompat.success(Unit, callback) },
            { ResultCompat.failure(it, callback) }
        )
    }
}