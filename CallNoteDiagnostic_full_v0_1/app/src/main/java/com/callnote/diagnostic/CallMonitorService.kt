package com.callnote.diagnostic

import android.app.Service
import android.content.Intent
import android.os.IBinder

class CallMonitorService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
