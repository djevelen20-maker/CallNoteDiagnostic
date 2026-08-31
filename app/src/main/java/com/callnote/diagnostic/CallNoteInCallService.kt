package com.callnote.diagnostic

import android.telecom.Call
import android.telecom.InCallService

class CallNoteInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        runCatching {
            callEventsFile(this).appendText("${timestampForNote()} - Звонок открыт в CallNote AI\n")
        }
    }

    override fun onCallRemoved(call: Call) {
        runCatching {
            callEventsFile(this).appendText("${timestampForNote()} - Звонок закрыт в CallNote AI\n")
        }
        super.onCallRemoved(call)
    }
}
