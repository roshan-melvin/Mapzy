package com.swapmap.zwap.demo

import android.app.Application
import com.mappls.sdk.maps.Mappls
import com.mappls.sdk.services.account.MapplsAccountManager
import com.swapmap.zwap.demo.config.AppConfig

class ZwapApplication : Application() {
    companion object {
        lateinit var instance: ZwapApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        AppConfig.load(this)
        
        val mapplsRestKey = AppConfig.get("MAPPLS_REST_API_KEY", "")
        val mapplsSdkKey = AppConfig.get("MAPPLS_MAP_SDK_KEY", "")
        val atlasClientId = AppConfig.get("MAPPLS_ATLAS_CLIENT_ID", "")
        val atlasClientSecret = AppConfig.get("MAPPLS_ATLAS_CLIENT_SECRET", "")

        MapplsAccountManager.getInstance().restAPIKey = mapplsRestKey
        MapplsAccountManager.getInstance().mapSDKKey = mapplsSdkKey
        MapplsAccountManager.getInstance().atlasClientId = atlasClientId
        MapplsAccountManager.getInstance().atlasClientSecret = atlasClientSecret
        
        Mappls.getInstance(this)
        
        // Initialize Cloudinary
        com.swapmap.zwap.demo.network.CloudinaryManager.init(this)
    }
}
