package com.vxplayer.player

enum class AspectMode(val label: String,val ratio: Float=0f) {
    Fit("Fit"), Fill("Fill"), Zoom("Zoom"), Wide("16:9",16f/9), Classic("4:3",4f/3), Original("Original");
    fun next()=entries[(ordinal+1)%entries.size]
    companion object {
        fun parse(value: Any?,legacy: Int=0)=entries.firstOrNull {it.name==value}
            ?: when(legacy){1->Fill;2->Zoom;3->Original;else->Fit}
    }
}
