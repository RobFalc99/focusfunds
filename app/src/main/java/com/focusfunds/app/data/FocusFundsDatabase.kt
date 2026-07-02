package com.focusfunds.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WalletState::class, BlockedApp::class, ActiveUnlockSession::class, Transaction::class],
    version = 2,
    exportSchema = false
)
abstract class FocusFundsDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun unlockSessionDao(): UnlockSessionDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: FocusFundsDatabase? = null

        fun getDatabase(context: Context): FocusFundsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FocusFundsDatabase::class.java,
                    "focus_funds_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
