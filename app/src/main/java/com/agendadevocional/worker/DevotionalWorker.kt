package com.agendadevocional.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.agendadevocional.MainActivity
import com.agendadevocional.data.MensagensRepository
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DevotionalWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val database = com.agendadevocional.data.AppDatabase.getDatabase(applicationContext)
        val repository = MensagensRepository(applicationContext, database.mensagemDao())
        val mensagens = repository.carregarMensagens()
        val hojeIdx = repository.indiceHoje(mensagens)
        
        if (hojeIdx >= 0 && hojeIdx < mensagens.size) {
            val hoje = mensagens[hojeIdx]
            showNotification(hoje.versiculo, hoje.referencia)
        }

        return Result.success()
    }

    private fun showNotification(versiculo: String, referencia: String) {
        val prefs = applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val lang = prefs.getString("selected_language", "pt") ?: "pt"
        
        com.agendadevocional.LocaleManager.applicationContext = applicationContext

        val channelName = com.agendadevocional.LocaleManager.getLocalizedString(lang, "notif_channel_name")
        val channelDesc = com.agendadevocional.LocaleManager.getLocalizedString(lang, "notif_channel_desc")
        val contentTitle = com.agendadevocional.LocaleManager.getLocalizedString(lang, "notif_title")

        val channelId = "devocional_diario"
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = channelDesc
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Sugestão: trocar por ícone do app
            .setContentTitle(contentTitle)
            .setContentText("\"$versiculo\" ($referencia)")
            .setStyle(NotificationCompat.BigTextStyle().bigText("\"$versiculo\"\n\n$referencia"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)
    }

    companion object {
        fun scheduleDailyNotification(context: Context, hour: Int = 7, minute: Int = 0) {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DATE, 1)
                }
            }

            val delay = calendar.timeInMillis - System.currentTimeMillis()

            val request = PeriodicWorkRequestBuilder<DevotionalWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag("daily_devotional")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "daily_devotional",
                ExistingPeriodicWorkPolicy.REPLACE, // Usar REPLACE para atualizar o horário
                request
            )
        }

        fun cancelNotifications(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("daily_devotional")
        }
    }
}
