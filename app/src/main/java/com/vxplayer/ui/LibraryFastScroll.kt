package com.vxplayer.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

fun Modifier.libraryFastScroll(state: LazyListState)=fastScroll(state,
    {Triple(state.layoutInfo.totalItemsCount,state.firstVisibleItemIndex,state.layoutInfo.visibleItemsInfo.size)},{state.scrollToItem(it)})
fun Modifier.libraryFastScroll(state: LazyGridState)=fastScroll(state,
    {Triple(state.layoutInfo.totalItemsCount,state.firstVisibleItemIndex,state.layoutInfo.visibleItemsInfo.size)},{state.scrollToItem(it)})
private fun Modifier.fastScroll(key: Any,metrics: ()->Triple<Int,Int,Int>,scroll: suspend (Int)->Unit): Modifier=composed {
    val scope=rememberCoroutineScope();val color=MaterialTheme.colorScheme.primary
    drawWithContent {
        drawContent()
        val (total,first,visible)=metrics()
        if(total>visible && total>0) {
            val height=56.dp.toPx().coerceAtMost(size.height)
            val y=(size.height-height)*(first.toFloat()/(total-visible)).coerceIn(0f,1f)
            drawRoundRect(color,Offset(size.width-6.dp.toPx(),y),Size(4.dp.toPx(),height),androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
        }
    }.pointerInput(key) {
        awaitEachGesture {
            val down=awaitFirstDown(false,PointerEventPass.Initial)
            if(down.position.x<size.width-48.dp.toPx())return@awaitEachGesture
            var dragging=false
            do {
                val event=awaitPointerEvent(PointerEventPass.Initial)
                val point=event.changes.firstOrNull {it.id==down.id} ?: break
                val (total,_,visible)=metrics()
                if(total>visible && kotlin.math.abs(point.position.y-down.position.y)>16.dp.toPx())dragging=true
                if(dragging) {
                    point.consume()
                    val index=(point.position.y/size.height.coerceAtLeast(1)*total).toInt().coerceIn(0,(total-1).coerceAtLeast(0))
                    scope.launch {scroll(index)}
                }
            }while(event.changes.any {it.pressed})
        }
    }
}
