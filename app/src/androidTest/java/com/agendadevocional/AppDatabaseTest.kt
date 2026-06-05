package com.agendadevocional

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agendadevocional.data.AppDatabase
import com.agendadevocional.data.MensagemDao
import com.agendadevocional.model.MensagemDia
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: MensagemDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.mensagemDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun testInsertAndGetMensagem() = runBlocking {
        val msg = MensagemDia(
            data = "03 de Junho de 2026",
            versiculo = "O Senhor é o meu pastor",
            referencia = "Salmo 23:1",
            contexto = "Contexto do Salmo",
            significado = "Significado do Salmo",
            mensagem = "Mensagem do dia"
        )
        
        dao.insertAll(listOf(msg))
        
        val list = dao.getAllMensagensList()
        assertEquals(1, list.size)
        assertEquals("03 de Junho de 2026", list[0].data)
        assertEquals("Salmo 23:1", list[0].referencia)
    }

    @Test
    fun testUpdateFavoriteStatus() = runBlocking {
        val msg = MensagemDia(
            data = "03 de Junho de 2026",
            versiculo = "O Senhor é o meu pastor",
            referencia = "Salmo 23:1",
            contexto = "Contexto do Salmo",
            significado = "Significado do Salmo",
            mensagem = "Mensagem do dia",
            isFavorite = false
        )
        dao.insertAll(listOf(msg))
        
        dao.updateFavoriteStatus("03 de Junho de 2026", true)
        
        val list = dao.getAllMensagensList()
        assertTrue(list[0].isFavorite)
    }

    @Test
    fun testUpdateAnotacao() = runBlocking {
        val msg = MensagemDia(
            data = "03 de Junho de 2026",
            versiculo = "O Senhor é o meu pastor",
            referencia = "Salmo 23:1",
            contexto = "Contexto do Salmo",
            significado = "Significado do Salmo",
            mensagem = "Mensagem do dia"
        )
        dao.insertAll(listOf(msg))
        
        dao.updateAnotacao("03 de Junho de 2026", "Minha anotação")
        
        val list = dao.getAllMensagensList()
        assertEquals("Minha anotação", list[0].anotacao)
    }
}
