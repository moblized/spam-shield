package app.spamshield.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersTest {
    @Test
    fun normalizesUsNumbers() {
        assertEquals("2125551234", PhoneNumbers.normalize("+1 (212) 555-1234"))
        assertEquals("2125551234", PhoneNumbers.normalize("12125551234"))
        assertEquals("2125551234", PhoneNumbers.normalize("212.555.1234"))
    }

    @Test
    fun normalizesInternationalNumbers() {
        assertEquals("+447700900123", PhoneNumbers.normalize("+44 7700 900123"))
        assertEquals("+447700900123", PhoneNumbers.normalize("011 44 7700 900123"))
    }

    @Test
    fun rejectsEmpty() {
        assertNull(PhoneNumbers.normalize(""))
        assertNull(PhoneNumbers.normalize("Unknown"))
        assertNull(PhoneNumbers.normalize(null))
    }

    @Test
    fun displaysNanp() {
        assertEquals("(212) 555-1234", PhoneNumbers.display("2125551234"))
        assertEquals("Private number", PhoneNumbers.display(null))
    }
}

class NumberPatternTest {
    @Test
    fun exactNumberMatchesAnyFormatting() {
        val p = NumberPattern.parse("(212) 555-1234")!!
        assertFalse(p.isWildcard)
        assertEquals("2125551234", p.stored)
        assertTrue(p.matches("2125551234"))
        assertFalse(p.matches("2125551235"))
    }

    @Test
    fun prefixPatterns() {
        assertTrue(NumberPattern.parse("800*")!!.matches("8005551234"))
        assertTrue(NumberPattern.parse("1800*")!!.matches("8005551234"))
        assertTrue(NumberPattern.parse("+1800*")!!.matches("8005551234"))
        assertFalse(NumberPattern.parse("800*")!!.matches("2128005555"))
    }

    @Test
    fun suffixAndSingleDigitPatterns() {
        assertTrue(NumberPattern.parse("*1234")!!.matches("3105551234"))
        assertTrue(NumberPattern.parse("212555????")!!.matches("2125559876"))
        assertFalse(NumberPattern.parse("212555????")!!.matches("21255598765"))
    }

    @Test
    fun internationalPatterns() {
        assertTrue(NumberPattern.parse("+44*")!!.matches("+447700900123"))
        assertFalse(NumberPattern.parse("+44*")!!.matches("4405551234"))
    }

    @Test
    fun rejectsPatternsWithoutDigits() {
        assertNull(NumberPattern.parse("*"))
        assertNull(NumberPattern.parse("abc"))
        assertNull(NumberPattern.parse("12+3"))
    }
}

class FtcCsvTest {
    private val sample = """
        Company_Phone_Number,Created_Date,Violation_Date,Consumer_City,Consumer_State,Consumer_Area_Code,Subject,Recorded_Message_Or_Robocall
        5625743803,2026-10-22 00:02:18,2026-10-21 18:21:00,Redondo Beach,California,310,Dropped call or no message,N
        9409991876,2026-10-22 00:02:23,2026-10-21 14:55:00,Houston,Texas,660,"Reducing your debt (credit cards, mortgage, student loans)",Y
        5625743803,2026-10-22 00:05:00,2026-10-21 18:30:00,Torrance,California,310,Imposters,N
        0,2026-10-22 00:06:00,2026-10-21 18:30:00,Torrance,California,310,Other,N
        95149990443,2026-10-22 00:07:00,2026-10-21 18:30:00,Torrance,California,310,Other,N
        ,2026-10-22 00:08:00,2026-10-21 18:30:00,Torrance,California,310,Other,N
    """.trimIndent()

    @Test
    fun aggregatesReportsPerNumber() {
        val result = FtcCsv.parse(sample.lineSequence())
        assertEquals(setOf("5625743803", "9409991876"), result.keys)
        assertEquals(2, result["5625743803"]!!.reports)
        assertEquals("Imposters", result["5625743803"]!!.subject)
        assertEquals("Reducing your debt (credit cards, mortgage, student loans)", result["9409991876"]!!.subject)
        assertTrue(result["9409991876"]!!.robocall)
        assertFalse(result["5625743803"]!!.robocall)
    }

    @Test
    fun splitsQuotedFields() {
        assertEquals(listOf("a", "b, c", "d \"e\""), FtcCsv.splitLine("a,\"b, c\",\"d \"\"e\"\"\""))
    }

    @Test(expected = FtcCsv.FormatException::class)
    fun rejectsUnexpectedFiles() {
        FtcCsv.parse(sequenceOf("<html>", "<body>Access denied</body>"))
    }
}

class RulesEngineTest {

    private class FakeSources(
        val allow: Set<String> = emptySet(),
        val block: List<String> = emptyList(),
        val ftc: Map<String, Int> = emptyMap(),
        val community: Map<String, Int> = emptyMap(),
    ) : NumberSources {
        var communityCalls = 0
        override suspend fun allowListMatch(number: String) =
            if (number in allow) ListMatch(number, "Mom's work") else null
        override suspend fun blockListMatch(number: String) =
            block.firstOrNull { NumberPattern.parse(it)!!.matches(number) }?.let { ListMatch(it, null) }
        override suspend fun ftcReports(number: String) =
            ftc[number]?.let { FtcHit(it, "2026-10-23", "Imposters", robocall = true) }
        override suspend fun communityLookup(number: String): CommunityHit? {
            communityCalls++
            return community[number]?.let { CommunityHit(it, "E_ADVERTISING", null) }
        }
    }

    private fun evaluate(
        number: String?,
        sources: FakeSources = FakeSources(),
        config: EngineConfig = EngineConfig(),
        hidden: Boolean = number == null,
        verification: Verification = Verification.NOT_VERIFIED,
    ) = runBlocking { RulesEngine(sources).evaluate(IncomingCall(number, hidden, verification), config) }

    @Test
    fun hiddenNumbersAreSilencedByDefault() {
        val v = evaluate(null)
        assertEquals(Action.SILENCE, v.action)
        assertEquals(Rule.HIDDEN, v.rule)
    }

    @Test
    fun ordinaryNumbersRing() {
        val v = evaluate("2125551234")
        assertEquals(Action.OFF, v.action)
        assertNull(v.rule)
    }

    @Test
    fun ftcReportsBlockAboveThreshold() {
        val sources = FakeSources(ftc = mapOf("2125551234" to 3, "2125550000" to 1))
        val blocked = evaluate("2125551234", sources)
        assertEquals(Action.BLOCK, blocked.action)
        assertEquals(Rule.FTC, blocked.rule)
        assertEquals("Reported to the FTC 3 times · Imposters · robocall", blocked.reason)
        assertEquals(Action.OFF, evaluate("2125550000", sources).action)
    }

    @Test
    fun allowListBeatsEverything() {
        val sources = FakeSources(allow = setOf("2125551234"), block = listOf("212*"), ftc = mapOf("2125551234" to 9))
        val v = evaluate("2125551234", sources)
        assertEquals(Action.OFF, v.action)
        assertEquals("On your allow list (Mom's work)", v.reason)
    }

    @Test
    fun blockListPatterns() {
        val v = evaluate("8885551234", FakeSources(block = listOf("888*")))
        assertEquals(Action.BLOCK, v.action)
        assertEquals(Rule.BLOCK_LIST, v.rule)
    }

    @Test
    fun failedVerificationIsBlocked() {
        val v = evaluate("2125551234", verification = Verification.FAILED)
        assertEquals(Rule.SPOOFED, v.rule)
        assertEquals(Action.BLOCK, v.action)
    }

    @Test
    fun neighborSpoofing() {
        val config = EngineConfig(myNumber = "(704) 555-0199")
        assertEquals(Rule.NEIGHBOR, evaluate("7045551234", config = config).rule)
        assertEquals(Action.SILENCE, evaluate("7045551234", config = config).action)
        assertNull(evaluate("7045561234", config = config).rule)
        assertEquals("Caller ID shows your own number", evaluate("7045550199", config = config).reason)

        val wide = config.copy(neighborMode = NeighborMode.SAME_AREA_CODE)
        assertEquals(Rule.NEIGHBOR, evaluate("7049991234", config = wide).rule)
    }

    @Test
    fun oneRingAreaCodes() {
        val v = evaluate("8765551234")
        assertEquals(Rule.ONE_RING, v.rule)
        assertEquals(Action.SILENCE, v.action)
    }

    @Test
    fun strongestActionWinsAndFirstRuleExplainsIt() {
        // Neighbor (silence) and FTC (block) both match: block wins.
        val sources = FakeSources(ftc = mapOf("7045551234" to 5))
        val v = evaluate("7045551234", sources, EngineConfig(myNumber = "7045550199"))
        assertEquals(Action.BLOCK, v.action)
        assertEquals(Rule.FTC, v.rule)
    }

    @Test
    fun unknownCallerRuleExplainsLast() {
        val config = EngineConfig(actions = mapOf(Rule.UNKNOWN to Action.SILENCE))
        assertEquals(Rule.UNKNOWN, evaluate("2125551234", config = config).rule)
        // A more specific rule at the same strength gets the credit.
        assertEquals(Rule.ONE_RING, evaluate("8765551234", config = config).rule)
    }

    @Test
    fun communityLookupOnlyWhenNeeded() {
        val sources = FakeSources(ftc = mapOf("2125551234" to 5), community = mapOf("2125559999" to 7))
        val config = EngineConfig(actions = mapOf(Rule.COMMUNITY to Action.SILENCE))

        assertEquals(Rule.FTC, evaluate("2125551234", sources, config).rule)
        assertEquals(0, sources.communityCalls)

        val v = evaluate("2125559999", sources, config)
        assertEquals(Rule.COMMUNITY, v.rule)
        assertEquals(1, sources.communityCalls)
    }

    @Test
    fun disabledRulesDoNothing() {
        val config = EngineConfig(actions = Rule.entries.associateWith { Action.OFF })
        val sources = FakeSources(block = listOf("876*"), ftc = mapOf("8765551234" to 10))
        assertEquals(Action.OFF, evaluate("8765551234", sources, config).action)
        assertEquals(Action.OFF, evaluate(null, sources, config).action)
    }
}
