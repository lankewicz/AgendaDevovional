package com.agendadevocional.backup

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import java.io.ByteArrayOutputStream

class GoogleDriveBackupHelper(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    fun getSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    fun isUserSignedIn(): Boolean {
        val account = getSignedInAccount()
        return account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_APPDATA))
    }

    private fun getDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_APPDATA)
        ).setSelectedAccount(account.account)

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("AgendaDevocional").build()
    }

    fun uploadBackup(
        jsonContent: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val account = getSignedInAccount()
        if (account == null) {
            onFailure(Exception("Usuário não conectado"))
            return
        }

        Thread {
            try {
                val drive = getDriveService(account)

                // Buscar arquivo existente na pasta oculta appDataFolder
                val result = drive.files().list()
                    .setSpaces("appDataFolder")
                    .setQ("name = 'agenda_backup.json'")
                    .setFields("files(id, name)")
                    .execute()

                val files = result.files
                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = "agenda_backup.json"
                    parents = listOf("appDataFolder")
                }

                val mediaContent = ByteArrayContent("application/json", jsonContent.toByteArray(Charsets.UTF_8))

                if (files != null && files.isNotEmpty()) {
                    // Atualizar arquivo existente
                    val existingFileId = files[0].id
                    drive.files().update(existingFileId, null, mediaContent).execute()
                } else {
                    // Criar novo arquivo
                    drive.files().create(fileMetadata, mediaContent).execute()
                }

                mainHandler.post { onSuccess() }
            } catch (e: Exception) {
                e.printStackTrace()
                mainHandler.post { onFailure(e) }
            }
        }.start()
    }

    fun downloadBackup(
        onSuccess: (String?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val account = getSignedInAccount()
        if (account == null) {
            onFailure(Exception("Usuário não conectado"))
            return
        }

        Thread {
            try {
                val drive = getDriveService(account)

                // Buscar arquivo existente
                val result = drive.files().list()
                    .setSpaces("appDataFolder")
                    .setQ("name = 'agenda_backup.json'")
                    .setFields("files(id, name)")
                    .execute()

                val files = result.files
                if (files == null || files.isEmpty()) {
                    mainHandler.post { onSuccess(null) }
                    return@Thread
                }

                val existingFileId = files[0].id
                val outputStream = java.io.ByteArrayOutputStream()
                drive.files().get(existingFileId).executeMediaAndDownloadTo(outputStream)
                val jsonContent = outputStream.toString("UTF-8")

                mainHandler.post { onSuccess(jsonContent) }
            } catch (e: Exception) {
                e.printStackTrace()
                mainHandler.post { onFailure(e) }
            }
        }.start()
    }
}
