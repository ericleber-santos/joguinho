package com.ericleber.joguinho.core

import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Logger interno do jogo. Grava erros em arquivo de log interno do app.
 * O arquivo de log é excluído do backup automático (backup_rules.xml).
 */
object Logger {
    private var arquivoLog: File? = null
    private val formatoData = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    /** Inicializa o logger apontando para o diretório de arquivos internos do app. */
    fun init(filesDir: File) {
        val dirLogs = File(filesDir, "logs")
        dirLogs.mkdirs()
        arquivoLog = File(dirLogs, "erros_jogo.log")
    }

    /**
     * Registra um erro no Logcat e no arquivo de log interno.
     *
     * @param tag     Identificador da origem do erro.
     * @param message Mensagem descritiva do erro.
     * @param throwable Exceção associada, se houver.
     */
    fun error(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        try {
            val arquivo = arquivoLog ?: return
            FileWriter(arquivo, true).use { escritor ->
                val timestamp = formatoData.format(Date())
                escritor.appendLine("[$timestamp] ERRO/$tag: $message")
                throwable?.let { escritor.appendLine(it.stackTraceToString()) }
            }
        } catch (e: Exception) {
            Log.e("Logger", "Falha ao gravar log em arquivo", e)
        }
    }
}
