package com.callnote.diagnostic

import android.telecom.Call
import android.telecom.InCallService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallNoteInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        appendCallEvent("Звонок открыт в CallNote AI")
    }

    override fun onCallRemoved(call: Call) {
        appendCallEvent("Звонок закрыт в CallNote AI")
        super.onCallRemoved(call)
    }

    private fun appendCallEvent(event: String) {
        runCatching {
            File(filesDir, "callnote_call_events.txt")
                .appendText("${timestamp()} - $event\n")
        }
    }

    private fun timestamp(): String {
        return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
    }
}
