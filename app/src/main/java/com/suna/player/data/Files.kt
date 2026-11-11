package com.suna.player.data

import java.io.File

/** Writes to a temporary file first, so a crash mid-write never leaves a half-written file. */
fun File.writeAtomically(text: String) {
    val tmp = File(parentFile, "$name.tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(this)) {
        writeText(text)
        tmp.delete()
    }
}
