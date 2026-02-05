package com.swapmap.zwap.demo

import android.app.Application
import com.mappls.sdk.maps.Mappls
import com.mappls.sdk.services.account.MapplsAccountManager

class ZwapApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val key = "4f5f56254fc624e2a817b1b2d2de4020"
        MapplsAccountManager.getInstance().restAPIKey = key
        MapplsAccountManager.getInstance().mapSDKKey = key
        MapplsAccountManager.getInstance().atlasClientId = "96dHZVzsAuugadw-3_1eeb4IapToWCSpJypKyJKDGuItzGMH3MU7FBnX-hPym7qFvg7zJ3QN-j4xkE--p_4JoQ=="
        MapplsAccountManager.getInstance().atlasClientSecret = "lrFxI-iSEg-JlQ2tDZ-tuTMRtniiUG6rX0JZsAnxSrnuOU1U0v3f8OJqeZ9b_14NjptQRzVmBSOiaNUfrdtCAM2gvc_E3-o5"
        
        Mappls.getInstance(this)
        
        // Initialize Cloudinary
        com.swapmap.zwap.demo.network.CloudinaryManager.init(this)
    }
}
