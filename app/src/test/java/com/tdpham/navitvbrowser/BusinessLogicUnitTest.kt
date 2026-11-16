package com.tdpham.navitvbrowser

import com.tdpham.navitvbrowser.util.AiModelConfig
import com.tdpham.navitvbrowser.util.AppPreferences
import com.tdpham.navitvbrowser.util.UsageQuotaManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class BusinessLogicUnitTest {

    @Test
    fun testSanitizeSyncUserId_removesPathSeparatorsAndCapsLength() {
        assertEquals("user_123_abc-def", AppPreferences.sanitizeSyncUserId(" user/123.abc-def "))
        assertEquals("___", AppPreferences.sanitizeSyncUserId("///"))
        val oversized = "a".repeat(100)
        assertEquals(64, AppPreferences.sanitizeSyncUserId(oversized).length)
        assertEquals("", AppPreferences.sanitizeSyncUserId("   "))
    }

    @Test
    fun testGetTimeUntilReset_computesHoursAndMinutesToMidnight() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 15)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val (hours, minutes) = UsageQuotaManager.getTimeUntilReset(cal.timeInMillis)
        assertEquals(1, hours)
        assertEquals(45, minutes)
    }

    @Test
    fun testQuotaSnapshot_freeSoftNudgeAndExhaustedStates() {
        val softNudgeSnapshot = UsageQuotaManager.QuotaSnapshot(
            dateIso = "2026-11-16",
            summaryUsed = 6,
            chatUsed = 4,
            voiceUsed = 2,
            bonusGraceCredits = 0,
            graceClaimed = false,
            ecoLiteMode = false,
            fallbackCascadeCount = 0,
            isSupporter = false
        )
        assertEquals(12, softNudgeSnapshot.totalUsed)
        assertEquals(UsageQuotaManager.DAILY_BASE_ALLOWANCE, softNudgeSnapshot.baseAllowance)
        assertEquals(15, softNudgeSnapshot.totalAllowance)
        assertEquals(3, softNudgeSnapshot.remainingCredits)
        assertEquals(0.8f, softNudgeSnapshot.usageRatio, 0.001f)
        assertTrue(softNudgeSnapshot.isAtOrAboveSoftNudge)
        assertFalse(softNudgeSnapshot.isExhausted)

        val freeExhausted = softNudgeSnapshot.copy(summaryUsed = 9)
        assertEquals(15, freeExhausted.totalUsed)
        assertEquals(0, freeExhausted.remainingCredits)
        assertTrue(freeExhausted.isExhausted)
        assertFalse(freeExhausted.isAtOrAboveSoftNudge)
    }

    @Test
    fun testQuotaSnapshot_supporterAllowanceAndGraceBonus() {
        val supporterSnapshot = UsageQuotaManager.QuotaSnapshot(
            dateIso = "2026-11-16",
            summaryUsed = 8,
            chatUsed = 2,
            voiceUsed = 0,
            bonusGraceCredits = UsageQuotaManager.GRACE_CREDIT_PACK_AMOUNT,
            graceClaimed = true,
            ecoLiteMode = false,
            fallbackCascadeCount = 1,
            isSupporter = true
        )
        assertEquals(10, supporterSnapshot.totalUsed)
        assertEquals(UsageQuotaManager.SUPPORTER_BASE_ALLOWANCE, supporterSnapshot.baseAllowance)
        assertEquals(33, supporterSnapshot.totalAllowance)
        assertEquals(23, supporterSnapshot.remainingCredits)
        assertFalse(supporterSnapshot.isExhausted)
        assertFalse(supporterSnapshot.isAtOrAboveSoftNudge)
    }

    @Test
    fun testAiModelConfig_defaultsAndLocalEngineId() {
        assertEquals("local-extractive-v3", AiModelConfig.LOCAL_DETERMINISTIC_ENGINE)
        assertTrue(AiModelConfig.DEFAULT_PRIMARY_REASONING_MODEL.isNotBlank())
        assertTrue(AiModelConfig.DEFAULT_ECONOMY_LITE_MODEL.isNotBlank())
    }
}
