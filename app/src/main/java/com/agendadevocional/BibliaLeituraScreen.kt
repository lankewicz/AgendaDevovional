package com.agendadevocional

import android.content.Context
import android.os.StatFs
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agendadevocional.data.AudioDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibliaLeituraScreen(
    selectedLanguage: String,
    audioPlayer: AndroidAudioPlayer,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    val downloadManager = remember { AudioDownloadManager(context) }

    // Sub-tab selection state (0 for Hoje, 1 for Plano Completo)
    var selectedSubTab by remember { mutableStateOf(0) }
    var expandedDay by remember { mutableStateOf<String?>(null) }

    // Load available voices list dynamically based on language
    val voiceOptions = remember(selectedLanguage) {
        when (selectedLanguage) {
            "en" -> listOf(
                "en-US-AriaNeural" to "Aria (US)",
                "en-US-BrianNeural" to "Brian (US)",
                "en-US-JennyNeural" to "Jenny (US)"
            )
            "es" -> listOf(
                "es-ES-AlvaroNeural" to "Alvaro (España)",
                "es-ES-ElviraNeural" to "Elvira (España)",
                "es-MX-DaliaNeural" to "Dalia (México)",
                "es-MX-JorgeNeural" to "Jorge (México)"
            )
            else -> listOf(
                "pt-BR-FranciscaNeural" to "Francisca (Brasil)",
                "pt-BR-AntonioNeural" to "Antonio (Brasil)",
                "pt-BR-ThalitaNeural" to "Thalita (Brasil)",
                "pt-PT-DuarteNeural" to "Duarte (Portugal)"
            )
        }
    }

    var selectedVoice by remember {
        mutableStateOf(
            prefs.getString("biblia_selected_voice_$selectedLanguage", voiceOptions.first().first) ?: voiceOptions.first().first
        )
    }

    var downloadScope by remember {
        mutableStateOf(
            prefs.getString("biblia_download_scope", "dia") ?: "dia"
        )
    }

    // JSON Data states
    var manifestJson by remember { mutableStateOf<JSONObject?>(null) }
    var planoLeituraJson by remember { mutableStateOf<JSONObject?>(null) }
    var isLoadingData by remember { mutableStateOf(true) }

    // Audio Playback State
    var currentPlayingPath by remember { mutableStateOf<String?>(null) }
    var currentPlayingProgress by remember { mutableStateOf(0f) }
    var isAudioPlaying by remember { mutableStateOf(false) }

    // Load JSONs from Assets
    LaunchedEffect(selectedLanguage) {
        withContext(Dispatchers.IO) {
            try {
                val manifestStr = context.assets.open("audio_manifest.json").use { it.bufferedReader().readText() }
                val planoStr = context.assets.open("plano_leitura.json").use { it.bufferedReader().readText() }
                manifestJson = JSONObject(manifestStr)
                planoLeituraJson = JSONObject(planoStr)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingData = false
            }
        }
    }

    // Save configurations
    LaunchedEffect(selectedVoice) {
        prefs.edit().putString("biblia_selected_voice_$selectedLanguage", selectedVoice).apply()
    }
    LaunchedEffect(downloadScope) {
        prefs.edit().putString("biblia_download_scope", downloadScope).apply()
    }

    if (isLoadingData) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val cal = java.util.Calendar.getInstance()
    val dayOfYear = cal.get(java.util.Calendar.DAY_OF_YEAR)
    val todayKey = dayOfYear.toString()

    // Helper to extract reading plan details for any day of the year
    fun getReadingPlanForDay(dayKey: String): TodayReadingPlan? {
        return planoLeituraJson?.optJSONObject(dayKey)?.let { obj ->
            val refObj = obj.optJSONObject("leitura_referencia")
            val refText = refObj?.optString(selectedLanguage) ?: refObj?.optString("pt") ?: ""
            val leituraArray = obj.optJSONArray("leitura")
            val chapters = mutableListOf<BibleChapter>()
            if (leituraArray != null) {
                for (i in 0 until leituraArray.length()) {
                    val chapObj = leituraArray.getJSONObject(i)
                    val bookId = chapObj.getString("book_id")
                    val start = chapObj.getInt("chapter_start")
                    val end = chapObj.getInt("chapter_end")
                    for (ch in start..end) {
                        chapters.add(BibleChapter(bookId, ch))
                    }
                }
            }
            TodayReadingPlan(refText, chapters)
        }
    }

    val readingPlanForToday = remember(planoLeituraJson, todayKey, selectedLanguage) {
        getReadingPlanForDay(todayKey)
    }

    // Device storage information
    val freeSpace = remember {
        try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            10L * 1024 * 1024 * 1024 // Fallback 10GB
        }
    }

    // Size calculation helpers
    fun getChapterSize(bookId: String, chapter: Int): Long {
        val manifest = manifestJson ?: return 200 * 1024L
        val biblia = manifest.optJSONObject("biblia") ?: return 200 * 1024L
        val book = biblia.optJSONObject(bookId) ?: return 200 * 1024L
        val langObj = book.optJSONObject(selectedLanguage) ?: return 200 * 1024L
        val voiceObj = langObj.optJSONObject(selectedVoice) ?: return 200 * 1024L
        val chapterObj = voiceObj.optJSONObject(chapter.toString()) ?: return 200 * 1024L
        return chapterObj.optLong("size", 200 * 1024L)
    }

    fun getChapterPath(bookId: String, chapter: Int): String? {
        val manifest = manifestJson ?: return null
        val biblia = manifest.optJSONObject("biblia") ?: return null
        val book = biblia.optJSONObject(bookId) ?: return null
        val langObj = book.optJSONObject(selectedLanguage) ?: return null
        val voiceObj = langObj.optJSONObject(selectedVoice) ?: return null
        val chapterObj = voiceObj.optJSONObject(chapter.toString()) ?: return null
        return if (chapterObj.has("path")) chapterObj.getString("path") else null
    }

    val estimatedSizes = remember(manifestJson, selectedLanguage, selectedVoice, readingPlanForToday) {
        val daySize = readingPlanForToday?.chapters?.sumOf { getChapterSize(it.bookId, it.chapter) } ?: 0L
        val weekSize = daySize * 7
        val monthSize = daySize * 30
        val yearSize = daySize * 365
        mapOf("dia" to daySize, "semana" to weekSize, "mes" to monthSize, "ano" to yearSize)
    }

    val selectedPlanSize = estimatedSizes[downloadScope] ?: 0L

    // Auto-download today's reading if scope is 'dia'
    LaunchedEffect(readingPlanForToday, selectedVoice, downloadScope) {
        if (downloadScope == "dia" && readingPlanForToday != null) {
            readingPlanForToday.chapters.forEach { chapter ->
                val path = getChapterPath(chapter.bookId, chapter.chapter)
                if (path != null && !downloadManager.isDownloaded(path)) {
                    downloadManager.downloadAudio(path)
                }
            }
        }
    }

    // Main layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App bar
        TopAppBar(
            title = {
                Text(
                    text = getLocalizedString(selectedLanguage, "biblia_titulo"),
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = getLocalizedString(selectedLanguage, "desc_voltar"))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        // Sub Tab selector (Hoje vs Plano Completo)
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text(getLocalizedString(selectedLanguage, "hoje"), fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text(getLocalizedString(selectedLanguage, "plano_completo"), fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (selectedSubTab == 0) {
            // Today's reading, settings, and storage Canvas
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Configuration selectors
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Voice Selector
                            Column {
                                Text(
                                    text = getLocalizedString(selectedLanguage, "seletor_voz"),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    voiceOptions.forEach { (voiceId, name) ->
                                        val isSelected = selectedVoice == voiceId
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { selectedVoice = voiceId },
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            border = if (isSelected) null else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                        ) {
                                            Text(
                                                text = name.split(" ").first(),
                                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                                textAlign = TextAlign.Center,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            // Scope Selector
                            Column {
                                Text(
                                    text = getLocalizedString(selectedLanguage, "seletor_escopo"),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        "dia" to "escopo_dia",
                                        "semana" to "escopo_semana",
                                        "mes" to "escopo_mes",
                                        "ano" to "escopo_ano"
                                    ).forEach { (scopeId, stringKey) ->
                                        val isSelected = downloadScope == scopeId
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { downloadScope = scopeId },
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            border = if (isSelected) null else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                        ) {
                                            Text(
                                                text = getLocalizedString(selectedLanguage, stringKey),
                                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                                textAlign = TextAlign.Center,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Beautiful Canvas Storage Chart
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = getLocalizedString(selectedLanguage, "grafico_consumo"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            // Animating the progress ratio
                            val ratio = (selectedPlanSize.toFloat() / freeSpace.toFloat()).coerceIn(0.001f, 1f)
                            val animatedRatio by animateFloatAsState(
                                targetValue = ratio,
                                animationSpec = tween(durationMillis = 800)
                            )

                            val planSizeFormatted = formatBytes(selectedPlanSize)
                            val freeSpaceFormatted = formatBytes(freeSpace)

                            val arcColor = MaterialTheme.colorScheme.primary
                            val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(150.dp)
                            ) {
                                Canvas(modifier = Modifier.size(140.dp)) {
                                    drawArc(
                                        color = trackColor,
                                        startAngle = -220f,
                                        sweepAngle = 260f,
                                        useCenter = false,
                                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                    drawArc(
                                        brush = Brush.sweepGradient(
                                            colors = listOf(arcColor.copy(alpha = 0.5f), arcColor)
                                        ),
                                        startAngle = -220f,
                                        sweepAngle = animatedRatio * 260f,
                                        useCenter = false,
                                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = planSizeFormatted,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = getLocalizedString(selectedLanguage, "espaco_ocupado"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${getLocalizedString(selectedLanguage, "espaco_total")}: $freeSpaceFormatted",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // Today's reading list
                item {
                    Text(
                        text = getLocalizedString(selectedLanguage, "leitura_diaria"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (readingPlanForToday == null || readingPlanForToday.chapters.isEmpty()) {
                    item {
                        Text(
                            text = getLocalizedString(selectedLanguage, "sem_leitura_hoje"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                        )
                    }
                } else {
                    item {
                        Text(
                            text = readingPlanForToday.referenceText,
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    items(readingPlanForToday.chapters) { chapter ->
                        val path = getChapterPath(chapter.bookId, chapter.chapter)
                        val bookName = translateBookId(chapter.bookId, selectedLanguage)
                        val title = "$bookName ${chapter.chapter}"

                        val isDownloaded = path?.let { downloadManager.isDownloaded(it) } ?: false
                        var isDownloading by remember { mutableStateOf(false) }

                        var playbackProgress by remember { mutableStateOf(0f) }
                        val isPlayingThis = currentPlayingPath != null && currentPlayingPath == path && isAudioPlaying

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(16.dp)
                                ),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (path == null) return@IconButton
                                            if (isPlayingThis) {
                                                audioPlayer.pause()
                                                isAudioPlaying = false
                                            } else if (currentPlayingPath == path) {
                                                audioPlayer.resume { currentMs, totalMs ->
                                                    playbackProgress = if (totalMs > 0) currentMs.toFloat() / totalMs.toFloat() else 0f
                                                }
                                                isAudioPlaying = true
                                            } else {
                                                val playBlock = { fileOrUrl: String, isLocal: Boolean ->
                                                    currentPlayingPath = path
                                                    isAudioPlaying = true
                                                    val progressCallback = { currentMs: Int, totalMs: Int ->
                                                        if (currentPlayingPath == path) {
                                                            playbackProgress = if (totalMs > 0) currentMs.toFloat() / totalMs.toFloat() else 0f
                                                        }
                                                    }
                                                    val completionCallback = {
                                                        if (currentPlayingPath == path) {
                                                            isAudioPlaying = false
                                                            currentPlayingPath = null
                                                            playbackProgress = 0f
                                                        }
                                                    }
                                                    if (isLocal) {
                                                        audioPlayer.playFile(File(fileOrUrl), progressCallback, completionCallback)
                                                    } else {
                                                        audioPlayer.playUrl(fileOrUrl, progressCallback, completionCallback)
                                                    }
                                                }

                                                if (isDownloaded) {
                                                    val localFile = downloadManager.getLocalFile(path)
                                                    playBlock(localFile.absolutePath, true)
                                                } else {
                                                    val url = "${downloadManager.r2BaseUrl}$path"
                                                    playBlock(url, false)
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (isDownloaded) getLocalizedString(selectedLanguage, "baixado") else getLocalizedString(selectedLanguage, "nuvem"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }

                                // Download/delete action
                                if (!isDownloaded && path != null) {
                                    if (isDownloading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        IconButton(
                                            onClick = {
                                                isDownloading = true
                                                coroutineScope.launch {
                                                    val file = downloadManager.downloadAudio(path)
                                                    isDownloading = false
                                                    if (file == null) {
                                                        Toast.makeText(context, getLocalizedString(selectedLanguage, "erro_download_audio"), Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                } else if (isDownloaded && path != null) {
                                    IconButton(
                                        onClick = {
                                            downloadManager.deleteAudio(path)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Full 365 Days Plan View
            val daysList = remember { (1..365).map { it.toString() } }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(daysList) { dayKey ->
                    val plan = remember(planoLeituraJson, dayKey, selectedLanguage) {
                        getReadingPlanForDay(dayKey)
                    }
                    val isExpanded = expandedDay == dayKey

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedDay = if (isExpanded) null else dayKey
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isExpanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isExpanded) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) else null
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = dayKey,
                                            color = if (isExpanded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = plan?.referenceText ?: "",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isExpanded && plan != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                plan.chapters.forEach { chapter ->
                                    val path = getChapterPath(chapter.bookId, chapter.chapter)
                                    val bookName = translateBookId(chapter.bookId, selectedLanguage)
                                    val title = "$bookName ${chapter.chapter}"

                                    val isDownloaded = path?.let { downloadManager.isDownloaded(it) } ?: false
                                    var isDownloading by remember { mutableStateOf(false) }
                                    var playbackProgress by remember { mutableStateOf(0f) }
                                    val isPlayingThis = currentPlayingPath != null && currentPlayingPath == path && isAudioPlaying

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                            .background(
                                                MaterialTheme.colorScheme.surface,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    if (path == null) return@IconButton
                                                    if (isPlayingThis) {
                                                        audioPlayer.pause()
                                                        isAudioPlaying = false
                                                    } else if (currentPlayingPath == path) {
                                                        audioPlayer.resume { currentMs, totalMs ->
                                                            playbackProgress = if (totalMs > 0) currentMs.toFloat() / totalMs.toFloat() else 0f
                                                        }
                                                        isAudioPlaying = true
                                                    } else {
                                                        val playBlock = { fileOrUrl: String, isLocal: Boolean ->
                                                            currentPlayingPath = path
                                                            isAudioPlaying = true
                                                            val progressCallback = { currentMs: Int, totalMs: Int ->
                                                                if (currentPlayingPath == path) {
                                                                    playbackProgress = if (totalMs > 0) currentMs.toFloat() / totalMs.toFloat() else 0f
                                                                }
                                                            }
                                                            val completionCallback = {
                                                                if (currentPlayingPath == path) {
                                                                    isAudioPlaying = false
                                                                    currentPlayingPath = null
                                                                    playbackProgress = 0f
                                                                }
                                                            }
                                                            if (isLocal) {
                                                                audioPlayer.playFile(File(fileOrUrl), progressCallback, completionCallback)
                                                            } else {
                                                                audioPlayer.playUrl(fileOrUrl, progressCallback, completionCallback)
                                                            }
                                                        }

                                                        if (isDownloaded) {
                                                            val localFile = downloadManager.getLocalFile(path)
                                                            playBlock(localFile.absolutePath, true)
                                                        } else {
                                                            val url = "${downloadManager.r2BaseUrl}$path"
                                                            playBlock(url, false)
                                                        }
                                                    }
                                                },
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column {
                                                Text(
                                                    text = title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = if (isDownloaded) getLocalizedString(selectedLanguage, "baixado") else getLocalizedString(selectedLanguage, "nuvem"),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                )
                                            }
                                        }

                                        // Download/delete action
                                        if (!isDownloaded && path != null) {
                                            if (isDownloading) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                IconButton(
                                                    onClick = {
                                                        isDownloading = true
                                                        coroutineScope.launch {
                                                            val file = downloadManager.downloadAudio(path)
                                                            isDownloading = false
                                                            if (file == null) {
                                                                Toast.makeText(context, getLocalizedString(selectedLanguage, "erro_download_audio"), Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Download,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        } else if (isDownloaded && path != null) {
                                            IconButton(
                                                onClick = {
                                                    downloadManager.deleteAudio(path)
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class BibleChapter(val bookId: String, val chapter: Int)
data class TodayReadingPlan(val referenceText: String, val chapters: List<BibleChapter>)

fun translateBookId(bookId: String, lang: String): String {
    val maps = mapOf(
        "GEN" to mapOf("pt" to "Gênesis", "en" to "Genesis", "es" to "Génesis"),
        "EXO" to mapOf("pt" to "Êxodo", "en" to "Exodus", "es" to "Éxodo"),
        "LEV" to mapOf("pt" to "Levítico", "en" to "Leviticus", "es" to "Levítico"),
        "NUM" to mapOf("pt" to "Números", "en" to "Numbers", "es" to "Números"),
        "DEU" to mapOf("pt" to "Deuteronômio", "en" to "Deuteronomy", "es" to "Deuteronomio"),
        "JOS" to mapOf("pt" to "Josué", "en" to "Joshua", "es" to "Josué"),
        "JDG" to mapOf("pt" to "Juízes", "en" to "Judges", "es" to "Jueces"),
        "RUT" to mapOf("pt" to "Rute", "en" to "Ruth", "es" to "Rut"),
        "1SA" to mapOf("pt" to "1 Samuel", "en" to "1 Samuel", "es" to "1 Samuel"),
        "2SA" to mapOf("pt" to "2 Samuel", "en" to "2 Samuel", "es" to "2 Samuel"),
        "1KI" to mapOf("pt" to "1 Reis", "en" to "1 Kings", "es" to "1 Reyes"),
        "2KI" to mapOf("pt" to "2 Reis", "en" to "2 Kings", "es" to "2 Reyes"),
        "1CH" to mapOf("pt" to "1 Crônicas", "en" to "1 Chronicles", "es" to "1 Crónicas"),
        "2CH" to mapOf("pt" to "2 Crônicas", "en" to "2 Chronicles", "es" to "2 Crónicas"),
        "EZR" to mapOf("pt" to "Esdras", "en" to "Ezra", "es" to "Esdras"),
        "NEH" to mapOf("pt" to "Neemias", "en" to "Nehemiah", "es" to "Nehemías"),
        "EST" to mapOf("pt" to "Ester", "en" to "Esther", "es" to "Ester"),
        "JOB" to mapOf("pt" to "Jó", "en" to "Job", "es" to "Job"),
        "PSA" to mapOf("pt" to "Salmos", "en" to "Psalms", "es" to "Salmos"),
        "PRO" to mapOf("pt" to "Provérbios", "en" to "Proverbs", "es" to "Proverbios"),
        "ECC" to mapOf("pt" to "Eclesiastes", "en" to "Ecclesiastes", "es" to "Eclesiastes"),
        "SNG" to mapOf("pt" to "Cânticos", "en" to "Song of Solomon", "es" to "Cantares"),
        "ISA" to mapOf("pt" to "Isaías", "en" to "Isaiah", "es" to "Isaías"),
        "JER" to mapOf("pt" to "Jeremias", "en" to "Jeremiah", "es" to "Jeremías"),
        "LAM" to mapOf("pt" to "Lamentações", "en" to "Lamentations", "es" to "Lamentaciones"),
        "EZK" to mapOf("pt" to "Ezequiel", "en" to "Ezekiel", "es" to "Ezequiel"),
        "DAN" to mapOf("pt" to "Daniel", "en" to "Daniel", "es" to "Daniel"),
        "HOS" to mapOf("pt" to "Oseias", "en" to "Hosea", "es" to "Oseas"),
        "JOL" to mapOf("pt" to "Joel", "en" to "Joel", "es" to "Joel"),
        "AMO" to mapOf("pt" to "Amós", "en" to "Amos", "es" to "Amós"),
        "OBA" to mapOf("pt" to "Obadias", "en" to "Obadiah", "es" to "Abdías"),
        "JON" to mapOf("pt" to "Jonas", "en" to "Jonah", "es" to "Jonás"),
        "MIC" to mapOf("pt" to "Miqueias", "en" to "Micah", "es" to "Miqueas"),
        "NAM" to mapOf("pt" to "Naum", "en" to "Nahum", "es" to "Nahúm"),
        "HAB" to mapOf("pt" to "Habacuque", "en" to "Habakkuk", "es" to "Habacuc"),
        "ZEP" to mapOf("pt" to "Sofonias", "en" to "Zephaniah", "es" to "Sofonías"),
        "HAG" to mapOf("pt" to "Ageu", "en" to "Haggai", "es" to "Hageo"),
        "ZEC" to mapOf("pt" to "Zacarias", "en" to "Zechariah", "es" to "Zacarías"),
        "MAL" to mapOf("pt" to "Malaquias", "en" to "Malachi", "es" to "Malaquías"),
        "MAT" to mapOf("pt" to "Mateus", "en" to "Matthew", "es" to "Mateo"),
        "MRK" to mapOf("pt" to "Marcos", "en" to "Mark", "es" to "Marcos"),
        "LUK" to mapOf("pt" to "Lucas", "en" to "Luke", "es" to "Lucas"),
        "JHN" to mapOf("pt" to "João", "en" to "John", "es" to "Juan"),
        "ACT" to mapOf("pt" to "Atos", "en" to "Acts", "es" to "Hechos"),
        "ROM" to mapOf("pt" to "Romanos", "en" to "Romans", "es" to "Romanos"),
        "1COR" to mapOf("pt" to "1 Coríntios", "en" to "1 Corinthians", "es" to "1 Corintios"),
        "2COR" to mapOf("pt" to "2 Coríntios", "en" to "2 Corinthians", "es" to "2 Corintios"),
        "GAL" to mapOf("pt" to "Gálatas", "en" to "Galatians", "es" to "Gálatas"),
        "EPH" to mapOf("pt" to "Efésios", "en" to "Ephesians", "es" to "Efesios"),
        "PHP" to mapOf("pt" to "Filipenses", "en" to "Philippians", "es" to "Filipenses"),
        "COL" to mapOf("pt" to "Colossenses", "en" to "Colossians", "es" to "Colosenses"),
        "1TH" to mapOf("pt" to "1 Tessalonicenses", "en" to "1 Thessalonians", "es" to "1 Tesalonicenses"),
        "2TH" to mapOf("pt" to "2 Tessalonicenses", "en" to "2 Thessalonians", "es" to "2 Tesalonicenses"),
        "1TI" to mapOf("pt" to "1 Timóteo", "en" to "1 Timothy", "es" to "1 Timoteo"),
        "2TI" to mapOf("pt" to "2 Timóteo", "en" to "2 Timothy", "es" to "2 Timoteo"),
        "TIT" to mapOf("pt" to "Tito", "en" to "Titus", "es" to "Tito"),
        "PHM" to mapOf("pt" to "Filemom", "en" to "Philemon", "es" to "Filemón"),
        "HEB" to mapOf("pt" to "Hebreus", "en" to "Hebrews", "es" to "Hebreos"),
        "JAS" to mapOf("pt" to "Tiago", "en" to "James", "es" to "Santiago"),
        "1PE" to mapOf("pt" to "1 Pedro", "en" to "1 Peter", "es" to "1 Pedro"),
        "2PE" to mapOf("pt" to "2 Pedro", "en" to "2 Peter", "es" to "2 Pedro"),
        "1JN" to mapOf("pt" to "1 João", "en" to "1 John", "es" to "1 Juan"),
        "2JN" to mapOf("pt" to "2 João", "en" to "2 John", "es" to "2 Juan"),
        "3JN" to mapOf("pt" to "3 João", "en" to "3 John", "es" to "3 Juan"),
        "JUD" to mapOf("pt" to "Judas", "en" to "Jude", "es" to "Judas"),
        "REV" to mapOf("pt" to "Apocalipse", "en" to "Revelation", "es" to "Apocalipsis")
    )
    return maps[bookId]?.get(lang) ?: bookId
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}
