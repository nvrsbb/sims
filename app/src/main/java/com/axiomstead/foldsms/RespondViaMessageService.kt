package com.axiomstead.foldsms

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Declared because the default SMS role requires it. It never sends: stops at once, so no text leaves without review. */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { stopSelf(startId); return START_NOT_STICKY }
}
