package com.arnit.imagex

import android.app.Application
import com.arnit.imagex.cleanup.TempCleanupWorker

/**
 * ARNIT ImageX - universal, private, on-device image conversion.
 * No INTERNET permission exists in this app: images never leave the device.
 */
class ImageXApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        com.arnit.imagex.di.ServiceLocator.tempFileManager(this).cleanupAll()
        TempCleanupWorker.schedule(this)
    }
}
