package com.focusfunds.app.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class FocusFundsRepository(private val context: Context) {
    private val db = FocusFundsDatabase.getDatabase(context)
    private val walletDao = db.walletDao()
    private val blockedAppDao = db.blockedAppDao()
    private val unlockSessionDao = db.unlockSessionDao()
    private val transactionDao = db.transactionDao()

    val walletStateFlow: Flow<WalletState?> = walletDao.getWalletStateFlow()
    val blockedAppsFlow: Flow<List<BlockedApp>> = blockedAppDao.getAllBlockedAppsFlow()
    val transactionsFlow: Flow<List<Transaction>> = transactionDao.getAllTransactionsFlow()

    suspend fun getWalletState(): WalletState = withContext(Dispatchers.IO) {
        var state = walletDao.getWalletState()
        if (state == null) {
            val startingBalance = checkFirstInstallAndGetBonus()
            state = WalletState(balance = startingBalance)
            walletDao.insertWalletState(state)
            
            if (startingBalance > 0.0) {
                transactionDao.insertTransaction(
                    Transaction(
                        amount = startingBalance,
                        type = "DEPOSIT",
                        description = "Welcome Bonus",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
        state
    }

    private fun checkFirstInstallAndGetBonus(): Double {
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), ".ff_history")
        return if (file.exists()) {
            0.0
        } else {
            try {
                file.parentFile?.mkdirs()
                file.createNewFile()
                file.writeText("installed_at_${System.currentTimeMillis()}")
            } catch (e: Exception) {
                // If public storage fails, it will fall back to default,
                // but we try to persist this to enforce anti-cheat
            }
            5.0
        }
    }

    suspend fun updateWalletState(state: WalletState) = withContext(Dispatchers.IO) {
        walletDao.updateWalletState(state)
    }

    suspend fun updateSelectedTheme(themeId: Int) = withContext(Dispatchers.IO) {
        val currentState = getWalletState()
        walletDao.updateWalletState(currentState.copy(selectedTheme = themeId))
    }

    suspend fun addFocusFunds(amount: Double, description: String) = withContext(Dispatchers.IO) {
        val currentState = getWalletState()
        val newBalance = currentState.balance + amount
        walletDao.updateWalletState(currentState.copy(balance = newBalance))
        transactionDao.insertTransaction(
            Transaction(
                amount = amount,
                type = "DEPOSIT",
                description = description,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun deductFocusFunds(amount: Double, description: String, isEmergency: Boolean = false) = withContext(Dispatchers.IO) {
        val currentState = getWalletState()
        val newBalance = currentState.balance - amount
        walletDao.updateWalletState(currentState.copy(balance = newBalance))
        transactionDao.insertTransaction(
            Transaction(
                amount = amount,
                type = if (isEmergency) "EMERGENCY" else "WITHDRAWAL",
                description = description,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun getBlockedApps(): List<BlockedApp> = withContext(Dispatchers.IO) {
        blockedAppDao.getAllBlockedApps()
    }

    suspend fun isAppBlocked(packageName: String): Boolean = withContext(Dispatchers.IO) {
        blockedAppDao.isAppBlocked(packageName)
    }

    suspend fun addBlockedApp(app: BlockedApp) = withContext(Dispatchers.IO) {
        blockedAppDao.insertBlockedApp(app)
    }

    suspend fun removeBlockedApp(app: BlockedApp) = withContext(Dispatchers.IO) {
        blockedAppDao.deleteBlockedApp(app)
    }

    suspend fun getSession(packageName: String): ActiveUnlockSession? = withContext(Dispatchers.IO) {
        unlockSessionDao.getSession(packageName)
    }

    suspend fun insertSession(session: ActiveUnlockSession) = withContext(Dispatchers.IO) {
        unlockSessionDao.insertSession(session)
    }

    suspend fun deleteSession(packageName: String) = withContext(Dispatchers.IO) {
        unlockSessionDao.deleteSession(packageName)
    }

    suspend fun getAllSessions(): List<ActiveUnlockSession> = withContext(Dispatchers.IO) {
        unlockSessionDao.getAllSessions()
    }
}
