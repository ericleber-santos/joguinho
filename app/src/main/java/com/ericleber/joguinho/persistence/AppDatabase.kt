package com.ericleber.joguinho.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Banco de dados Room do aplicativo.
 * Mantém um buffer circular de 3 snapshots de segurança do estado do jogo.
 * Versão 2: adicionada tabela de placar local (leaderboard).
 */
@Database(
    entities = [SaveStateEntity::class, LeaderboardEntryEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun saveStateDao(): SaveStateDao
    abstract fun leaderboardDao(): LeaderboardDao

    companion object {
        @Volatile
        private var instancia: AppDatabase? = null

        /**
         * Retorna a instância singleton do banco de dados.
         * Utiliza double-checked locking para segurança em ambientes concorrentes.
         */
        fun getInstance(contexto: Context): AppDatabase {
            return instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    contexto.applicationContext,
                    AppDatabase::class.java,
                    "caverna_do_spike.db"
                )
                    .fallbackToDestructiveMigration()
                    .build().also { instancia = it }
            }
        }
    }
}
