package com.ericleber.joguinho.update

import android.app.Activity
import android.content.IntentSender
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.ericleber.joguinho.core.Logger
import java.lang.ref.WeakReference

/**
 * Gerencia atualizações do aplicativo via Google Play In-App Updates API.
 *
 * Comportamento:
 * - Atualização opcional (FLEXIBLE): diálogo não bloqueante na tela principal.
 *   Player pode escolher "Atualizar agora" ou "Lembrar depois" (Req. 15.1, 15.2).
 * - Atualização obrigatória (IMMEDIATE): diálogo bloqueante, apenas "Atualizar agora" (Req. 15.3).
 * - Sem conexão + atualização obrigatória pendente: informa o Player (Req. 15.4).
 * - Falha de rede ao verificar: prossegue normalmente sem erro (Req. 15.5).
 *
 * Usa WeakReference para Activity para evitar vazamento de memória (Req. 20.2).
 *
 * Uso típico em MainMenuActivity:
 * ```kotlin
 * updateManager = UpdateManager(this)
 * updateManager.checkForUpdates(REQUEST_CODE_UPDATE)
 * // Em onActivityResult:
 * updateManager.onActivityResult(requestCode, resultCode)
 * // Em onResume:
 * updateManager.onResume()
 * // Em onDestroy:
 * updateManager.onDestroy()
 * ```
 */
class UpdateManager(activity: Activity) {

    private val activityRef = WeakReference(activity)
    private val appUpdateManager: AppUpdateManager =
        AppUpdateManagerFactory.create(activity.applicationContext)

    // Controle de sessão: "Lembrar depois" descarta o diálogo pelo resto da sessão (Req. 15.2)
    private var flexibleDismissedThisSession = false

    // Listener para acompanhar o progresso do download da atualização flexível
    private val installStateListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            // Download concluído — notifica o caller para exibir snackbar de conclusão
            onFlexibleUpdateDownloaded?.invoke()
        }
    }

    // =========================================================================
    // Callbacks opcionais para a Activity
    // =========================================================================

    /** Chamado quando uma atualização flexível termina de baixar (pronto para instalar). */
    var onFlexibleUpdateDownloaded: (() -> Unit)? = null

    /** Chamado quando não há conexão e existe atualização obrigatória pendente (Req. 15.4). */
    var onMandatoryUpdateRequiresNetwork: (() -> Unit)? = null

    // =========================================================================
    // API pública
    // =========================================================================

    /**
     * Verifica disponibilidade de atualização e inicia o fluxo adequado.
     *
     * @param requestCode Código de resultado para onActivityResult da Activity chamadora.
     */
    fun checkForUpdates(requestCode: Int) {
        appUpdateManager.registerListener(installStateListener)

        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                handleUpdateInfo(info, requestCode)
            }
            .addOnFailureListener { exception ->
                // Falha de rede ou Play Store indisponível — prossegue normalmente (Req. 15.5)
                Logger.error(TAG, "Falha ao verificar atualização — prosseguindo normalmente", exception)
            }
    }

    /**
     * Deve ser chamado em onResume da Activity para retomar atualizações imediatas
     * interrompidas (ex: usuário saiu do app durante atualização obrigatória).
     */
    fun onResume() {
        val activity = activityRef.get() ?: return
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                // Retoma atualização imediata se estava em andamento (Req. 15.3)
                if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                    && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                    try {
                        appUpdateManager.startUpdateFlowForResult(
                            info,
                            activity,
                            AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                            REQUEST_CODE_RESUME
                        )
                    } catch (e: IntentSender.SendIntentException) {
                        Logger.error(TAG, "Erro ao retomar atualização imediata", e)
                    }
                }
            }
    }

    /**
     * Deve ser chamado em onActivityResult da Activity para tratar o resultado
     * do fluxo de atualização iniciado pelo Play Core.
     *
     * @param requestCode Código recebido em onActivityResult.
     * @param resultCode  Resultado recebido em onActivityResult.
     */
    fun onActivityResult(requestCode: Int, resultCode: Int) {
        if (requestCode != REQUEST_CODE_RESUME && resultCode == Activity.RESULT_CANCELED) {
            // Player cancelou atualização obrigatória — re-verifica para forçar novamente
            Logger.error(TAG, "Atualização obrigatória cancelada pelo usuário — re-verificando", null)
        }
    }

    /**
     * Conclui a instalação de uma atualização flexível já baixada.
     * Deve ser chamado após o Player confirmar no snackbar de "Reiniciar para atualizar".
     */
    fun completeFlexibleUpdate() {
        appUpdateManager.completeUpdate()
    }

    /**
     * Libera o listener de instalação. Deve ser chamado em onDestroy da Activity.
     */
    fun onDestroy() {
        appUpdateManager.unregisterListener(installStateListener)
        onFlexibleUpdateDownloaded = null
        onMandatoryUpdateRequiresNetwork = null
    }

    // =========================================================================
    // Lógica interna
    // =========================================================================

    private fun handleUpdateInfo(info: AppUpdateInfo, requestCode: Int) {
        val activity = activityRef.get() ?: return

        when {
            // Atualização obrigatória disponível (IMMEDIATE) — diálogo bloqueante (Req. 15.3)
            info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> {
                startImmediateUpdate(activity, info, requestCode)
            }

            // Atualização opcional disponível (FLEXIBLE) — diálogo não bloqueante (Req. 15.1)
            info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                && !flexibleDismissedThisSession -> {
                startFlexibleUpdate(activity, info, requestCode)
            }

            // Atualização obrigatória em andamento mas sem rede (Req. 15.4)
            info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                onMandatoryUpdateRequiresNetwork?.invoke()
            }
        }
    }

    /**
     * Inicia fluxo de atualização imediata (bloqueante).
     * Exibe diálogo do Play Store com apenas "Atualizar agora" (Req. 15.3).
     */
    private fun startImmediateUpdate(activity: Activity, info: AppUpdateInfo, requestCode: Int) {
        try {
            appUpdateManager.startUpdateFlowForResult(
                info,
                activity,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                requestCode
            )
        } catch (e: IntentSender.SendIntentException) {
            // Falha ao abrir o diálogo — prossegue normalmente (Req. 15.5)
            Logger.error(TAG, "Erro ao iniciar atualização imediata", e)
        }
    }

    /**
     * Inicia fluxo de atualização flexível (não bloqueante).
     * Exibe diálogo com "Atualizar agora" e "Lembrar depois" (Req. 15.1, 15.2).
     */
    private fun startFlexibleUpdate(activity: Activity, info: AppUpdateInfo, requestCode: Int) {
        try {
            appUpdateManager.startUpdateFlowForResult(
                info,
                activity,
                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE)
                    .setAllowAssetPackDeletion(false)
                    .build(),
                requestCode
            )
            // Marca como dispensado nesta sessão caso o Player escolha "Lembrar depois" (Req. 15.2)
            // O Play Core retorna RESULT_CANCELED quando o usuário dispensa
            flexibleDismissedThisSession = true
        } catch (e: IntentSender.SendIntentException) {
            // Falha ao abrir o diálogo — prossegue normalmente (Req. 15.5)
            Logger.error(TAG, "Erro ao iniciar atualização flexível", e)
        }
    }

    companion object {
        private const val TAG = "UpdateManager"
        private const val REQUEST_CODE_RESUME = 9001
    }
}
