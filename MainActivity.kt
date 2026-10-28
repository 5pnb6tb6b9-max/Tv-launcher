package com.example.tvlauncher

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ===== PAS DIT AAN =====
const val LAT = 52.37   // breedtegraad van jouw stad (nu Amsterdam)
const val LON = 4.90    // lengtegraad
// =======================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LauncherScreen() }
    }

    // Terugknop doet niets op het startscherm
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {}
}

data class AppItem(val label: String, val pkg: String, val icon: Drawable, val intent: Intent)

fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    fun query(cat: String) = pm.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(cat), 0
    )
    val list = query(Intent.CATEGORY_LEANBACK_LAUNCHER).ifEmpty { query(Intent.CATEGORY_LAUNCHER) }
    return list
        .filter { it.activityInfo.packageName != ctx.packageName }
        .distinctBy { it.activityInfo.packageName }
        .map {
            AppItem(
                label = it.loadLabel(pm).toString(),
                pkg = it.activityInfo.packageName,
                icon = it.loadIcon(pm),
                intent = pm.getLeanbackLaunchIntentForPackage(it.activityInfo.packageName)
                    ?: pm.getLaunchIntentForPackage(it.activityInfo.packageName)!!
            )
        }
        .sortedBy { it.label.lowercase() }
}

fun weatherEmoji(code: Int) = when (code) {
    0 -> "☀️"; 1, 2 -> "🌤️"; 3 -> "☁️"; 45, 48 -> "🌫️"
    in 51..67 -> "🌧️"; in 71..77 -> "❄️"; in 80..82 -> "🌦️"
    in 95..99 -> "⛈️"; else -> "🌡️"
}

suspend fun fetchWeather(): String? = withContext(Dispatchers.IO) {
    try {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$LAT&longitude=$LON" +
            "&current=temperature_2m,weather_code"
        val cur = JSONObject(URL(url).readText()).getJSONObject("current")
        "${weatherEmoji(cur.getInt("weather_code"))} ${Math.round(cur.getDouble("temperature_2m"))}°"
    } catch (e: Exception) { null }
}

@Composable
fun LauncherScreen() {
    val ctx = LocalContext.current
    val apps by produceState(emptyList<AppItem>()) { value = withContext(Dispatchers.IO) { loadApps(ctx) } }

    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }

    var weather by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { while (true) { weather = fetchWeather(); delay(15 * 60_000L) } }

    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale("nl", "NL")) }
    val dateFmt = remember { SimpleDateFormat("EEEE d MMMM", Locale("nl", "NL")) }

    Box(Modifier.fillMaxSize()) {
        // Achtergrond: eigen wallpaper als die bestaat, anders een gradient
        val wpId = remember { ctx.resources.getIdentifier("wallpaper", "drawable", ctx.packageName) }
        if (wpId != 0) {
            Image(
                bitmap = ImageBitmap_from(ctx, wpId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)))
            ))
        }
        // Donkere overlay voor leesbaarheid
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0x66000000), Color(0x22000000), Color(0xCC000000)))
        ))

        // Klok, datum en weer
        Row(
            Modifier.align(Alignment.TopStart).padding(start = 64.dp, top = 48.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text2(timeFmt.format(now), 96, FontWeight.Light)
                Text2(dateFmt.format(now).replaceFirstChar { it.uppercase() }, 24, FontWeight.Normal, 0.8f)
            }
            weather?.let {
                Spacer(Modifier.width(40.dp))
                Text2(it, 48, FontWeight.Normal, modifier = Modifier.padding(bottom = 8.dp))
            }
        }

        // Rij met apps
        val first = remember { FocusRequester() }
        LaunchedEffect(apps.isNotEmpty()) { if (apps.isNotEmpty()) first.requestFocus() }
        LazyRow(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom = 48.dp),
            contentPadding = PaddingValues(horizontal = 64.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            itemsIndexed(apps, key = { _, a -> a.pkg }) { i, app ->
                AppIcon(app, if (i == 0) Modifier.focusRequester(first) else Modifier) {
                    ctx.startActivity(app.intent)
                }
            }
        }
    }
}

@Composable
fun ImageBitmap_from(ctx: Context, id: Int) =
    remember(id) { ctx.getDrawable(id)!!.toBitmap(1920, 1080).asImageBitmap() }

@Composable
fun Text2(
    text: String, size: Int, weight: FontWeight, alpha: Float = 1f, modifier: Modifier = Modifier
) = androidx.compose.foundation.text.BasicText(
    text,
    modifier = modifier,
    style = androidx.compose.ui.text.TextStyle(
        color = Color.White.copy(alpha = alpha), fontSize = size.sp, fontWeight = weight
    )
)

@Composable
fun AppIcon(app: AppItem, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.25f else 1f, label = "scale")
    val bmp = remember(app.pkg) { app.icon.toBitmap(256, 256).asImageBitmap() }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(120.dp)) {
        Box(
            modifier
                .size(96.dp)
                .scale(scale)
                .drawBehind {
                    if (focused) drawCircle(
                        Brush.radialGradient(
                            listOf(Color(0xAAFFFFFF), Color.Transparent),
                            center = Offset(size.width / 2, size.height / 2),
                            radius = size.width * 0.9f
                        ),
                        radius = size.width * 0.9f
                    )
                }
                .clip(CircleShape)
                .background(Color(0x33FFFFFF))
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Image(bmp, app.label, Modifier.size(64.dp))
        }
        Spacer(Modifier.height(14.dp))
        androidx.compose.foundation.text.BasicText(
            app.label,
            maxLines = 1,
            style = androidx.compose.ui.text.TextStyle(
                color = Color.White.copy(alpha = if (focused) 1f else 0.6f),
                fontSize = 14.sp, textAlign = TextAlign.Center
            )
        )
    }
}
