package com.vxplayer.ui

/** The rightmost 24 dp belongs to the action panel, never to seeking or volume. */
fun opensActionPanel(startX: Float, width: Float, dx: Float, dy: Float): Boolean =
    width > 0 && startX >= width - 24f && dx < -16f && kotlin.math.abs(dx) > kotlin.math.abs(dy)

enum class PlayerAction(val label: String) {
    Night("Night Mode"), Customise("Customise Items"), Shuffle("Shuffle"), Loop("Loop"), Mute("Mute"),
    Sleep("Sleep Timer"), RepeatAB("A-B Repeat"), Equalizer("Equalizer"), Speed("Speed"),
    Screenshot("Screenshot"), Rotation("Screen Rotation"), Background("Background Play"), RotationLock("Rotation Lock")
}
data class QuickActionLayout(val order: List<PlayerAction> = PlayerAction.entries.toList(),val hidden: Set<PlayerAction> = emptySet()) {
    fun move(action: PlayerAction, delta: Int): QuickActionLayout {
        val from = order.indexOf(action);val to = from+delta
        if (from < 0 || to !in order.indices) return this
        return copy(order=order.toMutableList().apply { add(to,removeAt(from)) })
    }
    fun visible(action: PlayerAction, show: Boolean) = copy(hidden=if(show)hidden-action else hidden+action)
}
fun parseQuickActions(order: String?, hidden: String?): QuickActionLayout {
    fun parse(raw: String?) = raw.orEmpty().split(',').mapNotNull { name -> PlayerAction.entries.firstOrNull { it.name==name } }.distinct()
    val saved = parse(order)
    return QuickActionLayout(saved+PlayerAction.entries.filter { it !in saved },parse(hidden).toSet())
}
fun speedActionLabel(value: Float): String {
    val speed = value.takeIf { it.isFinite() && it>0 } ?: 1f
    return if(speed%1f==0f)"${speed.toInt()}X" else "${speed}X"
}
