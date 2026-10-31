package com.vxplayer.music

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vxplayer.player.rememberOrientationController
import com.vxplayer.ui.*

@Composable
fun MusicPlayerOverlay(model: MusicViewModel,stats: List<MusicStat>,onInfo: ()->Unit,onShare: ()->Unit,lyrics: @Composable ()->Unit) {
    val context=LocalContext.current
    val orientation=rememberOrientationController()
    val art by musicArtwork(model.current)
    val background by animateColorAsState(lerp(Color.Black,Color(art?.color ?: 0xFF293543.toInt()),.45f),label="Artwork background")
    var menu by remember {mutableStateOf(false)}
    var showLyrics by remember {mutableStateOf(false)}
    var speed by remember {mutableStateOf(false)}
    var actions by remember {mutableStateOf(false)}
    var customise by remember {mutableStateOf(false)}
    var night by remember {mutableStateOf(false)}
    val (layout,saveLayout)=rememberQuickActions()
    val service=MusicPlaybackService.instance
    val favourite=stats.any {it.uri==model.current?.uri && it.favourite}
    fun toast(value: String){Toast.makeText(context,value,Toast.LENGTH_SHORT).show()}
    Dialog(onDismissRequest={model.nowPlaying=false},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(background,Color.Black)))
                .quickActionEdge {actions=true}) {
                val artworkSize=minOf(maxWidth*.78f,340.dp)
                Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        IconButton(onClick={model.nowPlaying=false}) {Icon(Icons.AutoMirrored.Filled.ArrowBack,"Collapse player",tint=Color.White)}
                        Text("Now playing",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                        Box {
                            IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"Track options",tint=Color.White)}
                            DropdownMenu(menu,{menu=false}) {
                                DropdownMenuItem(text={Text("Track info")},onClick={menu=false;onInfo()})
                                DropdownMenuItem(text={Text("Share")},onClick={menu=false;onShare()})
                                DropdownMenuItem(text={Text(if(showLyrics)"Artwork" else "Lyrics")},onClick={menu=false;showLyrics=!showLyrics})
                                DropdownMenuItem(text={Text("Quick actions")},onClick={menu=false;actions=true})
                                DropdownMenuItem(text={Text("Customise items")},onClick={menu=false;customise=true})
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    SongArt(model.current,Modifier.size(artworkSize))
                    Spacer(Modifier.height(24.dp))
                    Text(model.current?.title ?: "Nothing queued",style=MaterialTheme.typography.headlineSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text(model.current?.artist.orEmpty(),color=Color.White.copy(alpha=.65f),style=MaterialTheme.typography.titleMedium,maxLines=2)
                    Spacer(Modifier.height(12.dp))
                    MusicSeekBar(model,{}) {}
                    Box(Modifier.fillMaxWidth().height(96.dp)) {
                        IconButton(onClick=model::shuffleToggle,modifier=Modifier.align(Alignment.CenterStart)) {
                            Icon(Icons.Default.Shuffle,"Shuffle ${if(model.shuffle)"on" else "off"}",tint=if(model.shuffle)MaterialTheme.colorScheme.primary else Color.White)
                        }
                        Row(Modifier.align(Alignment.Center),verticalAlignment=Alignment.CenterVertically) {
                            IconButton(onClick=model::previous){Icon(Icons.Default.SkipPrevious,"Previous",tint=Color.White)}
                            FilledIconButton(onClick=model::toggle,shape=CircleShape,modifier=Modifier.size(76.dp)) {
                                if(model.buffering)CircularProgressIndicator(Modifier.size(32.dp),color=MaterialTheme.colorScheme.onPrimary)
                                else Icon(if(model.playing)Icons.Default.Pause else Icons.Default.PlayArrow,if(model.playing)"Pause" else "Play",Modifier.size(40.dp))
                            }
                            IconButton(onClick=model::next){Icon(Icons.Default.SkipNext,"Next",tint=Color.White)}
                        }
                        IconButton(onClick=model::repeatToggle,modifier=Modifier.align(Alignment.CenterEnd)) {
                            Icon(if(model.repeat==1)Icons.Default.RepeatOne else Icons.Default.Repeat,"Repeat ${when(model.repeat){1->"one";2->"all";else->"off"}}",tint=if(model.repeat!=0)MaterialTheme.colorScheme.primary else Color.White)
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.SpaceEvenly) {
                        MusicAction(Icons.Default.Timer,"Sleep",{model.showSleep=true})
                        MusicAction(Icons.Default.Equalizer,"Equalizer",{model.showEqualizer=true})
                        MusicAction(Icons.Default.Speed,speedActionLabel(model.speed),{speed=true})
                        MusicAction(Icons.Default.QueueMusic,"Queue",{model.showQueue=true})
                        MusicAction(if(favourite)Icons.Default.Favorite else Icons.Default.FavoriteBorder,"Favourite",{model.current?.let(model::favourite)},favourite)
                    }
                    if(showLyrics)lyrics()
                    model.message?.let { Text(it,Modifier.padding(top=12.dp),color=Color.White.copy(alpha=.7f)) }
                }
                if(night)Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.25f)))
                IconButton(onClick={actions=true},modifier=Modifier.align(Alignment.CenterEnd).background(Color.Black.copy(alpha=.35f),CircleShape)) {
                    Icon(Icons.Default.ChevronLeft,"Open quick actions",tint=Color.White)
                }
                PlayerQuickActions(layout.copy(hidden=layout.hidden+setOf(PlayerAction.Screenshot)),buildSet {
                    if(night)add(PlayerAction.Night);if(model.shuffle)add(PlayerAction.Shuffle);if(model.repeat!=0)add(PlayerAction.Loop)
                    if(model.muted)add(PlayerAction.Mute);if(service?.aMs!=null)add(PlayerAction.RepeatAB)
                    if(service?.sleepLabel!="Off" && service!=null)add(PlayerAction.Sleep)
                    if(service?.allowBackground==true)add(PlayerAction.Background)
                    if(model.speed!=1f)add(PlayerAction.Speed);if(orientation.locked)add(PlayerAction.RotationLock)
                },labels=mapOf(PlayerAction.Speed to speedActionLabel(model.speed)),touch={},action={action->when(action){
                    PlayerAction.Night->night=!night
                    PlayerAction.Customise->{actions=false;customise=true}
                    PlayerAction.Shuffle->model.shuffleToggle()
                    PlayerAction.Loop->model.repeatToggle()
                    PlayerAction.Mute->model.toggleMute()
                    PlayerAction.Sleep->{actions=false;model.showSleep=true}
                    PlayerAction.RepeatAB->toast(service?.cycleRepeatAB() ?: "Music controls are connecting.")
                    PlayerAction.Equalizer->{actions=false;model.showEqualizer=true}
                    PlayerAction.Speed->{actions=false;speed=true}
                    PlayerAction.Rotation->{orientation.cycle();toast("Rotation: ${orientation.mode.label}")}
                    PlayerAction.RotationLock->orientation.toggleLock(com.vxplayer.player.currentOrientation(context.resources.configuration.orientation==2,0))
                    PlayerAction.Background->service?.toggleBackground()
                    PlayerAction.Screenshot->toast("Open a video to capture a video frame.")
                }},open=actions,close={actions=false})
            }
        }
    }
    if(customise)CustomiseActionsSheet(layout,saveLayout){customise=false}
    if(speed)AlertDialog(onDismissRequest={speed=false},title={Text("Playback speed")},text={Column {
        listOf(.5f,.75f,1f,1.25f,1.5f,2f).forEach {value->TextButton(onClick={model.changeSpeed(value);speed=false}){Text(speedActionLabel(value))}}
    }},confirmButton={TextButton(onClick={speed=false}){Text("Close")}})
}

@Composable private fun MusicAction(icon: ImageVector,label: String,action: ()->Unit,selected: Boolean=false) {
    Column(Modifier.width(64.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        IconButton(onClick=action){Icon(icon,label,tint=if(selected)MaterialTheme.colorScheme.primary else Color.White)}
        Text(label,style=MaterialTheme.typography.labelSmall,color=Color.White)
    }
}
