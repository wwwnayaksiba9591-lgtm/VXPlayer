package com.vxplayer.library

enum class LibrarySort { Name, Date, Size, Duration }

fun <T> orderLibrary(items: List<T>, sort: LibrarySort, descending: Boolean,
    name: (T) -> String, date: (T) -> Long, size: (T) -> Long, duration: (T) -> Long): List<T> {
    val comparator = when (sort) {
        LibrarySort.Name -> compareBy<T> { name(it).lowercase() }
        LibrarySort.Date -> compareBy(date)
        LibrarySort.Size -> compareBy(size)
        LibrarySort.Duration -> compareBy(duration)
    }.thenBy { name(it).lowercase() }
    return items.sortedWith(if (descending) comparator.reversed() else comparator)
}
