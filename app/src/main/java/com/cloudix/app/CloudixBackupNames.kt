package com.cloudix.app

import java.security.MessageDigest

/**
 * Preserve ordinary Unicode, punctuation and extensions in backed-up filenames.
 * Only characters rejected by the Cloudix server are escaped. A stable digest
 * prevents two different sanitized/truncated names from silently colliding.
 */
object CloudixBackupNames {
    fun safeName(original: String): String {
        val clean = original.map { char ->
            if (char == '/' || char == '\\' || char.code < 32) '_' else char
        }.joinToString("").trim().ifEmpty { "Sans_nom" }
            .let { if (it == "." || it == "..") "Sans_nom" else it }

        if (clean == original && clean.length <= 120) return clean

        val suffix = MessageDigest.getInstance("SHA-256")
            .digest(original.toByteArray(Charsets.UTF_8))
            .take(6)
            .joinToString("") { "%02x".format(it) }
        return clean.take(120 - suffix.length - 1).trimEnd() + "_" + suffix
    }
}
