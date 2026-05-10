package io.github.openpause.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class RuleConstraintsTest {
    @Test
    fun sanitizeWaitSeconds_clampsToSupportedRange() {
        assertEquals(0, RuleConstraints.sanitizeWaitSeconds(-1))
        assertEquals(45, RuleConstraints.sanitizeWaitSeconds(45))
        assertEquals(RuleConstraints.MaxWaitSeconds, RuleConstraints.sanitizeWaitSeconds(999))
    }

    @Test
    fun sanitizeUsageMinutes_clampsToSupportedRange() {
        assertEquals(1, RuleConstraints.sanitizeUsageMinutes(0))
        assertEquals(30, RuleConstraints.sanitizeUsageMinutes(30))
        assertEquals(RuleConstraints.MaxUsageMinutes, RuleConstraints.sanitizeUsageMinutes(999))
    }
}
