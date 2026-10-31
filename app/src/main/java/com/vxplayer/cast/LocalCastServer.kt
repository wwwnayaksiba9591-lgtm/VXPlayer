package com.vxplayer.cast

import android.content.Context
import android.net.*
import android.os.ParcelFileDescriptor
import java.io.*
import java.net.*
import java.security.SecureRandom
import java.util.concurrent.*

/** Serves exactly one readable file, on the Wi-Fi interface only, with an unguessable URL. */
class LocalCastServer(private val context: Context,private val uri: Uri,private val mime: String) : Closeable {
    private val token=ByteArray(24).also {SecureRandom().nextBytes(it)}.joinToString(""){"%02x".format(it.toInt() and 255)}
    private val path="/$token/media"
    private val clients=java.util.Collections.synchronizedSet(mutableSetOf<Socket>())
    private val workers=ThreadPoolExecutor(2,3,10,TimeUnit.SECONDS,ArrayBlockingQueue(8))
    private var server: ServerSocket?=null
    private var size=0L
    @Volatile private var closed=false
    fun start(): String {
        check(!closed) {"Cast session ended."}
        val connectivity=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val address=connectivity.allNetworks.firstNotNullOfOrNull { network ->
            if(connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true)
                connectivity.getLinkProperties(network)?.linkAddresses?.map {it.address}?.filterIsInstance<Inet4Address>()?.firstOrNull { !it.isLoopbackAddress }
            else null
        } ?: error("Connect the phone and TV to the same Wi-Fi network.")
        context.contentResolver.openAssetFileDescriptor(uri,"r")?.use { descriptor ->
            size=descriptor.length.takeIf {it>0} ?: descriptor.parcelFileDescriptor.statSize
            check(size>0) {"This provider does not expose a seekable file."}
            ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(descriptor.fileDescriptor)).use {it.channel.position(descriptor.startOffset)}
        } ?: error("The selected file is unavailable.")
        val socket=ServerSocket(0,8,address).apply {soTimeout=1000};server=socket
        if(closed) {socket.close();error("Cast session ended.")}
        Thread({while(!socket.isClosed) {
            try {
                val client=socket.accept().apply {soTimeout=10_000};clients+=client
                try {workers.execute {try {serve(client)} catch(_: Exception) {} finally {clients-=client;runCatching {client.close()}}}}
                catch(_: RejectedExecutionException){clients-=client;client.close()}
            } catch(_: SocketTimeoutException) {} catch(_: IOException) {break}
        }},"VX-cast-http").apply {isDaemon=true;start()}
        return "http://${address.hostAddress}:${socket.localPort}$path"
    }
    private fun serve(socket: Socket) {
        val input=socket.getInputStream();val header=ByteArrayOutputStream()
        var tail=0
        while(header.size()<8192) {
            val byte=input.read();if(byte<0)return;header.write(byte)
            tail=(tail shl 8) or byte
            if(tail==0x0D0A0D0A)break
        }
        val lines=header.toString("US-ASCII").split("\r\n")
        val request=lines.first().split(' ')
        val output=socket.getOutputStream()
        fun reject(code: String,extra: String="") {output.write("HTTP/1.1 $code\r\n${extra}Content-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())}
        if(request.size!=3 || request[0] !in listOf("GET","HEAD","OPTIONS")) {reject("405 Method Not Allowed");return}
        if(!java.security.MessageDigest.isEqual(request[1].toByteArray(),path.toByteArray())) {reject("404 Not Found");return}
        if(request[0]=="OPTIONS") {reject("204 No Content","Access-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, HEAD, OPTIONS\r\nAccess-Control-Allow-Headers: Range\r\n");return}
        val rangeHeader=lines.drop(1).firstOrNull {it.startsWith("Range:",true)}?.substringAfter(':')?.trim()
        val range=parseHttpRange(rangeHeader,size) ?: run {reject("416 Range Not Satisfiable","Content-Range: bytes */$size\r\n");return}
        context.contentResolver.openAssetFileDescriptor(uri,"r")?.use { descriptor ->
            ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(descriptor.fileDescriptor)).use {file ->
                file.channel.position(descriptor.startOffset+range.first)
                output.write(buildString {
                    val safeMime=mime.takeIf {Regex("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+").matches(it)} ?: "video/mp4"
                    append("HTTP/1.1 ${if(rangeHeader==null)"200 OK" else "206 Partial Content"}\r\nContent-Type: $safeMime\r\nAccept-Ranges: bytes\r\nContent-Length: ${range.length}\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges\r\nCache-Control: no-store\r\n")
                    if(rangeHeader!=null)append("Content-Range: bytes ${range.first}-${range.last}/$size\r\n")
                    append("Connection: close\r\n\r\n")
                }.toByteArray())
                if(request[0]=="HEAD")return
                val buffer=ByteArray(64*1024);var remaining=range.length
                while(remaining>0) {val count=file.read(buffer,0,minOf(buffer.size.toLong(),remaining).toInt());if(count<0)break;output.write(buffer,0,count);remaining-=count}
            }
        } ?: reject("404 Not Found")
    }
    override fun close() {closed=true;runCatching {server?.close()};server=null;synchronized(clients){clients.toList().forEach {runCatching {it.close()}};clients.clear()};workers.shutdownNow()}
}
