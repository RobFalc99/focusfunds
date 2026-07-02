package com.focusfunds.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallet_state")
data class WalletState(
    @PrimaryKey val id: Int = 1,
    val balance: Double = 5.0, // Welcome bonus of 5.0 FF
    val inFocusMode: Boolean = false,
    val focusStartTimestamp: Long = 0L
)

@Entity(tableName = "blocked_apps")
data class BlockedApp(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isBlocked: Boolean = true
)

@Entity(tableName = "active_unlock_sessions")
data class ActiveUnlockSession(
    @PrimaryKey val packageName: String,
    val unlockEndTime: Long,
    val isEmergency: Boolean = false
)

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val type: String, // "DEPOSIT", "WITHDRAWAL", "EMERGENCY"
    val description: String,
    val timestamp: Long
)
