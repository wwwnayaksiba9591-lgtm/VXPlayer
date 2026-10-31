package com.vxplayer.cast

data class HttpRange(val first: Long,val last: Long) {val length get()=last-first+1}
fun parseHttpRange(header: String?,size: Long): HttpRange? {
    if(size<=0)return null
    if(header==null)return HttpRange(0,size-1)
    val match=Regex("bytes=([0-9]*)-([0-9]*)").matchEntire(header.trim()) ?: return null
    val left=match.groupValues[1];val right=match.groupValues[2]
    if(left.isEmpty()) {
        val suffix=right.toLongOrNull()?.takeIf {it>0} ?: return null
        return HttpRange((size-suffix).coerceAtLeast(0),size-1)
    }
    val start=left.toLongOrNull()?.takeIf {it in 0 until size} ?: return null
    val end=if(right.isEmpty())size-1 else right.toLongOrNull()?.coerceAtMost(size-1) ?: return null
    return if(end<start)null else HttpRange(start,end)
}
