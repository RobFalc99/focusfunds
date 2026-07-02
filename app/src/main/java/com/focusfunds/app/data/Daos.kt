package com.focusfunds.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallet_state WHERE id = 1 LIMIT 1")
    fun getWalletState(): WalletState?

    @Query("SELECT * FROM wallet_state WHERE id = 1 LIMIT 1")
    fun getWalletStateFlow(): Flow<WalletState?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertWalletState(state: WalletState)

    @Update
    fun updateWalletState(state: WalletState)
}

@Dao
interface BlockedAppDao {
    @Query("SELECT * FROM blocked_apps")
    fun getAllBlockedApps(): List<BlockedApp>

    @Query("SELECT * FROM blocked_apps")
    fun getAllBlockedAppsFlow(): Flow<List<BlockedApp>>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_apps WHERE packageName = :packageName AND isBlocked = 1 LIMIT 1)")
    fun isAppBlocked(packageName: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBlockedApp(app: BlockedApp)

    @Delete
    fun deleteBlockedApp(app: BlockedApp)
}

@Dao
interface UnlockSessionDao {
    @Query("SELECT * FROM active_unlock_sessions WHERE packageName = :packageName LIMIT 1")
    fun getSession(packageName: String): ActiveUnlockSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSession(session: ActiveUnlockSession)

    @Query("DELETE FROM active_unlock_sessions WHERE packageName = :packageName")
    fun deleteSession(packageName: String)

    @Query("SELECT * FROM active_unlock_sessions")
    fun getAllSessions(): List<ActiveUnlockSession>
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): List<Transaction>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactionsFlow(): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTransaction(transaction: Transaction)
}
