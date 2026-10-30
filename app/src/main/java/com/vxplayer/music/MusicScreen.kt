package com.vxplayer.music

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import com.vxplayer.library.formatDuration
import com.vxplayer.ui.VXIconButton
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
private fun musicArtwork(song: Song?): State<MusicArtwork?> {
    val context = LocalContext.current
    return produceState<MusicArtwork?>(null,song?.uri) {
        value = song?.let { withContext(Dispatchers.IO) { MusicArt.load(context,it) } }
    }
}
@Composable
fun SongArt(song: Song?, modifier: Modifier = Modifier, circle: Boolean = false) {
    val art by musicArtwork(song)
    val background = art?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primaryContainer
    Box(modifier.clip(if (circle) CircleShape else RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(background,background.copy(alpha=.4f)))),contentAlignment = Alignment.Center) {
        art?.bitmap?.let { Image(it.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale = ContentScale.Crop) }
            ?: Icon(Icons.Default.MusicNote,null,Modifier.fillMaxSize(.45f),tint = Color.White)
    }
}
@Composable
private fun musicPosition(model: MusicViewModel): State<Long> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(0L,model.controller,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) { val next = model.controller?.currentPosition?.coerceAtLeast(0) ?: 0; if (next != value) value = next; delay(400) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable
fun MusicScreen(model: MusicViewModel, onSettings: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var allowed by remember { mutableStateOf(model.allowed()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it; if (it) model.refresh() }
    LaunchedEffect(Unit) { if (allowed) model.refresh() else if (!asked) { asked = true; permission.launch(model.permission) } }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _,event -> if (event == Lifecycle.Event.ON_RESUME) { allowed = model.allowed(); if (allowed) model.refresh() } }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    val stats by model.stats.collectAsStateWithLifecycle()
    val playlists by model.playlists.collectAsStateWithLifecycle()
    val entries by model.playlistItems.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf("Home") }
    var query by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var playlistId by rememberSaveable { mutableStateOf<String?>(null) }
    var editPlaylist by remember { mutableStateOf<MusicPlaylist?>(null) }
    var playlistName by rememberSaveable { mutableStateOf("") }
    var addSong by remember { mutableStateOf<Song?>(null) }
    var info by remember { mutableStateOf<Song?>(null) }
    val favourites = stats.filter { it.favourite }.map { it.uri }.toSet()
    val songs = sortSongs(model.songs.filter { query.isBlank() || listOf(it.title,it.artist,it.album).any { field -> field.contains(query,true) } },model.sort)
    val detailTitle = detail?.let { selected -> if (section == "Artists") selected else model.songs.firstOrNull { "${it.volume}:${it.albumId}:${it.album}" == selected }?.album }
    fun share(song: Song) { shareSong(context,song) }
    BackHandler(detail != null || playlistId != null) { detail = null; playlistId = null }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment = Alignment.CenterVertically) {
            if (detail != null || playlistId != null) VXIconButton(Icons.AutoMirrored.Filled.ArrowBack,"Back",{ detail = null; playlistId = null })
            Text(detailTitle ?: playlists.firstOrNull { it.id == playlistId }?.name ?: if (playlistId == "favourites") "Favourites" else "Music",Modifier.weight(1f),style = MaterialTheme.typography.headlineLarge,maxLines = 1,overflow = TextOverflow.Ellipsis)
            VXIconButton(Icons.Default.Search,"Search songs, albums and artists",{ search = !search })
            Box {
                VXIconButton(Icons.Default.MoreVert,"Music menu",{ menu = true })
                DropdownMenu(menu,{ menu = false }) {
                    DropdownMenuItem(text={Text("Sort")},onClick={menu=false;sortMenu=true})
                    DropdownMenuItem(text={Text("Refresh")},onClick={menu=false;model.refresh()})
                    DropdownMenuItem(text={Text("Equalizer")},onClick={menu=false;model.showEqualizer=true})
                    DropdownMenuItem(text={Text("Sleep timer")},onClick={menu=false;model.showSleep=true})
                    DropdownMenuItem(text={Text("Settings")},onClick={menu=false;onSettings()})
                }
                DropdownMenu(sortMenu,{sortMenu=false}) { MusicSort.entries.forEach { sort -> DropdownMenuItem(text={Text(sort.name)},onClick={model.changeSort(sort);sortMenu=false}) } }
            }
        }
        if (search) OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(horizontal=16.dp),singleLine=true,label={Text("Search songs, albums, artists")})
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=16.dp),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Home","Songs","Albums","Artists","Playlists").forEach { value -> FilterChip(section==value,{section=value;detail=null;playlistId=null},label={Text(value)}) }
        }
        model.message?.let { Text(it,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error) }
        when {
            !allowed -> Column(Modifier.fillMaxWidth().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.LibraryMusic,null,Modifier.size(64.dp)); Text("Your music library",style=MaterialTheme.typography.titleLarge)
                Text("Plays music stored on your device.")
                Text("Allow audio access to browse your songs. If access is limited by your device or work profile, only available tracks appear.")
                Button(onClick={permission.launch(model.permission)}) {Text("Allow music")}
                TextButton(onClick={context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}) {Text("App settings")}
            }
            model.loading && model.songs.isEmpty() -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
            else -> PullToRefreshBox(model.loading,{model.refresh()},Modifier.weight(1f)) {
                val selected = when {
                    playlistId == "favourites" -> songs.filter { it.uri in favourites }
                    playlistId != null -> entries.filter { it.playlistId == playlistId }.mapNotNull { entry -> songs.firstOrNull { it.uri == entry.uri } }
                    detail != null && section == "Albums" -> songs.filter { "${it.volume}:${it.albumId}:${it.album}" == detail }
                    detail != null && section == "Artists" -> songs.filter { it.artist == detail }
                    else -> songs
                }
                if (songs.isEmpty()) Column(Modifier.fillMaxWidth().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.MusicNote,null,Modifier.size(64.dp)); Text("No music found",style=MaterialTheme.typography.titleLarge)
                    Text("Plays music stored on your device."); Text("Add audio files to Music, then refresh. Ringtones, alarms and recordings are excluded.")
                    Row(verticalAlignment=Alignment.CenterVertically) { Text("Hide tracks shorter than 30 s",Modifier.weight(1f)); Switch(model.hideShort,{model.changeHideShort(it)}) }
                    OutlinedButton(onClick={model.refresh()}) {Text("Refresh")}
                } else if (detail != null || playlistId != null || section == "Songs") {
                    val art by musicArtwork(selected.firstOrNull())
                    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        if (detail != null || playlistId != null) item {
                            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(art?.color ?: 0xFF293543.toInt()).copy(alpha=.5f),Color.Transparent))).padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                SongArt(selected.firstOrNull(),Modifier.size(180.dp),section=="Artists")
                                Text(if (playlistId != null) playlists.firstOrNull { it.id==playlistId }?.name ?: "Favourites" else selected.firstOrNull()?.let { if(section=="Artists")it.artist else it.album }.orEmpty(),style=MaterialTheme.typography.headlineSmall)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Button(onClick={selected.firstOrNull()?.let {model.play(it,selected)}},enabled=selected.isNotEmpty()){Text("Play")}
                                    OutlinedButton(onClick={selected.randomOrNull()?.let {model.play(it,selected,true)}},enabled=selected.isNotEmpty()){Text("Shuffle")}
                                }
                                playlists.firstOrNull { it.id==playlistId }?.let { list -> Row {
                                    TextButton(onClick={editPlaylist=list;playlistName=list.name}){Text("Rename")}
                                    TextButton(onClick={model.deletePlaylist(list.id);playlistId=null}){Text("Delete playlist")}
                                } }
                            }
                        }
                        itemsIndexed(selected,key={_,song->song.uri}) { index,song ->
                            val listId=playlistId?.takeUnless { it=="favourites" }
                            SongRow(song,model.current?.uri==song.uri&&model.playing,song.uri in favourites,
                                play={model.play(song,selected)},next={model.add(song,true)},add={model.add(song,false)},
                                favouriteAction={model.favourite(song)},playlist={addSong=song},share={share(song)},info={info=song},
                                handle = if(listId==null) null else { to -> model.updatePlaylist(listId,reorderMusic(selected.map {it.uri},index,to.coerceIn(selected.indices))) },
                                index=index,count=selected.size,remove=if(listId==null)null else { {model.updatePlaylist(listId,selected.filter {it.uri!=song.uri}.map {it.uri})} })
                        }
                    }
                } else when (section) {
                    "Home" -> LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                        item { Button(onClick={songs.randomOrNull()?.let {model.play(it,songs,true)}},Modifier.fillMaxWidth()) {Icon(Icons.Default.Shuffle,null);Text("Shuffle all")} }
                        val recent=stats.sortedByDescending {it.lastPlayed}.filter {it.lastPlayed>0}.mapNotNull {stat->songs.firstOrNull {it.uri==stat.uri}}
                        val most=stats.sortedByDescending {it.playCount}.filter {it.playCount>0}.mapNotNull {stat->songs.firstOrNull {it.uri==stat.uri}}
                        listOf("Recently played" to recent,"Most played" to most,"Recently added" to songs.sortedByDescending {it.added},"Favourites" to songs.filter {it.uri in favourites}).forEach { (label,row) ->
                            item { Text(label,style=MaterialTheme.typography.titleLarge) }
                            item { if(row.isEmpty()) Text("Your listening history will appear here.",style=MaterialTheme.typography.bodySmall) else LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                items(row.take(20),key={it.uri}) { song ->
                                    Column(Modifier.width(132.dp).clickable {model.play(song,row)}) {
                                        SongArt(song,Modifier.size(132.dp))
                                        Text(song.title,maxLines=1,overflow=TextOverflow.Ellipsis)
                                        Text(song.artist,maxLines=1,style=MaterialTheme.typography.bodySmall)
                                    }
                                }
                            } }
                        }
                    }
                    "Albums","Artists" -> {
                        val groups=songs.groupBy {if(section=="Artists")it.artist else "${it.volume}:${it.albumId}:${it.album}"}
                        LazyVerticalGrid(GridCells.Fixed(2),contentPadding=PaddingValues(16.dp),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            items(groups.keys.toList(),key={it}) { key -> val group=groups.getValue(key); Column(Modifier.clickable {detail=key}) {
                                SongArt(group.first(),Modifier.fillMaxWidth().aspectRatio(1f),section=="Artists")
                                Text(if(section=="Artists")group.first().artist else group.first().album,maxLines=1,overflow=TextOverflow.Ellipsis)
                                Text("${group.size} songs",style=MaterialTheme.typography.bodySmall)
                            } }
                        }
                    }
                    "Playlists" -> LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        item { Button(onClick={editPlaylist=MusicPlaylist("","");playlistName=""}){Icon(Icons.Default.Add,null);Text("Create playlist")} }
                        item { ListItem(headlineContent={Text("Favourites")},leadingContent={Icon(Icons.Default.Favorite,null)},modifier=Modifier.clickable {playlistId="favourites"}) }
                        items(playlists,key={it.id}) { list ->
                            val cover=entries.filter {it.playlistId==list.id}.take(4).mapNotNull {e->songs.firstOrNull {it.uri==e.uri}}
                            ListItem(headlineContent={Text(list.name)},supportingContent={Text("${entries.count {it.playlistId==list.id}} songs")},leadingContent={
                                Column(Modifier.size(64.dp)) { cover.chunked(2).forEach { pair -> Row(Modifier.weight(1f)) { pair.forEach {SongArt(it,Modifier.weight(1f).fillMaxHeight())} } };if(cover.isEmpty())Icon(Icons.Default.QueueMusic,null) }
                            },modifier=Modifier.clickable {playlistId=list.id})
                        }
                    }
                }
            }
        }
    }
    editPlaylist?.let { list -> AlertDialog(onDismissRequest={editPlaylist=null},title={Text(if(list.id.isBlank())"Create playlist" else "Rename playlist")},text={OutlinedTextField(playlistName,{playlistName=it.take(120)},label={Text("Name")})},
        confirmButton={TextButton(onClick={if(list.id.isBlank())model.playlist(playlistName) else model.playlist(playlistName,list.id);editPlaylist=null},enabled=playlistName.isNotBlank()){Text("Save")}},dismissButton={TextButton(onClick={editPlaylist=null}){Text("Cancel")}}) }
    addSong?.let { song -> AlertDialog(onDismissRequest={addSong=null},title={Text("Add to playlist")},text={Column(Modifier.verticalScroll(rememberScrollState())){
        if(playlists.isEmpty())Text("Create a playlist in the Playlists section first.")
        playlists.forEach { list -> TextButton(onClick={model.updatePlaylist(list.id,addPlaylistSong(entries.filter {it.playlistId==list.id}.map {it.uri},song.uri));addSong=null}){Text(list.name)} }
    }},confirmButton={TextButton(onClick={addSong=null}){Text("Close")}}) }
    info?.let { song -> AlertDialog(onDismissRequest={info=null},title={Text(song.title)},text={Text("${song.artist}\n${song.album}\n${formatDuration(song.duration)}\nTrack ${song.track} · ${song.year.takeIf {it>0} ?: "Unknown year"}")},confirmButton={TextButton(onClick={info=null}){Text("Close")}}) }
}

@Composable
private fun SongRow(song: Song, active: Boolean, favourite: Boolean, play: () -> Unit, next: () -> Unit, add: () -> Unit,
    favouriteAction: (() -> Unit)? = null, playlist: () -> Unit, share: () -> Unit, info: () -> Unit,
    handle: ((Int) -> Unit)? = null,index: Int = 0,count: Int = 0,remove: (() -> Unit)? = null,
    queueOnly: Boolean = false) {
    var menu by remember {mutableStateOf(false)}
    val rowHeight=with(LocalDensity.current){72.dp.toPx()}
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick=play).padding(6.dp),verticalAlignment=Alignment.CenterVertically) {
        SongArt(song,Modifier.size(56.dp));Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {Text(song.title,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${song.artist} · ${song.album}",style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)}
        if(active) PlayingBars() else Text(formatDuration(song.duration),style=MaterialTheme.typography.labelSmall)
        if(handle!=null) {
            var distance by remember {mutableFloatStateOf(0f)}
            Icon(Icons.Default.DragHandle,"Long press and drag to reorder",Modifier.size(48.dp).pointerInput(index,count){detectDragGesturesAfterLongPress(onDragStart={distance=0f},onDragEnd={handle((index+(distance/rowHeight).roundToInt()).coerceIn(0,(count-1).coerceAtLeast(0)))}) {change,drag->change.consume();distance+=drag.y}})
        }
        Box { VXIconButton(Icons.Default.MoreVert,"Song menu",{menu=true});DropdownMenu(menu,{menu=false}) {
            (listOf("Play next" to next,"Add to queue" to add) + if(queueOnly)emptyList() else listOf("Add to playlist" to playlist,"Share" to share,"Info" to info)).forEach {(label,action)->DropdownMenuItem(text={Text(label)},onClick={menu=false;action()})}
            DropdownMenuItem(text={Text(if(favourite)"Unfavourite" else "Favourite")},onClick={menu=false;favouriteAction?.invoke()})
            if(remove!=null)DropdownMenuItem(text={Text("Remove")},onClick={menu=false;remove()})
        } }
    }
}

@Composable
private fun PlayingBars() {
    val transition=rememberInfiniteTransition(label="Playing")
    val height by transition.animateFloat(.25f,1f,infiniteRepeatable(tween(450),RepeatMode.Reverse),label="bars")
    Row(Modifier.size(28.dp),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(3.dp)) {
        repeat(3) { index -> Box(Modifier.width(4.dp).fillMaxHeight(if(index==1)height else 1.1f-height*.7f).background(MaterialTheme.colorScheme.primary,RoundedCornerShape(2.dp))) }
    }
}

@Composable
fun MusicMiniPlayer(model: MusicViewModel) {
    val song=model.current ?: return
    var distance by remember {mutableFloatStateOf(0f)}
    Card(onClick={model.nowPlaying=true},modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp).pointerInput(song.uri){detectHorizontalDragGestures(onDragStart={distance=0f},onDragEnd={if(abs(distance)>60){if(distance<0)model.next() else model.previous()}}){change,drag->change.consume();distance+=drag}}) {
        Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
            SongArt(song,Modifier.size(48.dp));Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(song.title,maxLines=1,overflow=TextOverflow.Ellipsis);Text(song.artist,maxLines=1,style=MaterialTheme.typography.bodySmall)}
            VXIconButton(if(model.playing)Icons.Default.Pause else Icons.Default.PlayArrow,"Play or pause",model::toggle)
            VXIconButton(Icons.Default.SkipNext,"Next",model::next)
        }
        MusicThinProgress(model)
    }
}
@Composable private fun MusicThinProgress(model: MusicViewModel) {
    val position by musicPosition(model)
    val duration=model.controller?.duration?.takeIf {it>0} ?: 0
    LinearProgressIndicator(progress={if(duration>0)(position.toFloat()/duration).coerceIn(0f,1f) else 0f},Modifier.fillMaxWidth().height(2.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicOverlays(model: MusicViewModel) {
    val context = LocalContext.current
    var nowMenu by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    val stats by model.stats.collectAsStateWithLifecycle()
    if(model.nowPlaying) Dialog(onDismissRequest={model.nowPlaying=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        var shown by remember {mutableStateOf(false)};LaunchedEffect(Unit){shown=true}
        AnimatedVisibility(shown,enter=slideInVertically {it}) {
            val song=model.current
            val art by musicArtwork(song)
            val shade by animateColorAsState(Color(art?.color ?: 0xFF293543.toInt()),label="Album background")
            Surface(Modifier.fillMaxSize()) {Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(shade.copy(alpha=.5f),Color.Transparent))).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                var drag by remember {mutableFloatStateOf(0f)}
                Row(Modifier.fillMaxWidth().pointerInput(Unit){detectVerticalDragGestures(onDragEnd={if(drag>70)model.nowPlaying=false}){change,amount->change.consume();drag+=amount}},verticalAlignment=Alignment.CenterVertically) {
                    VXIconButton(Icons.Default.KeyboardArrowDown,"Collapse",{model.nowPlaying=false});Text("Now playing",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                    VXIconButton(Icons.Default.QueueMusic,"Queue",{model.showQueue=true})
                    Box {
                        VXIconButton(Icons.Default.MoreVert,"Now playing menu",{nowMenu=true})
                        DropdownMenu(nowMenu,{nowMenu=false}) {
                            DropdownMenuItem(text={Text("Share track")},onClick={nowMenu=false;song?.let {shareSong(context,it)}})
                            DropdownMenuItem(text={Text("Track info")},onClick={nowMenu=false;info=true})
                        }
                    }
                }
                SongArt(song,Modifier.widthIn(max=360.dp).fillMaxWidth().aspectRatio(1f))
                Text(song?.title ?: "Nothing queued",style=MaterialTheme.typography.headlineSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(song?.artist.orEmpty(),style=MaterialTheme.typography.titleMedium)
                VXIconButton(if(stats.any {it.uri==song?.uri&&it.favourite})Icons.Default.Favorite else Icons.Default.FavoriteBorder,"Favourite",{song?.let(model::favourite)})
                MusicSeekBar(model)
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly) {
                    IconToggleButton(model.shuffle,{model.shuffleToggle()}){Icon(Icons.Default.Shuffle,"Shuffle")}
                    VXIconButton(Icons.Default.SkipPrevious,"Previous",model::previous)
                    FilledIconButton(model::toggle,Modifier.size(76.dp)){Icon(if(model.playing)Icons.Default.Pause else Icons.Default.PlayArrow,"Play or pause",Modifier.size(42.dp))}
                    VXIconButton(Icons.Default.SkipNext,"Next",model::next)
                    IconButton(model::repeatToggle){Icon(if(model.repeat==1)Icons.Default.RepeatOne else Icons.Default.Repeat,"Repeat: ${when(model.repeat){1->"one";2->"all";else->"off"}}",tint=if(model.repeat==0)MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)}
                }
                Row {
                    TextButton({model.showSleep=true}){Text("Sleep timer")};TextButton({model.showEqualizer=true}){Text("Equalizer")}
                }
                MusicLyrics(model,song)
            } }
        }
    }
    if(model.showQueue) MusicQueueDialog(model){model.showQueue=false}
    if(info) AlertDialog(onDismissRequest={info=false},title={Text(model.current?.title ?: "Track info")},text={Text("${model.current?.artist.orEmpty()}\n${model.current?.album.orEmpty()}\n${formatDuration(model.controller?.duration?.takeIf {it>0} ?: 0)}")},confirmButton={TextButton(onClick={info=false}){Text("Close")}})
    if(model.showEqualizer) {
        val engine=MusicPlaybackService.instance?.effects
        AlertDialog(onDismissRequest={model.showEqualizer=false},title={Text("Music equalizer")},text={Column(Modifier.verticalScroll(rememberScrollState())){
            if(engine==null)Text("Music service is connecting. Try again.") else {
                Text("Supported effects apply to the music audio session.")
                listOf("Original","Vivid","Cinema","Sports/Action","Night").forEach {mode->TextButton(onClick={engine.smartProfile(mode,mode!="Original")}){Text(mode)}}
                Text("Bass ${engine.bassStrength/10}%")
                Slider(engine.bassStrength.toFloat(),{engine.updateBass(it.roundToInt())},valueRange=0f..1000f,enabled=engine.bassAvailable)
                Row(verticalAlignment=Alignment.CenterVertically){Text("Night compression",Modifier.weight(1f));Switch(engine.night,{engine.updateNight(it)},enabled=engine.nightAvailable)}
                if(!engine.smartAvailable)Text("Equalizer or spatial effects are unavailable on this output.")
            }
        }},confirmButton={TextButton(onClick={model.showEqualizer=false}){Text("Close")}})
    }
    if(model.showSleep) AlertDialog(onDismissRequest={model.showSleep=false},title={Text("Sleep timer")},text={Column{
        Text(MusicPlaybackService.instance?.sleepLabel ?: "Off")
        listOf(0,15,30,60).forEach {minutes->TextButton(onClick={MusicPlaybackService.instance?.setSleep(minutes);model.showSleep=false}){Text(if(minutes==0)"Off" else "$minutes minutes")}}
        TextButton(onClick={MusicPlaybackService.instance?.setSleep(0,true);model.showSleep=false}){Text("End of track")}
    }},confirmButton={TextButton(onClick={model.showSleep=false}){Text("Close")}})
}

private fun shareSong(context: android.content.Context,song: Song) {
    runCatching {
        val uri = Uri.parse(song.uri)
        val intent = Intent(Intent.ACTION_SEND).setType("audio/*").putExtra(Intent.EXTRA_STREAM,uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri("Audio",uri) }
        context.startActivity(Intent.createChooser(intent,"Share audio"))
    }
}

@Composable private fun MusicSeekBar(model: MusicViewModel) {
    val position by musicPosition(model)
    var preview by remember {mutableStateOf<Float?>(null)}
    val duration=model.controller?.duration?.takeIf {it>0} ?: 0
    Column {Slider(preview ?: position.coerceIn(0,duration.coerceAtLeast(1)).toFloat(),{preview=it},onValueChangeFinished={preview?.let {model.controller?.seekTo(it.toLong())};preview=null},valueRange=0f..duration.coerceAtLeast(1).toFloat(),enabled=duration>0)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(formatDuration(preview?.toLong() ?: position));Text(formatDuration(duration))}}
}
@Composable private fun MusicLyrics(model: MusicViewModel,song: Song?) {
    val context=LocalContext.current
    val lines by produceState<List<LyricLine>>(emptyList(),song?.uri){value=song?.let {withContext(Dispatchers.IO){companionLyrics(context,it)}}.orEmpty()}
    if(lines.isEmpty()){Text("No companion LRC lyrics available.",style=MaterialTheme.typography.bodySmall);return}
    val position by musicPosition(model);val selected=activeLyric(lines,position);val state=rememberLazyListState()
    LaunchedEffect(selected){if(selected>=0)state.animateScrollToItem(selected)}
    LazyColumn(Modifier.fillMaxWidth().height(220.dp),state=state,verticalArrangement=Arrangement.spacedBy(12.dp)){itemsIndexed(lines){index,line->Text(line.text,color=if(index==selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.titleMedium)}}
}
@Composable private fun MusicQueueDialog(model: MusicViewModel,close: ()->Unit) {
    val stats by model.stats.collectAsStateWithLifecycle()
    Dialog(close,properties=DialogProperties(usePlatformDefaultWidth=false)) {Surface(Modifier.fillMaxSize()){Column(Modifier.safeDrawingPadding()){
        Row(verticalAlignment=Alignment.CenterVertically){VXIconButton(Icons.AutoMirrored.Filled.ArrowBack,"Back",close);Text("Queue",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);TextButton(onClick=model::clearQueue){Text("Clear")}}
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(12.dp)) {
            itemsIndexed(model.queue,key={i,s->"$i:${s.uri}"}){index,song->
                val dismiss=rememberSwipeToDismissBoxState(confirmValueChange={if(it!=SwipeToDismissBoxValue.Settled){model.remove(index);true}else false})
                SwipeToDismissBox(dismiss,backgroundContent={Box(Modifier.fillMaxSize(),contentAlignment=Alignment.CenterEnd){Icon(Icons.Default.Delete,null)}}){
                    SongRow(song,model.current?.uri==song.uri&&model.playing,stats.any {it.uri==song.uri&&it.favourite},play={model.seekQueue(index)},next={val current=model.controller?.currentMediaItemIndex ?: 0;model.move(index,if(index<current)current else (current+1).coerceAtMost(model.queue.lastIndex))},add={model.add(song,false)},favouriteAction={model.favourite(song)},playlist={},share={},info={},handle={to->model.move(index,to)},index=index,count=model.queue.size,remove={model.remove(index)},queueOnly=true)
                }
            }
            item{Text("Played tracks",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(16.dp))}
            items(stats.filter {it.lastPlayed>0}.sortedByDescending {it.lastPlayed}.take(20),key={"history:${it.uri}"}){stat->model.songs.firstOrNull {it.uri==stat.uri}?.let {song->ListItem(headlineContent={Text(song.title)},supportingContent={Text(song.artist)},modifier=Modifier.clickable {model.play(song)})}}
        }
    }}}
}
