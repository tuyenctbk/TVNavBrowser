package com.tdpham.navitvbrowser.util

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

private val Context.usageQuotaDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ai_usage_quota_datastore"
)

/**
 * Hybrid Quota Ledger (Playbook Section 1.B):
 * - Firestore Source of Truth (/users/{userId}/usageQuota/daily) with real-time snapshot listener.
 * - Local Jetpack DataStore mirror (Preferences DataStore) emitting real-time Flow<QuotaSnapshot> for instant UI feedback,
 *   paired with a synchronous cache for 0ms non-blocking reads on cold start.
 * - Automatic Rollover Reset when existing.dateIso != currentPeriodIso while preserving ecoLiteMode.
 * - Atomic Deduct-with-Refund: only deducts credits when a cloud AI request succeeds; refunds on rollback.
 */
object UsageQuotaManager {

    private const val TAG = "UsageQuotaManager"
    private const val PREFS_NAME = "ai_usage_quota_ledger"

    private const val KEY_DATE_ISO = "date_iso"
    private const val KEY_SUMMARY_USED = "summary_used"
    private const val KEY_CHAT_USED = "chat_used"
    private const val KEY_VOICE_USED = "voice_used"
    private const val KEY_BONUS_GRACE_CREDITS = "bonus_grace_credits"
    private const val KEY_GRACE_CLAIMED = "grace_claimed"
    private const val KEY_ECO_LITE_MODE = "eco_lite_mode"
    private const val KEY_FALLBACK_CASCADE_COUNT = "fallback_cascade_count"

    // Jetpack DataStore Preferences Keys
    private val DS_DATE_ISO = stringPreferencesKey(KEY_DATE_ISO)
    private val DS_SUMMARY_USED = intPreferencesKey(KEY_SUMMARY_USED)
    private val DS_CHAT_USED = intPreferencesKey(KEY_CHAT_USED)
    private val DS_VOICE_USED = intPreferencesKey(KEY_VOICE_USED)
    private val DS_BONUS_GRACE_CREDITS = intPreferencesKey(KEY_BONUS_GRACE_CREDITS)
    private val DS_GRACE_CLAIMED = booleanPreferencesKey(KEY_GRACE_CLAIMED)
    private val DS_ECO_LITE_MODE = booleanPreferencesKey(KEY_ECO_LITE_MODE)
    private val DS_FALLBACK_CASCADE_COUNT = intPreferencesKey(KEY_FALLBACK_CASCADE_COUNT)
    private val DS_IS_SUPPORTER = booleanPreferencesKey("is_supporter")

    /** Free daily cloud AI allowance (Tier 3 local summarizer & Q&A is always unlimited). */
    const val DAILY_BASE_ALLOWANCE = 15
    const val SUPPORTER_BASE_ALLOWANCE = 30
    const val GRACE_CREDIT_PACK_AMOUNT = 3
    const val SOFT_NUDGE_THRESHOLD_RATIO = 0.80f

    private val ledgerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listenerLock = Any()

    @Volatile
    private var firestoreListener: ListenerRegistration? = null

    @Volatile
    private var activeListeningUserId: String? = null

    @Volatile
    private var hasSeededDataStore = false

    enum class AiFeature {
        SUMMARY,
        CHAT,
        VOICE
    }

    data class QuotaSnapshot(
        val dateIso: String,
        val summaryUsed: Int,
        val chatUsed: Int,
        val voiceUsed: Int,
        val bonusGraceCredits: Int,
        val graceClaimed: Boolean,
        val ecoLiteMode: Boolean,
        val fallbackCascadeCount: Int,
        val isSupporter: Boolean = false
    ) {
        val totalUsed: Int get() = summaryUsed + chatUsed + voiceUsed
        val baseAllowance: Int get() = if (isSupporter) SUPPORTER_BASE_ALLOWANCE else DAILY_BASE_ALLOWANCE
        val totalAllowance: Int get() = baseAllowance + bonusGraceCredits
        val remainingCredits: Int get() = (totalAllowance - totalUsed).coerceAtLeast(0)
        val usageRatio: Float get() = if (totalAllowance > 0) totalUsed.toFloat() / totalAllowance else 1f
        val isAtOrAboveSoftNudge: Boolean get() = usageRatio >= SOFT_NUDGE_THRESHOLD_RATIO && remainingCredits > 0
        val isExhausted: Boolean get() = remainingCredits <= 0
    }

    private fun currentDateIso(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    /**
     * Resolves the active user ID for optional Firestore quota sync using the sanitized Sync User ID.
     */
    fun resolveQuotaUserId(context: Context): String {
        return AppPreferences.sanitizeSyncUserId(AppPreferences.getSyncUserId(context))
    }

    /**
     * Real-time Jetpack DataStore Flow emitting QuotaSnapshot updates whenever local AI usage
     * or the Firestore mirror changes. Automatically handles midnight rollover resets.
     */
    fun observeQuotaSnapshot(context: Context): Flow<QuotaSnapshot> {
        val appContext = context.applicationContext
        return appContext.usageQuotaDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { prefs ->
                val today = currentDateIso()
                val storedDate = prefs[DS_DATE_ISO] ?: ""
                val supporter = prefs[DS_IS_SUPPORTER] ?: DonationManager.isSupporter(appContext)
                if (storedDate != today) {
                    // Delegate to getSnapshot() so an uninitialized DataStore never zeroes out today's SharedPreferences usage
                    val current = getSnapshot(appContext)
                    persistDataStoreAsync(appContext, current)
                    current
                } else {
                    QuotaSnapshot(
                        dateIso = today,
                        summaryUsed = prefs[DS_SUMMARY_USED] ?: 0,
                        chatUsed = prefs[DS_CHAT_USED] ?: 0,
                        voiceUsed = prefs[DS_VOICE_USED] ?: 0,
                        bonusGraceCredits = prefs[DS_BONUS_GRACE_CREDITS] ?: 0,
                        graceClaimed = prefs[DS_GRACE_CLAIMED] ?: false,
                        ecoLiteMode = prefs[DS_ECO_LITE_MODE] ?: false,
                        fallbackCascadeCount = prefs[DS_FALLBACK_CASCADE_COUNT] ?: 0,
                        isSupporter = supporter
                    )
                }
            }
            .distinctUntilChanged()
    }

    /**
     * Attaches a real-time Firestore snapshot listener on `/users/{userId}/usageQuota/daily`
     * and mirrors remote updates into local Jetpack DataStore for instant reactive UI feedback.
     * Offloaded onto [ledgerScope] so calling from Activity.onCreate/onResume never blocks VSync.
     */
    fun startRealtimeSync(context: Context) {
        val appContext = context.applicationContext
        ledgerScope.launch {
            if (!hasSeededDataStore) {
                hasSeededDataStore = true
                val currentSnap = getSnapshot(appContext)
                persistDataStoreAsync(appContext, currentSnap)
            }

            if (!AppPreferences.isFirestoreSyncEnabled(appContext)) return@launch
            val userId = resolveQuotaUserId(appContext)
            if (userId.isEmpty()) return@launch

            synchronized(listenerLock) {
                if (firestoreListener != null && activeListeningUserId == userId) {
                    return@launch
                }
                firestoreListener?.remove()
                firestoreListener = null
                activeListeningUserId = userId
            }

            try {
                val docRef = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .collection("usageQuota")
                    .document("daily")

                val registration = docRef.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Firestore quota listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    // Handle snapshot merge off the main thread so lock/SharedPrefs never stall VSync
                    ledgerScope.launch {
                        if (snapshot == null || !snapshot.exists()) {
                            syncToFirestoreAsync(appContext, getSnapshot(appContext))
                            return@launch
                        }

                        val today = currentDateIso()
                        val remoteDate = snapshot.getString("dateIso") ?: ""
                        val local = getSnapshot(appContext)

                        if (remoteDate != today) {
                            syncToFirestoreAsync(appContext, local)
                        } else {
                            val remoteSummary = (snapshot.getLong("summaryUsed") ?: 0L).toInt().coerceAtLeast(0)
                            val remoteChat = (snapshot.getLong("chatUsed") ?: 0L).toInt().coerceAtLeast(0)
                            val remoteVoice = (snapshot.getLong("voiceUsed") ?: 0L).toInt().coerceAtLeast(0)
                            val remoteBonus = (snapshot.getLong("bonusGraceCredits") ?: 0L).toInt().coerceAtLeast(0)
                            val remoteGraceClaimed = snapshot.getBoolean("graceClaimed") ?: false
                            val remoteEco = snapshot.getBoolean("ecoLiteMode") ?: local.ecoLiteMode
                            val remoteCascade = (snapshot.getLong("fallbackCascadeCount") ?: 0L).toInt().coerceAtLeast(0)

                            val merged = QuotaSnapshot(
                                dateIso = today,
                                summaryUsed = max(local.summaryUsed, remoteSummary),
                                chatUsed = max(local.chatUsed, remoteChat),
                                voiceUsed = max(local.voiceUsed, remoteVoice),
                                bonusGraceCredits = max(local.bonusGraceCredits, remoteBonus),
                                graceClaimed = local.graceClaimed || remoteGraceClaimed,
                                ecoLiteMode = remoteEco,
                                fallbackCascadeCount = max(local.fallbackCascadeCount, remoteCascade),
                                isSupporter = DonationManager.isSupporter(appContext)
                            )
                            if (merged != local) {
                                persistLocalMirror(appContext, merged)
                            }
                        }
                    }
                }
                synchronized(listenerLock) {
                    if (activeListeningUserId == userId) {
                        firestoreListener = registration
                    } else {
                        registration.remove()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to attach Firestore quota listener: ${e.message}")
            }
        }
    }

    fun stopRealtimeSync() {
        synchronized(listenerLock) {
            firestoreListener?.remove()
            firestoreListener = null
            activeListeningUserId = null
        }
    }

    @Synchronized
    fun getSnapshot(context: Context): QuotaSnapshot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = currentDateIso()
        val storedDate = prefs.getString(KEY_DATE_ISO, "") ?: ""

        if (storedDate != today) {
            // Automatic Rollover Reset: reset daily counters & graceClaimed, preserve ecoLiteMode & supporter state
            val preservedEco = prefs.getBoolean(KEY_ECO_LITE_MODE, false)
            val cascadeCount = prefs.getInt(KEY_FALLBACK_CASCADE_COUNT, 0)
            val rolled = QuotaSnapshot(
                dateIso = today,
                summaryUsed = 0,
                chatUsed = 0,
                voiceUsed = 0,
                bonusGraceCredits = 0,
                graceClaimed = false,
                ecoLiteMode = preservedEco,
                fallbackCascadeCount = cascadeCount,
                isSupporter = DonationManager.isSupporter(context)
            )
            persistLocalMirror(context, rolled)
            return rolled
        }

        return QuotaSnapshot(
            dateIso = today,
            summaryUsed = prefs.getInt(KEY_SUMMARY_USED, 0),
            chatUsed = prefs.getInt(KEY_CHAT_USED, 0),
            voiceUsed = prefs.getInt(KEY_VOICE_USED, 0),
            bonusGraceCredits = prefs.getInt(KEY_BONUS_GRACE_CREDITS, 0),
            graceClaimed = prefs.getBoolean(KEY_GRACE_CLAIMED, false),
            ecoLiteMode = prefs.getBoolean(KEY_ECO_LITE_MODE, false),
            fallbackCascadeCount = prefs.getInt(KEY_FALLBACK_CASCADE_COUNT, 0),
            isSupporter = DonationManager.isSupporter(context)
        )
    }

    @Synchronized
    fun refreshSupporterAllowance(context: Context) {
        val snap = getSnapshot(context)
        persistLocalMirror(context, snap)
        AiHelper.clearCache()
    }

    /**
     * Determines which capability tier should run for the next request based on quota health and Eco Lite setting.
     */
    fun resolvePreferredTier(context: Context): AiModelConfig.CapabilityTier {
        val snap = getSnapshot(context)
        return when {
            snap.isExhausted -> AiModelConfig.CapabilityTier.TIER_3_LOCAL_ZERO_COST
            snap.ecoLiteMode || snap.isAtOrAboveSoftNudge -> AiModelConfig.CapabilityTier.TIER_2_ECONOMY_LITE
            else -> AiModelConfig.CapabilityTier.TIER_1_PRIMARY_CLOUD
        }
    }

    /**
     * Charges 1 unit only when a cloud AI call succeeds (Eco Lite charges 1 unit, Local Tier 3 charges 0).
     * Updates both Jetpack DataStore and Firestore (/users/{userId}/usageQuota/daily).
     */
    @Synchronized
    fun recordSuccess(context: Context, feature: AiFeature, usedTier: AiModelConfig.CapabilityTier) {
        if (usedTier == AiModelConfig.CapabilityTier.TIER_3_LOCAL_ZERO_COST) return
        val snap = getSnapshot(context)
        val updated = when (feature) {
            AiFeature.SUMMARY -> snap.copy(summaryUsed = snap.summaryUsed + 1)
            AiFeature.CHAT -> snap.copy(chatUsed = snap.chatUsed + 1)
            AiFeature.VOICE -> snap.copy(voiceUsed = snap.voiceUsed + 1)
        }
        persistLocalMirror(context, updated)
        syncToFirestoreAsync(context, updated)
    }

    /**
     * Refunds 1 unit if a previously charged interaction needs rollback.
     */
    @Synchronized
    fun refundInteraction(context: Context, feature: AiFeature) {
        val snap = getSnapshot(context)
        val updated = when (feature) {
            AiFeature.SUMMARY -> snap.copy(summaryUsed = (snap.summaryUsed - 1).coerceAtLeast(0))
            AiFeature.CHAT -> snap.copy(chatUsed = (snap.chatUsed - 1).coerceAtLeast(0))
            AiFeature.VOICE -> snap.copy(voiceUsed = (snap.voiceUsed - 1).coerceAtLeast(0))
        }
        persistLocalMirror(context, updated)
        syncToFirestoreAsync(context, updated)
    }

    @Synchronized
    fun recordFallbackCascade(context: Context) {
        val snap = getSnapshot(context)
        val updated = snap.copy(fallbackCascadeCount = snap.fallbackCascadeCount + 1)
        persistLocalMirror(context, updated)
        syncToFirestoreAsync(context, updated)
    }

    @Synchronized
    fun isEcoLiteMode(context: Context): Boolean {
        return getSnapshot(context).ecoLiteMode
    }

    @Synchronized
    fun setEcoLiteMode(context: Context, enabled: Boolean) {
        val snap = getSnapshot(context)
        val updated = snap.copy(ecoLiteMode = enabled)
        persistLocalMirror(context, updated)
        syncToFirestoreAsync(context, updated)
    }

    /**
     * Claims one-time +3 Free Grace Credits for the current day (Playbook Section 3, Stage 2 Suggestion 1).
     */
    @Synchronized
    fun claimDailyGraceCredits(context: Context): Boolean {
        val snap = getSnapshot(context)
        if (snap.graceClaimed) return false
        val updated = snap.copy(
            bonusGraceCredits = snap.bonusGraceCredits + GRACE_CREDIT_PACK_AMOUNT,
            graceClaimed = true
        )
        persistLocalMirror(context, updated)
        syncToFirestoreAsync(context, updated)
        AiHelper.clearCache()
        return true
    }

    /**
     * Returns hours and minutes until midnight local reset (Playbook Section 3, Stage 2 Suggestion 4).
     */
    fun getTimeUntilReset(
        nowMillis: Long = System.currentTimeMillis()
    ): Pair<Int, Int> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffMinutes = ((cal.timeInMillis - nowMillis) / 60_000L).coerceAtLeast(1L).toInt()
        val hours = diffMinutes / 60
        val minutes = diffMinutes % 60
        return hours to minutes
    }

    private fun persistLocalMirror(context: Context, snapshot: QuotaSnapshot) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DATE_ISO, snapshot.dateIso)
            .putInt(KEY_SUMMARY_USED, snapshot.summaryUsed)
            .putInt(KEY_CHAT_USED, snapshot.chatUsed)
            .putInt(KEY_VOICE_USED, snapshot.voiceUsed)
            .putInt(KEY_BONUS_GRACE_CREDITS, snapshot.bonusGraceCredits)
            .putBoolean(KEY_GRACE_CLAIMED, snapshot.graceClaimed)
            .putBoolean(KEY_ECO_LITE_MODE, snapshot.ecoLiteMode)
            .putInt(KEY_FALLBACK_CASCADE_COUNT, snapshot.fallbackCascadeCount)
            .apply()
        persistDataStoreAsync(appContext, snapshot)
    }

    private fun persistDataStoreAsync(context: Context, snapshot: QuotaSnapshot) {
        val appContext = context.applicationContext
        ledgerScope.launch {
            try {
                appContext.usageQuotaDataStore.edit { prefs ->
                    prefs[DS_DATE_ISO] = snapshot.dateIso
                    prefs[DS_SUMMARY_USED] = snapshot.summaryUsed
                    prefs[DS_CHAT_USED] = snapshot.chatUsed
                    prefs[DS_VOICE_USED] = snapshot.voiceUsed
                    prefs[DS_BONUS_GRACE_CREDITS] = snapshot.bonusGraceCredits
                    prefs[DS_GRACE_CLAIMED] = snapshot.graceClaimed
                    prefs[DS_ECO_LITE_MODE] = snapshot.ecoLiteMode
                    prefs[DS_FALLBACK_CASCADE_COUNT] = snapshot.fallbackCascadeCount
                    prefs[DS_IS_SUPPORTER] = snapshot.isSupporter
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update DataStore mirror: ${e.message}")
            }
        }
    }

    private fun syncToFirestoreAsync(context: Context, snapshot: QuotaSnapshot = getSnapshot(context)) {
        val appContext = context.applicationContext
        ledgerScope.launch {
            if (!AppPreferences.isFirestoreSyncEnabled(appContext)) return@launch
            val userId = resolveQuotaUserId(appContext)
            if (userId.isEmpty()) return@launch
            try {
                val payload = mapOf(
                    "userId" to userId,
                    "dateIso" to snapshot.dateIso,
                    "summaryUsed" to snapshot.summaryUsed,
                    "chatUsed" to snapshot.chatUsed,
                    "voiceUsed" to snapshot.voiceUsed,
                    "bonusGraceCredits" to snapshot.bonusGraceCredits,
                    "graceClaimed" to snapshot.graceClaimed,
                    "ecoLiteMode" to snapshot.ecoLiteMode,
                    "fallbackCascadeCount" to snapshot.fallbackCascadeCount,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .collection("usageQuota")
                    .document("daily")
                    .set(payload, SetOptions.merge())
            } catch (_: Exception) {
                // Best-effort cloud sync; local DataStore mirror remains authoritative offline
            }
        }
    }
}
