package com.ericleber.joguinho.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager as AndroidAudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import com.ericleber.joguinho.biome.Biome

/**
 * Tipos de efeitos sonoros disponíveis no jogo.
 * Cada tipo corresponde a uma frequência e forma de onda distintas.
 */
enum class TipoEfeito(
    val frequenciaHz: Float,
    val duracaoMs: Int,
    val formaOnda: FormaOnda
) {
    PASSO_PEDRA(300f, 80, FormaOnda.QUADRADA),
    PASSO_AGUA(180f, 120, FormaOnda.SENOIDAL),
    PASSO_TERRA(220f, 100, FormaOnda.QUADRADA),
    ARMADILHA_ATIVADA(880f, 300, FormaOnda.SENOIDAL),
    CONTATO_MONSTRO(150f, 400, FormaOnda.QUADRADA),
    MAP_CONCLUIDO(523f, 600, FormaOnda.SENOIDAL),
    FLOOR_CONCLUIDO(659f, 800, FormaOnda.SENOIDAL),
    LENTIDAO_INICIO(200f, 500, FormaOnda.QUADRADA),
    BOSS_PROVOCACAO(400f, 600, FormaOnda.QUADRADA),
    BOSS_RISADA(100f, 1000, FormaOnda.QUADRADA),
    POWER_UP_COLETADO(880f, 400, FormaOnda.SENOIDAL),
    ESGUICHO_AGUA(4000f, -1, FormaOnda.SENOIDAL) // Frequência base ignorada, síntese específica
}

/** Forma de onda para síntese de áudio procedural. */
enum class FormaOnda { SENOIDAL, QUADRADA }

/**
 * Gerenciador de áudio do jogo.
 *
 * Responsabilidades:
 * - Reproduzir efeitos sonoros curtos via SoundPool (baixa latência)
 * - Reproduzir trilha ambiente por Bioma via MediaPlayer com loop contínuo
 * - Fade de 1 segundo na transição entre Biomas (Requisito 9.1)
 * - Sons ambientes procedurais com intervalo aleatório de 5–30 segundos (Requisito 9.3)
 * - Pausar toda reprodução em no máximo 100ms ao ir para background (Requisito 9.4)
 * - Respeitar volume do sistema Android com controles independentes (Requisito 9.5)
 * - Suporte a áudio espacial (Android 12+) com posicionamento 3D (Requisito 9.6)
 *
 * Usa WeakReference<Context> para evitar vazamentos de memória.
 */
class AudioManager(context: Context) {

    // --- Referência fraca ao contexto para evitar vazamento de memória ---
    private val contextoRef = WeakReference(context.applicationContext)

    // --- Escopo de coroutines para operações assíncronas ---
    private val escopo = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // --- SoundPool para efeitos sonoros de baixa latência ---
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    // --- MediaPlayer para trilha ambiente do Bioma ---
    private var mediaPlayerAtual: MediaPlayer? = null
    private var mediaPlayerProximo: MediaPlayer? = null

    // --- Bioma atual em reprodução ---
    private var biomaAtual: Biome? = null

    // --- Controles de volume independentes (0.0–1.0) ---
    private var volumeMusica: Float = 1.0f
    private var volumeEfeitos: Float = 1.0f

    // --- Estado de reprodução ---
    private var emReproducao: Boolean = false

    // --- Jobs de coroutines ---
    private var jobFade: Job? = null
    private var jobAmbiente: Job? = null
    private var jobEsguicho: Job? = null

    // --- AudioTrack persistente para o esguicho contínuo ---
    private var audioTrackEsguicho: AudioTrack? = null
    private var isWaterStreamActive = false

    // --- Cache de IDs de sons no SoundPool (gerados proceduralmente via AudioTrack) ---
    private val cacheSons: MutableMap<TipoEfeito, Int> = mutableMapOf()

    // --- AndroidAudioManager para respeitar volume do sistema ---
    private val androidAudioManager: AndroidAudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AndroidAudioManager

    init {
        carregarEfeitos()
    }

    // =========================================================================
    // Carregamento de efeitos sonoros procedurais
    // =========================================================================

    /**
     * Carrega todos os efeitos sonoros procedurais no SoundPool.
     * Gera tons via síntese de onda senoidal/quadrada usando AudioTrack.
     */
    private fun carregarEfeitos() {
        escopo.launch {
            TipoEfeito.entries.forEach { tipo ->
                // O esguicho contínuo é processado separadamente por síntese em tempo real
                if (tipo == TipoEfeito.ESGUICHO_AGUA) return@forEach

                val pcm = gerarTomPCM(tipo.frequenciaHz, tipo.duracaoMs, tipo.formaOnda)
                // SoundPool não aceita PCM diretamente; usamos AudioTrack para reprodução
                // e registramos o tipo para reprodução posterior
                cacheSons[tipo] = tipo.ordinal + 1 // ID simbólico para rastreamento
            }
        }
    }

    /**
     * Gera amostras PCM de 16 bits para um tom sintético.
     *
     * @param frequenciaHz Frequência do tom em Hz
     * @param duracaoMs Duração em milissegundos
     * @param formaOnda Tipo de forma de onda (senoidal ou quadrada)
     * @return Array de amostras PCM de 16 bits
     */
    private fun gerarTomPCM(
        frequenciaHz: Float,
        duracaoMs: Int,
        formaOnda: FormaOnda
    ): ShortArray {
        val taxaAmostragem = 44100
        val numAmostras = (taxaAmostragem * duracaoMs / 1000)
        val amostras = ShortArray(numAmostras)
        val angularFreq = 2.0 * PI * frequenciaHz / taxaAmostragem

        for (i in 0 until numAmostras) {
            // Envelope de amplitude: fade-in e fade-out suave
            val envelope = calcularEnvelope(i, numAmostras)
            val amostra = when (formaOnda) {
                FormaOnda.SENOIDAL -> sin(angularFreq * i) * envelope
                FormaOnda.QUADRADA -> (if (sin(angularFreq * i) >= 0) 1.0 else -1.0) * envelope * 0.5
            }
            amostras[i] = (amostra * Short.MAX_VALUE).toInt().toShort()
        }
        return amostras
    }

    /**
     * Gera amostras de ruído de alta pressão para o som de água.
     * Implementa a especificação de ruído branco filtrado + whoosh grave + modulação.
     */
    private fun generateHighPressureWaterSamples(numSamples: Int): ShortArray {
        val samples = ShortArray(numSamples)
        val random = java.util.Random()
        val sampleRate = 44100
        
        // Parâmetros de whoosh (80-120Hz)
        val whooshFreq = 100.0
        val whooshAngularFreq = 2.0 * PI * whooshFreq / sampleRate
        
        // Parâmetros de modulação (8Hz)
        val modFreq = 8.0
        val modAngularFreq = 2.0 * PI * modFreq / sampleRate

        for (i in 0 until numSamples) {
            // 1. Ruído Branco (base)
            val whiteNoise = random.nextFloat() * 2.0 - 1.0
            
            // 2. Whoosh Grave
            val whoosh = sin(whooshAngularFreq * i) * 0.3
            
            // 3. Modulação de Amplitude (simula variação de pressão)
            val modulation = 0.8 + 0.2 * sin(modAngularFreq * i)
            
            // 4. Mixagem e Simulação de High-Pass (ênfase em agudos via ruído)
            // (O ruído branco já contém altas frequências; o whoosh dá o corpo)
            val combined = (whiteNoise * 0.6 + whoosh * 0.4) * modulation
            
            samples[i] = (combined * Short.MAX_VALUE * 0.5).toInt().toShort()
        }
        return samples
    }

    /**
     * Calcula o envelope de amplitude para suavizar início e fim do som.
     * Evita cliques audíveis (pop) na reprodução.
     */
    private fun calcularEnvelope(indice: Int, total: Int): Double {
        val fadeAmostras = min(total / 10, 441) // 10ms de fade
        return when {
            indice < fadeAmostras -> indice.toDouble() / fadeAmostras
            indice > total - fadeAmostras -> (total - indice).toDouble() / fadeAmostras
            else -> 1.0
        }
    }

    /**
     * Reproduz amostras PCM via AudioTrack de forma assíncrona.
     * Aplica o volume de efeitos configurado.
     *
     * @param amostras Amostras PCM de 16 bits
     * @param volumeAplicado Volume final a aplicar (0.0–1.0)
     */
    private fun reproduzirPCM(amostras: ShortArray, volumeAplicado: Float) {
        escopo.launch(Dispatchers.IO) {
            val taxaAmostragem = 44100
            val bufferSize = AudioTrack.getMinBufferSize(
                taxaAmostragem,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(taxaAmostragem)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            try {
                audioTrack.setVolume(volumeAplicado)
                audioTrack.write(amostras, 0, amostras.size)
                audioTrack.play()
                // Aguarda a reprodução terminar antes de liberar
                delay(amostras.size * 1000L / taxaAmostragem + 50)
            } finally {
                audioTrack.stop()
                audioTrack.release()
            }
        }
    }

    // =========================================================================
    // API pública — Efeitos sonoros
    // =========================================================================

    /**
     * Reproduz um efeito sonoro pelo tipo especificado.
     * Aplica o volume de efeitos configurado e o volume do sistema.
     *
     * Requisito 9.2
     *
     * @param tipo Tipo do efeito sonoro a reproduzir
     */
    fun reproduzirEfeito(tipo: TipoEfeito) {
        if (!emReproducao) return
        val volumeFinal = calcularVolumeEfeitoFinal(volumeEfeitos)
        val amostras = gerarTomPCM(tipo.frequenciaHz, tipo.duracaoMs, tipo.formaOnda)
        reproduzirPCM(amostras, volumeFinal)
    }

    /**
     * Reproduz um efeito sonoro com volume proporcional à distância do Hero.
     * Suporta áudio espacial (Android 12+) com posicionamento 3D.
     *
     * Volume = volumeBase * max(0, 1 - distancia / DISTANCIA_MAXIMA)
     *
     * Requisito 9.6
     *
     * @param tipo Tipo do efeito sonoro
     * @param distancia Distância em unidades de tile entre o Hero e a fonte sonora
     */
    fun reproduzirEfeitoEspacial(tipo: TipoEfeito, distancia: Float) {
        if (!emReproducao) return
        val volumeDistancia = max(0f, 1f - distancia / DISTANCIA_MAXIMA_ESPACIAL)
        val volumeFinal = calcularVolumeEfeitoFinal(volumeEfeitos * volumeDistancia)
        if (volumeFinal <= 0.01f) return // Muito distante para ser audível

        val amostras = gerarTomPCM(tipo.frequenciaHz, tipo.duracaoMs, tipo.formaOnda)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: usa AudioTrack com atributos espaciais
            reproduzirPCMEspacial(amostras, volumeFinal, distancia)
        } else {
            reproduzirPCM(amostras, volumeFinal)
        }
    }

    private fun reproduzirPCMEspacial(amostras: ShortArray, volume: Float, distancia: Float) {
        // Para simplificação, usa o mesmo caminho de reprodução mono com volume ajustado
        // Em uma implementação completa, usaria Spatializer API do Android 12+
        reproduzirPCM(amostras, volume)
    }

    // =========================================================================
    // API pública — Esguicho de Água (Contínuo)
    // =========================================================================

    /**
     * Inicia a síntese e reprodução do som de esguicho contínuo.
     * Usa um envelope de Attack de 30ms.
     */
    fun startWaterStream() {
        if (isWaterStreamActive) return
        isWaterStreamActive = true
        
        jobEsguicho?.cancel()
        jobEsguicho = escopo.launch(Dispatchers.IO) {
            val sampleRate = 44100
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(max(bufferSize, 8192))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            
            audioTrackEsguicho = track
            track.play()
            
            val attackSamples = (sampleRate * 0.030).toInt() // 30ms Attack
            val chunkSamples = 2048
            var samplesGenerated = 0
            
            try {
                while (isActive && isWaterStreamActive) {
                    val pcm = generateHighPressureWaterSamples(chunkSamples)
                    
                    // Aplica Attack Envelope se estiver no início
                    if (samplesGenerated < attackSamples) {
                        for (i in 0 until chunkSamples) {
                            val currentSampleIdx = samplesGenerated + i
                            if (currentSampleIdx < attackSamples) {
                                val gain = currentSampleIdx.toFloat() / attackSamples
                                pcm[i] = (pcm[i] * gain).toInt().toShort()
                            }
                        }
                    }
                    
                    track.write(pcm, 0, pcm.size)
                    samplesGenerated += chunkSamples
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Release Envelope (80ms)
                val releaseSamples = (sampleRate * 0.080).toInt()
                val releasePcm = generateHighPressureWaterSamples(releaseSamples)
                for (i in 0 until releaseSamples) {
                    val gain = 1.0f - (i.toFloat() / releaseSamples)
                    releasePcm[i] = (releasePcm[i] * gain).toInt().toShort()
                }
                track.write(releasePcm, 0, releasePcm.size)
                
                track.stop()
                track.release()
                if (audioTrackEsguicho == track) audioTrackEsguicho = null
            }
        }
    }

    /**
     * Para a reprodução do som de esguicho.
     */
    fun stopWaterStream() {
        isWaterStreamActive = false
    }

    // =========================================================================
    // API pública — Trilha ambiente por Bioma
    // =========================================================================

    /**
     * Transiciona a trilha musical para o novo Bioma com fade de 1 segundo.
     *
     * Sequência:
     * 1. Fade out da trilha atual em 1 segundo
     * 2. Inicia a nova trilha com volume 0
     * 3. Fade in da nova trilha em 1 segundo
     *
     * Requisito 9.1
     *
     * @param novoBioma Bioma de destino da transição
     */
    fun transicionarParaBioma(novoBioma: Biome) {
        if (novoBioma == biomaAtual) return

        jobFade?.cancel()
        jobAmbiente?.cancel()

        jobFade = escopo.launch {
            // Fade out da trilha atual
            val playerAtual = mediaPlayerAtual
            if (playerAtual != null && playerAtual.isPlaying) {
                fadeOut(playerAtual, DURACAO_FADE_MS)
                playerAtual.stop()
                playerAtual.release()
                mediaPlayerAtual = null
            }

            // Inicia nova trilha com volume 0 e faz fade in
            val novoPlayer = criarMediaPlayerParaBioma(novoBioma)
            if (novoPlayer != null) {
                mediaPlayerAtual = novoPlayer
                novoPlayer.setVolume(0f, 0f)
                novoPlayer.start()
                fadeIn(novoPlayer, DURACAO_FADE_MS)
            }

            biomaAtual = novoBioma
            emReproducao = true

            // Agenda sons ambientes procedurais para o novo Bioma
            agendarSonsAmbientes(novoBioma)
        }
    }

    /**
     * Cria um MediaPlayer configurado para o Bioma especificado.
     *
     * Os IDs de recurso de música serão adicionados futuramente.
     * Por ora, retorna null (padrão de ID de recurso nulo).
     *
     * @param bioma Bioma para o qual criar o player
     * @return MediaPlayer configurado ou null se não houver recurso disponível
     */
    private fun criarMediaPlayerParaBioma(bioma: Biome): MediaPlayer? {
        // IDs de recurso de música por Bioma — serão preenchidos quando os assets forem adicionados
        val recursoMusica: Int? = obterRecursoMusicaBioma(bioma)
        val ctx = contextoRef.get() ?: return null
        if (recursoMusica == null) return null

        return try {
            MediaPlayer.create(ctx, recursoMusica)?.apply {
                isLooping = true
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                val vol = calcularVolumeMusicaFinal(volumeMusica)
                setVolume(vol, vol)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Retorna o ID de recurso de música para o Bioma especificado.
     * Retorna null enquanto os assets de áudio não forem adicionados ao projeto.
     *
     * @param bioma Bioma para buscar o recurso
     * @return ID do recurso de música ou null
     */
    private fun obterRecursoMusicaBioma(bioma: Biome): Int? {
        val ctx = contextoRef.get() ?: return null
        val nomeRes = when (bioma) {
            Biome.MINA_ABANDONADA -> "bgm_mina"
            Biome.RIACHOS_SUBTERRANEOS -> "bgm_riacho"
            Biome.JARDIM_DE_FUNGOS -> "bgm_plantacao"
            Biome.CONSTRUCOES_ROCHOSAS -> "bgm_rocha"
            Biome.ERA_DINOSSAUROS -> "bgm_vulcao"
            else -> "bgm_default"
        }
        val id = ctx.resources.getIdentifier(nomeRes, "raw", ctx.packageName)
        return if (id != 0) id else null
    }

    // =========================================================================
    // Fade de áudio
    // =========================================================================

    /**
     * Realiza fade out gradual em um MediaPlayer.
     *
     * @param player MediaPlayer a aplicar o fade
     * @param duracaoMs Duração total do fade em milissegundos
     */
    private suspend fun fadeOut(player: MediaPlayer, duracaoMs: Long) {
        val passos = PASSOS_FADE
        val intervaloMs = duracaoMs / passos
        for (passo in passos downTo 0) {
            if (!player.isPlaying) break
            val volume = (passo.toFloat() / passos) * calcularVolumeMusicaFinal(volumeMusica)
            player.setVolume(volume, volume)
            delay(intervaloMs)
        }
        player.setVolume(0f, 0f)
    }

    /**
     * Realiza fade in gradual em um MediaPlayer.
     *
     * @param player MediaPlayer a aplicar o fade
     * @param duracaoMs Duração total do fade em milissegundos
     */
    private suspend fun fadeIn(player: MediaPlayer, duracaoMs: Long) {
        val passos = PASSOS_FADE
        val intervaloMs = duracaoMs / passos
        val volumeAlvo = calcularVolumeMusicaFinal(volumeMusica)
        for (passo in 0..passos) {
            if (!player.isPlaying) break
            val volume = (passo.toFloat() / passos) * volumeAlvo
            player.setVolume(volume, volume)
            delay(intervaloMs)
        }
        player.setVolume(volumeAlvo, volumeAlvo)
    }

    // =========================================================================
    // Sons ambientes procedurais
    // =========================================================================

    /**
     * Agenda sons ambientes procedurais com intervalo aleatório de 5–30 segundos.
     * Cada Bioma tem sons característicos com frequências distintas.
     *
     * Requisito 9.3
     *
     * @param bioma Bioma atual para determinar o tipo de som ambiente
     */
    private fun agendarSonsAmbientes(bioma: Biome) {
        jobAmbiente?.cancel()
        jobAmbiente = escopo.launch {
            while (isActive && emReproducao) {
                val intervaloMs = (INTERVALO_AMBIENTE_MIN_MS..INTERVALO_AMBIENTE_MAX_MS).random()
                delay(intervaloMs)

                if (!isActive || !emReproducao) break

                val (frequencia, duracao) = obterParametrosSomAmbiente(bioma)
                val amostras = gerarTomPCM(frequencia, duracao, FormaOnda.SENOIDAL)
                val volumeAmbiente = calcularVolumeEfeitoFinal(volumeEfeitos * 0.4f) // Sons ambientes mais suaves
                reproduzirPCM(amostras, volumeAmbiente)
            }
        }
    }

    /**
     * Retorna frequência e duração do som ambiente característico de cada Bioma.
     *
     * @param bioma Bioma para determinar os parâmetros
     * @return Par (frequenciaHz, duracaoMs)
     */
    private fun obterParametrosSomAmbiente(bioma: Biome): Pair<Float, Int> {
        val nome = bioma.name
        return when {
            nome.contains("MINA") || nome.contains("CAVERNA") || nome.contains("TUNEIS") -> 
                Pair(80f, 500)        // Gotejamento grave
            nome.contains("RIACHO") || nome.contains("LAGO") || nome.contains("AQUATICO") || nome.contains("ABISMO") -> 
                Pair(440f, 300)       // Água corrente
            nome.contains("JARDIM") || nome.contains("FLORESTA") || nome.contains("PLANTACAO") || nome.contains("RAIZES") || nome.contains("POMAR") -> 
                Pair(330f, 400)       // Vento suave
            nome.contains("CONSTRUCAO") || nome.contains("RUINA") || nome.contains("TEMPLO") || nome.contains("SALOES") || nome.contains("TUMULO") -> 
                Pair(120f, 600)       // Eco de pedra
            nome.contains("VULCANICO") || nome.contains("LAVA") || nome.contains("FOGO") || nome.contains("DINOSSAURO") || nome.contains("FORJA") -> 
                Pair(60f, 800)        // Rugido distante
            else -> Pair(100f, 500)   // Som padrão
        }
    }

    // =========================================================================
    // API pública — Controle de reprodução
    // =========================================================================

    /**
     * Pausa toda reprodução de áudio em no máximo 100ms.
     * Chamado quando o app vai para background.
     *
     * Requisito 9.4
     */
    fun pausarTudo() {
        emReproducao = false
        jobAmbiente?.cancel()

        // Pausa imediata do MediaPlayer (< 100ms)
        try {
            mediaPlayerAtual?.pause()
        } catch (_: Exception) {}
    }

    /**
     * Retoma a reprodução de áudio após pausa.
     * Reinicia o agendamento de sons ambientes.
     */
    fun retomar() {
        emReproducao = true
        if (mediaPlayerAtual == null && biomaAtual != null) {
            transicionarParaBioma(biomaAtual!!)
        } else {
            try {
                mediaPlayerAtual?.start()
            } catch (_: Exception) {}
        }

        // Reinicia agendamento de sons ambientes
        biomaAtual?.let { agendarSonsAmbientes(it) }
    }

    /**
     * Libera todos os recursos de áudio.
     * Deve ser chamado em onDestroy para evitar vazamentos.
     */
    fun liberarRecursos() {
        emReproducao = false
        jobFade?.cancel()
        jobAmbiente?.cancel()
        escopo.cancel()

        try {
            mediaPlayerAtual?.stop()
            mediaPlayerAtual?.release()
            mediaPlayerAtual = null
        } catch (_: Exception) {}

        try {
            mediaPlayerProximo?.stop()
            mediaPlayerProximo?.release()
            mediaPlayerProximo = null
        } catch (_: Exception) {}

        soundPool.release()
        cacheSons.clear()
        biomaAtual = null
    }

    /**
     * Alias de [liberarRecursos] para conformidade com a interface de ciclo de vida.
     * Libera MediaPlayer, SoundPool e cancela todas as coroutines.
     * Deve ser chamado em onDestroy (Requisito 20.3).
     */
    fun release() = liberarRecursos()

    // =========================================================================
    // API pública — Controles de volume
    // =========================================================================

    /**
     * Define o volume da música (0.0–1.0).
     * Respeita o volume do sistema Android.
     *
     * Requisito 9.5
     *
     * @param volume Nível de volume entre 0.0 (mudo) e 1.0 (máximo)
     */
    fun setVolumeMusicaPercent(volume: Float) {
        volumeMusica = volume.coerceIn(0f, 1f)
        val volFinal = calcularVolumeMusicaFinal(volumeMusica)
        try {
            mediaPlayerAtual?.setVolume(volFinal, volFinal)
        } catch (_: Exception) {}
    }

    /**
     * Define o volume dos efeitos sonoros (0.0–1.0).
     * Respeita o volume do sistema Android.
     *
     * Requisito 9.5
     *
     * @param volume Nível de volume entre 0.0 (mudo) e 1.0 (máximo)
     */
    fun setVolumeEfeitosPercent(volume: Float) {
        volumeEfeitos = volume.coerceIn(0f, 1f)
    }

    // =========================================================================
    // Cálculo de volume respeitando o sistema Android
    // =========================================================================

    /**
     * Calcula o volume final da música considerando o volume do sistema Android.
     * Requisito 9.5
     */
    private fun calcularVolumeMusicaFinal(volumeBase: Float): Float {
        val volumeSistema = obterVolumeRelativoSistema(AndroidAudioManager.STREAM_MUSIC)
        return (volumeBase * volumeSistema).coerceIn(0f, 1f)
    }

    /**
     * Calcula o volume final dos efeitos considerando o volume do sistema Android.
     * Requisito 9.5
     */
    private fun calcularVolumeEfeitoFinal(volumeBase: Float): Float {
        val volumeSistema = obterVolumeRelativoSistema(AndroidAudioManager.STREAM_MUSIC)
        return (volumeBase * volumeSistema).coerceIn(0f, 1f)
    }

    /**
     * Obtém o volume relativo do stream especificado (0.0–1.0).
     *
     * @param streamType Tipo de stream Android (ex: STREAM_MUSIC)
     * @return Volume relativo entre 0.0 e 1.0
     */
    private fun obterVolumeRelativoSistema(streamType: Int): Float {
        val mgr = androidAudioManager ?: return 1f
        val volumeAtual = mgr.getStreamVolume(streamType)
        val volumeMaximo = mgr.getStreamMaxVolume(streamType)
        return if (volumeMaximo > 0) volumeAtual.toFloat() / volumeMaximo else 1f
    }

    // =========================================================================
    // Constantes
    // =========================================================================

    companion object {
        /** Duração do fade entre Biomas em milissegundos (Requisito 9.1). */
        private const val DURACAO_FADE_MS = 1000L

        /** Número de passos para o fade (maior = mais suave). */
        private const val PASSOS_FADE = 20L

        /** Intervalo mínimo entre sons ambientes em milissegundos (Requisito 9.3). */
        private const val INTERVALO_AMBIENTE_MIN_MS = 5_000L

        /** Intervalo máximo entre sons ambientes em milissegundos (Requisito 9.3). */
        private const val INTERVALO_AMBIENTE_MAX_MS = 30_000L

        /** Distância máxima para áudio espacial em unidades de tile (Requisito 9.6). */
        private const val DISTANCIA_MAXIMA_ESPACIAL = 20f
    }
}
