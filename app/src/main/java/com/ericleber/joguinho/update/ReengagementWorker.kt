package com.ericleber.joguinho.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ericleber.joguinho.ui.MainMenuActivity

/**
 * Worker de reengajamento — envia notificação local convidando o jogador a continuar explorando.
 * Agendado com 48h de atraso via WorkManager, respeitando o Doze Mode do Android.
 * Requisitos: 10.8, 18.3
 */
class ReengagementWorker(
    private val contexto: Context,
    params: WorkerParameters
) : CoroutineWorker(contexto, params) {

    companion object {
        const val NOME_TRABALHO = "reengajamento_caverna_spike"
        private const val ID_CANAL = "canal_reengajamento"
        private const val ID_NOTIFICACAO = 1001

        private val MENSAGENS = listOf(
            "A caverna ainda guarda segredos... Spike está te esperando!",
            "Você parou no meio da exploração. Continue de onde parou!",
            "Spike está farejando seu rastro. Volte à caverna!",
            "Novos andares aguardam sua coragem. A aventura continua!",
            "A caverna não para de crescer. Até onde você consegue chegar?"
        )
    }

    override suspend fun doWork(): Result {
        criarCanalNotificacao()
        enviarNotificacao()
        return Result.success()
    }

    private fun criarCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                ID_CANAL,
                "Reengajamento",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificações para convidar o jogador a retornar ao jogo"
            }
            val gerenciador = contexto.getSystemService(NotificationManager::class.java)
            gerenciador?.createNotificationChannel(canal)
        }
    }

    private fun enviarNotificacao() {
        val intencao = Intent(contexto, MainMenuActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val intencaoPendente = PendingIntent.getActivity(
            contexto,
            0,
            intencao,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mensagem = MENSAGENS.random()

        val notificacao = NotificationCompat.Builder(contexto, ID_CANAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Spike na Caverna")
            .setContentText(mensagem)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensagem))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(intencaoPendente)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(contexto).notify(ID_NOTIFICACAO, notificacao)
        } catch (_: SecurityException) {
            // Permissão de notificação não concedida — ignora silenciosamente
        }
    }
}
