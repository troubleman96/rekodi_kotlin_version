package com.camelcreatives.rekodi.service

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import com.camelcreatives.rekodi.recorder.service.RecordingForegroundService

class MediaProjectionTrampolineActivity : Activity() {

    companion object {
        const val REQUEST_MEDIA_PROJECTION = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == RESULT_OK && data != null) {
            val serviceIntent = Intent(this, RecordingForegroundService::class.java).apply {
                action = RecordingForegroundService.ACTION_START
                putExtra(RecordingForegroundService.EXTRA_RESULT_CODE, resultCode)
                putExtra(RecordingForegroundService.EXTRA_RESULT_DATA, data)
            }
            startForegroundService(serviceIntent)
        }
        finish()
    }

    override fun onActivityReenter(resultCode: Int, data: Intent?) {
        super.onActivityReenter(resultCode, data)
        finish()
    }
}
