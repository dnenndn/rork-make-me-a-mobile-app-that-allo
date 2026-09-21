package com.rork.plcpanelstudio.data

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Rules for the control password. */
object PasswordPolicy {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 64

    /** Returns a message describing what is wrong with a new password, or null if it is fine. */
    fun validateNew(password: String, confirm: String): String? = when {
        password.length < MIN_LENGTH -> "Use at least $MIN_LENGTH characters."
        password.length > MAX_LENGTH -> "Use at most $MAX_LENGTH characters."
        password != confirm -> "The two passwords don't match."
        else -> null
    }
}

/**
 * Salted, slow password hashing (PBKDF2). Only the hash is ever stored, never the password.
 * A stored value looks like `algorithm:iterations:saltHex:hashHex`, so the algorithm and cost
 * can change later without breaking passwords that were already saved.
 */
object PasswordHasher {
    private const val SEPARATOR = ":"
    private const val ITERATIONS = 100_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    /** SHA-256 is preferred; Android 7 (API 24/25) only has the SHA-1 variant. */
    private val DEFAULT_ALGORITHMS = listOf("PBKDF2WithHmacSHA256", "PBKDF2WithHmacSHA1")

    fun hash(
        password: String,
        salt: ByteArray = randomSalt(),
        algorithms: List<String> = DEFAULT_ALGORITHMS
    ): String {
        var lastError: Exception? = null
        for (algorithm in algorithms) {
            try {
                val key = derive(algorithm, password, salt, ITERATIONS, KEY_BITS)
                return listOf(algorithm, ITERATIONS.toString(), toHex(salt), toHex(key))
                    .joinToString(SEPARATOR)
            } catch (error: NoSuchAlgorithmException) {
                lastError = error
            }
        }
        throw IllegalStateException("No PBKDF2 implementation available", lastError)
    }

    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split(SEPARATOR)
        if (parts.size != 4) return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return false
        val salt = fromHex(parts[2]) ?: return false
        val expected = fromHex(parts[3]) ?: return false
        val actual = try {
            derive(parts[0], password, salt, iterations, expected.size * 8)
        } catch (error: Exception) {
            return false
        }
        // Constant-time comparison.
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(algorithm: String, password: String, salt: ByteArray, iterations: Int, bits: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, bits)
        try {
            return SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun randomSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    private fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun fromHex(text: String): ByteArray? {
        if (text.isEmpty() || text.length % 2 != 0) return null
        return try {
            ByteArray(text.length / 2) { index ->
                text.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        } catch (error: NumberFormatException) {
            null
        }
    }
}

/**
 * Slows down password guessing: after [freeAttempts] wrong passwords the lock opens for a
 * while, and every further wrong password doubles that wait (up to [maxLockMs]).
 * The [State] can be saved and restored so restarting the app does not reset it.
 */
class AttemptLimiter(
    private val freeAttempts: Int = 5,
    private val baseLockMs: Long = 30_000L,
    private val maxLockMs: Long = 300_000L,
    initial: State = State(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    data class State(val failures: Int = 0, val lockedUntil: Long = 0L)

    var state: State = initial
        private set

    fun remainingLockMs(): Long = (state.lockedUntil - clock()).coerceAtLeast(0L)

    fun isLocked(): Boolean = remainingLockMs() > 0L

    /** Wrong attempts still allowed before the first temporary lock. */
    fun attemptsLeft(): Int = (freeAttempts - state.failures).coerceAtLeast(0)

    fun recordFailure() {
        val failures = state.failures + 1
        val lockedUntil = if (failures >= freeAttempts) {
            val doublings = (failures - freeAttempts).coerceAtMost(20)
            clock() + (baseLockMs shl doublings).coerceAtMost(maxLockMs)
        } else {
            state.lockedUntil
        }
        state = State(failures, lockedUntil)
    }

    fun recordSuccess() {
        state = State()
    }
}

/**
 * The "controls are unlocked" state. It ends by itself after [timeoutMs] without use, and
 * every use ([touch]) starts the countdown again.
 */
class UnlockSession(
    private val timeoutMs: Long = 120_000L,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private var lastActivityAt: Long? = null

    val isUnlocked: Boolean
        get() {
            val last = lastActivityAt ?: return false
            if (clock() - last >= timeoutMs) {
                lastActivityAt = null
                return false
            }
            return true
        }

    fun unlock() {
        lastActivityAt = clock()
    }

    /** Counts as activity, but only while unlocked (it never unlocks by itself). */
    fun touch() {
        if (isUnlocked) lastActivityAt = clock()
    }

    fun lock() {
        lastActivityAt = null
    }
}

/** Outcome of checking a password. */
sealed interface UnlockResult {
    data object Success : UnlockResult
    data object NoPassword : UnlockResult
    data class WrongPassword(val attemptsLeft: Int) : UnlockResult
    data class TooManyAttempts(val retryInMs: Long) : UnlockResult
}

/** Text to show the user, or null on success. */
fun UnlockResult.errorMessage(): String? = when (this) {
    UnlockResult.Success -> null
    UnlockResult.NoPassword -> "No password is set yet."
    is UnlockResult.WrongPassword ->
        if (attemptsLeft > 0) {
            "Wrong password. $attemptsLeft ${if (attemptsLeft == 1) "attempt" else "attempts"} left before a temporary lock."
        } else {
            "Wrong password."
        }
    is UnlockResult.TooManyAttempts -> "Too many attempts. Try again in ${(retryInMs + 999) / 1000} s."
}
