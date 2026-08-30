package com.callnote.diagnostic

import java.io.File

class CallRepository {

    fun getCalls(directory: File): List<CallCard> {
        return directory.listFiles()
            ?.filter { it.extension == "m4a" }
            ?.sortedByDescending { it.lastModified() }
            ?.map {
                CallCard(
                    audio = it,
                    date = java.util.Date(it.lastModified()).toString()
                )
            }
            ?: emptyList()
    }
}
