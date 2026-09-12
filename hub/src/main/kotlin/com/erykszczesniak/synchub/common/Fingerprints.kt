package com.erykszczesniak.synchub.common

import java.security.MessageDigest

object Fingerprints {
    /** Hex SHA-256 of [content]; short enough for a column, stable across JVMs. */
    fun sha256(content: String): String =
        MessageDigest
            .getInstance(
                "SHA-256",
            ).digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
