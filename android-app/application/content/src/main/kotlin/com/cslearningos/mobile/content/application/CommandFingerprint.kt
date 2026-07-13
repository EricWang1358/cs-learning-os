package com.cslearningos.mobile.content.application

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

object CommandFingerprint {
    fun of(command: SaveNodeCommand): String {
        val bytes = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeLengthPrefixed(command.mode.name)
                output.writeLengthPrefixed(command.nodeId.value)
                output.writeNullableLong(command.expectedRevision?.value)
                output.writeNullableString(command.areaId)
                output.writeLengthPrefixed(command.title)
                output.writeLengthPrefixed(command.markdownBody)
            }
            buffer.toByteArray()
        }
        return MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()
    }

    private fun DataOutputStream.writeNullableLong(value: Long?) {
        writeBoolean(value != null)
        if (value != null) writeLong(value)
    }

    private fun DataOutputStream.writeNullableString(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeLengthPrefixed(value)
    }

    private fun DataOutputStream.writeLengthPrefixed(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun ByteArray.toHexString(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
}
