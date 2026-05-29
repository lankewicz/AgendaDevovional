package com.agendadevocional

import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

data class AssiduidadeStats(
    val anoAtual: Int,
    val mesAtual: Int,
    val streak: Int
)

fun getAssiduidadeStats(context: Context): AssiduidadeStats {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    val readDates = prefs.getStringSet("read_dates", emptySet()) ?: emptySet()
    
    val today = LocalDate.now()
    val currentYear = today.year
    val currentMonth = today.monthValue
    
    var countYear = 0
    var countMonth = 0
    
    val parsedDates = mutableListOf<LocalDate>()
    
    for (dateStr in readDates) {
        try {
            val date = LocalDate.parse(dateStr)
            parsedDates.add(date)
            if (date.year == currentYear) {
                countYear++
                if (date.monthValue == currentMonth) {
                    countMonth++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    parsedDates.sort()
    
    var streak = 0
    if (parsedDates.isNotEmpty()) {
        val todayStr = today.toString()
        val yesterdayStr = today.minusDays(1).toString()
        
        val hasToday = readDates.contains(todayStr)
        val hasYesterday = readDates.contains(yesterdayStr)
        
        if (hasToday || hasYesterday) {
            var currentCheck = if (hasToday) today else today.minusDays(1)
            while (readDates.contains(currentCheck.toString())) {
                streak++
                currentCheck = currentCheck.minusDays(1)
            }
        }
    }
    
    return AssiduidadeStats(
        anoAtual = countYear,
        mesAtual = countMonth,
        streak = streak
    )
}

@Composable
fun AssiduidadeDashboard(stats: AssiduidadeStats, selectedLanguage: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = getLocalizedString(selectedLanguage, "progresso").uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = "${stats.streak}",
                    label = getLocalizedString(selectedLanguage, "dias_seguidos"),
                    color = Color(0xFFFF8C00),
                    icon = "🔥"
                )
                
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = "${stats.mesAtual}",
                    label = getLocalizedString(selectedLanguage, "este_mes"),
                    color = Color(0xFF1E90FF),
                    icon = "📅"
                )
                
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = "${stats.anoAtual}",
                    label = getLocalizedString(selectedLanguage, "este_ano"),
                    color = Color(0xFF9370DB),
                    icon = "✨"
                )
            }
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    color: Color,
    icon: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                lineHeight = 12.sp
            )
        }
    }
}
