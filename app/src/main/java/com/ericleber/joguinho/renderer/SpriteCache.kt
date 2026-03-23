package com.ericleber.joguinho.renderer

import android.graphics.Bitmap

/**
 * Cache de Bitmaps pré-renderizados para sprites gerados proceduralmente.
 *
 * Sprites são gerados uma vez e armazenados em memória. A evicção ocorre
 * quando o bioma ativo muda ou quando onTrimMemory sinaliza memória baixa.
 *
 * Requisitos: 17.7, 20.1, 20.6
 */
class SpriteCache {

    private val cache = HashMap<String, Bitmap>()

    /** Bioma ativo atual — usado para evicção seletiva. */
    var currentBiome: String = ""

    /**
     * Retorna o Bitmap do cache ou cria um novo usando [generator].
     * @param key chave única do sprite (ex: "biome_MINA_ABANDONADA_wall")
     * @param generator função que cria o Bitmap se não estiver em cache
     */
    fun getOrCreate(key: String, generator: () -> Bitmap): Bitmap {
        return cache.getOrPut(key) { generator() }
    }

    /**
     * Remove sprites de biomas não ativos e chama Bitmap.recycle() nos evictados.
     * Sprites do bioma atual e sprites não relacionados a biomas são preservados.
     * Requisito 20.1
     */
    fun evictNonEssential() {
        val toRemove = cache.entries.filter { entry ->
            entry.key.startsWith("biome_") && !entry.key.contains(currentBiome)
        }
        toRemove.forEach { entry ->
            entry.value.recycle()
            cache.remove(entry.key)
        }
    }

    /**
     * Recicla TODOS os bitmaps e limpa o cache ao encerrar um Map.
     * Deve ser chamado quando um Map é concluído para liberar memória nativa imediatamente.
     * Requisito 20.1
     */
    fun recycleAll() {
        cache.values.forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        cache.clear()
    }

    /**
     * Recicla TODOS os bitmaps e limpa o cache.
     * Deve ser chamado em onDestroy para liberar memória nativa.
     * Requisito 20.1
     */
    fun clear() {
        recycleAll()
    }

    /**
     * Retorna o número de entradas atualmente no cache.
     */
    fun size(): Int = cache.size
}
