package com.camtonas.app.camera

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PTP-over-IP container (RFC 15740 / ISO 15740). All fields are big-endian.
 *
 * Header layout per [PTP-over-IP spec]:
 *   length  (4)  -- size of the entire container minus the leading length field
 *   type    (4)  -- 1=Command, 2=Data, 3=Response, 4=Event
 *   payload (...) -- type-specific header + body
 */
data class PTPContainer(
    val type: Int,
    val operationCode: Int,   // for Cmd/Data/Response
    val eventCode: Int,       // for Event
    val responseCode: Int,    // for Response
    val transactionId: Int,
    val parameters: ByteArray
) {
    companion object {
        const val TYPE_COMMAND  = 1
        const val TYPE_DATA     = 2
        const val TYPE_RESPONSE = 3
        const val TYPE_EVENT    = 4

        const val RESP_OK = 0x2001

        fun command(op: Int, txId: Int, params: ByteArray = EMPTY): PTPContainer =
            PTPContainer(TYPE_COMMAND, op, 0, 0, txId, params)

        fun readFrom(input: InputStream): PTPContainer {
            val lenBytes = readExact(input, 4) ?: throw EOFException("eof reading length")
            val length = ByteBuffer.wrap(lenBytes).order(ByteOrder.BIG_ENDIAN).int
            val header = readExact(input, length)
                ?: throw EOFException("eof reading container body (len=$length)")
            val buf = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
            val type = buf.int
            return when (type) {
                TYPE_COMMAND -> PTPContainer(
                    type = type,
                    operationCode = buf.short.toInt() and 0xFFFF,
                    eventCode = 0,
                    responseCode = 0,
                    transactionId = buf.int,
                    parameters = ByteArray(buf.remaining()).also { buf.get(it) }
                )
                TYPE_DATA -> PTPContainer(
                    type = type,
                    operationCode = buf.short.toInt() and 0xFFFF,
                    eventCode = 0,
                    responseCode = 0,
                    transactionId = buf.int,
                    parameters = ByteArray(buf.remaining()).also { buf.get(it) }
                )
                TYPE_RESPONSE -> PTPContainer(
                    type = type,
                    operationCode = buf.short.toInt() and 0xFFFF,
                    eventCode = 0,
                    responseCode = buf.short.toInt() and 0xFFFF,
                    transactionId = buf.int,
                    parameters = ByteArray(buf.remaining()).also { buf.get(it) }
                )
                TYPE_EVENT -> PTPContainer(
                    type = type,
                    operationCode = 0,
                    eventCode = buf.short.toInt() and 0xFFFF,
                    responseCode = 0,
                    transactionId = buf.int,
                    parameters = ByteArray(buf.remaining()).also { buf.get(it) }
                )
                else -> throw IllegalStateException("Unknown container type $type")
            }
        }

        private fun readExact(input: InputStream, n: Int): ByteArray? {
            val data = ByteArray(n)
            var off = 0
            while (off < n) {
                val r = input.read(data, off, n - off)
                if (r <= 0) return if (off == 0) null else data.copyOf(off)
                off += r
            }
            return data
        }

        val EMPTY = ByteArray(0)
    }

    fun writeTo(out: OutputStream) {
        val dos = DataOutputStream(out)
        val typeSpecificBytes = when (type) {
            TYPE_COMMAND  -> ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN).apply {
                putShort(operationCode.toShort()); putInt(0); putInt(transactionId)
            }.array() + parameters
            TYPE_DATA     -> ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN).apply {
                putShort(operationCode.toShort()); putInt(transactionId)
            }.array() + parameters
            TYPE_RESPONSE -> ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN).apply {
                putShort(operationCode.toShort()); putShort(responseCode.toShort()); putInt(transactionId)
            }.array() + parameters
            TYPE_EVENT    -> ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN).apply {
                putShort(eventCode.toShort()); putInt(0)
            }.array() + parameters
            else -> error("bad type $type")
        }
        val body = ByteBuffer.allocate(4 + typeSpecificBytes.size).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(type); put(typeSpecificBytes)
        }.array()
        dos.writeInt(body.size)
        dos.write(body)
        dos.flush()
    }

    fun describe(): String = when (type) {
        TYPE_COMMAND  -> "CMD  op=0x${operationCode.toString(16)} tx=$transactionId params=${parameters.size}B"
        TYPE_DATA     -> "DATA op=0x${operationCode.toString(16)} tx=$transactionId data=${parameters.size}B"
        TYPE_RESPONSE -> "RESP op=0x${operationCode.toString(16)} code=0x${responseCode.toString(16)} tx=$transactionId params=${parameters.size}B"
        TYPE_EVENT    -> "EVT  code=0x${eventCode.toString(16)} params=${parameters.size}B"
        else -> "???"
    }
}
