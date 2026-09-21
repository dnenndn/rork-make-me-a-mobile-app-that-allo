package com.rork.plcpanelstudio.data

import android.content.Context

/**
 * Keeps operators from controlling the PLC without the password.
 *
 * Only a salted hash of the password is stored (see [PasswordHasher]). Unlocking lasts for a
 * short session that ends by itself when the controls are not used ([UnlockSession]), and wrong
 * passwords are slowed down ([AttemptLimiter]) even across app restarts.
 * Held as a process singleton so the unlocked state is shared by every screen.
 */
class ControlLock private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val session = UnlockSession()
    private val limiter = AttemptLimiter(
        initial = AttemptLimiter.State(
            failures = prefs.getInt(KEY_FAILURES, 0),
            lockedUntil = prefs.getLong(KEY_LOCKED_UNTIL, 0L)
        )
    )

    /** True once a control password has been created. */
    val hasPassword: Boolean get() = prefs.getString(KEY_HASH, null) != null

    /** True while the controls may be used. Without a password nothing is ever unlocked. */
    val isUnlocked: Boolean get() = synchronized(this) { hasPassword && session.isUnlocked }

    /** Creates the first password and unlocks. Returns an error message, or null on success. */
    fun createPassword(password: String, confirm: String): String? {
        if (hasPassword) return "A password is already set."
        PasswordPolicy.validateNew(password, confirm)?.let { return it }
        val hash = PasswordHasher.hash(password)
        synchronized(this) {
            prefs.edit().putString(KEY_HASH, hash).apply()
            limiter.recordSuccess()
            saveLimiter()
            session.unlock()
        }
        return null
    }

    fun unlock(password: String): UnlockResult {
        val result = check(password)
        if (result is UnlockResult.Success) synchronized(this) { session.unlock() }
        return result
    }

    /** Changes the password after checking the current one. Returns an error message, or null. */
    fun changePassword(current: String, new: String, confirm: String): String? {
        check(current).errorMessage()?.let { return it }
        PasswordPolicy.validateNew(new, confirm)?.let { return it }
        val hash = PasswordHasher.hash(new)
        synchronized(this) {
            prefs.edit().putString(KEY_HASH, hash).apply()
            session.unlock()
        }
        return null
    }

    fun lock() = synchronized(this) { session.lock() }

    /** Registers use of a control so the unlocked session does not time out while it is in use. */
    fun touch() = synchronized(this) { session.touch() }

    /** Checks [password] against the stored hash, counting wrong attempts. */
    private fun check(password: String): UnlockResult {
        val stored = prefs.getString(KEY_HASH, null) ?: return UnlockResult.NoPassword
        synchronized(this) {
            if (limiter.isLocked()) return UnlockResult.TooManyAttempts(limiter.remainingLockMs())
        }
        // Slow on purpose, so it runs outside the lock and does not block the screen.
        val correct = PasswordHasher.verify(password, stored)
        synchronized(this) {
            if (correct) {
                limiter.recordSuccess()
                saveLimiter()
                return UnlockResult.Success
            }
            limiter.recordFailure()
            saveLimiter()
            return if (limiter.isLocked()) {
                UnlockResult.TooManyAttempts(limiter.remainingLockMs())
            } else {
                UnlockResult.WrongPassword(limiter.attemptsLeft())
            }
        }
    }

    private fun saveLimiter() {
        val state = limiter.state
        prefs.edit()
            .putInt(KEY_FAILURES, state.failures)
            .putLong(KEY_LOCKED_UNTIL, state.lockedUntil)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "control_lock"
        private const val KEY_HASH = "password_hash"
        private const val KEY_FAILURES = "failures"
        private const val KEY_LOCKED_UNTIL = "locked_until"

        @Volatile
        private var instance: ControlLock? = null

        fun get(context: Context): ControlLock =
            instance ?: synchronized(this) {
                instance ?: ControlLock(context).also { instance = it }
            }
    }
}
