package app.spamshield.core

/** What happens to a call. OFF means the rule is off / the call rings normally. */
enum class Action { OFF, SILENCE, BLOCK }

enum class NeighborMode { SAME_PREFIX, SAME_AREA_CODE }

/** Display order is the order the rules appear on the Rules tab. */
enum class Rule(val title: String, val description: String, val defaultAction: Action) {
    BLOCK_LIST(
        "Your block list",
        "Numbers and patterns you add on the Lists tab.",
        Action.BLOCK,
    ),
    FTC(
        "Reported to the FTC",
        "Numbers people have reported to the FTC's Do Not Call registry. The reports download to your phone daily, so checking a call never sends the number anywhere.",
        Action.BLOCK,
    ),
    SPOOFED(
        "Failed caller ID check",
        "Your carrier flagged the caller ID as fake (STIR/SHAKEN verification failed).",
        Action.BLOCK,
    ),
    NEIGHBOR(
        "Neighbor spoofing",
        "Calls faking a number that looks like yours, a common trick to get you to pick up.",
        Action.SILENCE,
    ),
    ONE_RING(
        "One-ring scam area codes",
        "Caribbean area codes like 876 and 809 that scammers use to get you to call back at international rates.",
        Action.SILENCE,
    ),
    HIDDEN(
        "Private or hidden numbers",
        "Callers who hide their caller ID.",
        Action.SILENCE,
    ),
    INTERNATIONAL(
        "International calls",
        "Calls from outside the US and Canada.",
        Action.OFF,
    ),
    COMMUNITY(
        "PhoneBlock community reports",
        "Looks up unknown callers in PhoneBlock's community spam database. It mostly covers European numbers.",
        Action.OFF,
    ),
    UNKNOWN(
        "Everyone not in your contacts",
        "Strictest setting: only your contacts and allow list ring normally.",
        Action.OFF,
    ),
}

enum class Verification { PASSED, FAILED, NOT_VERIFIED }

data class IncomingCall(
    /** Canonical number (see [PhoneNumbers.normalize]), or null when hidden. */
    val number: String?,
    val hidden: Boolean,
    val verification: Verification = Verification.NOT_VERIFIED,
)

data class ListMatch(val pattern: String, val label: String?)
data class FtcHit(val reports: Int, val lastDay: String, val subject: String?, val robocall: Boolean)
data class CommunityHit(val votes: Int, val rating: String?, val label: String?)

interface NumberSources {
    suspend fun allowListMatch(number: String): ListMatch?
    suspend fun blockListMatch(number: String): ListMatch?
    suspend fun ftcReports(number: String): FtcHit?
    suspend fun communityLookup(number: String): CommunityHit?
}

data class EngineConfig(
    val actions: Map<Rule, Action> = emptyMap(),
    val myNumber: String? = null,
    val neighborMode: NeighborMode = NeighborMode.SAME_PREFIX,
    val ftcMinReports: Int = 2,
    val communityMinVotes: Int = 3,
) {
    fun action(rule: Rule): Action = actions[rule] ?: rule.defaultAction
}

data class Verdict(val action: Action, val rule: Rule?, val reason: String)

/** NANP area codes outside the US/Canada that are common in one-ring ("wangiri") scams. */
val ONE_RING_AREA_CODES = setOf(
    "242", "246", "264", "268", "284", "345", "441", "473", "649", "658", "664",
    "721", "758", "767", "784", "809", "829", "849", "868", "869", "876",
)

class RulesEngine(private val sources: NumberSources) {

    private class Hit(val rule: Rule, val action: Action, val reason: String)

    /**
     * Every rule that matches is collected; the strongest action wins, and the
     * reason shown is the first (most specific) rule with that action.
     */
    suspend fun evaluate(call: IncomingCall, config: EngineConfig, useNetwork: Boolean = true): Verdict {
        val number = call.number
        if (call.hidden || number == null) {
            val action = config.action(Rule.HIDDEN)
            return Verdict(action, if (action == Action.OFF) null else Rule.HIDDEN, "Private or hidden number")
        }

        sources.allowListMatch(number)?.let { match ->
            val label = match.label?.let { " ($it)" }.orEmpty()
            return Verdict(Action.OFF, null, "On your allow list$label")
        }

        val hits = ArrayList<Hit>()
        fun enabled(rule: Rule) = config.action(rule) != Action.OFF
        fun add(rule: Rule, reason: String) {
            val action = config.action(rule)
            if (action != Action.OFF) hits += Hit(rule, action, reason)
        }

        if (enabled(Rule.BLOCK_LIST)) {
            sources.blockListMatch(number)?.let { match ->
                add(Rule.BLOCK_LIST, "On your block list" + (match.label?.let { " ($it)" } ?: ""))
            }
        }
        if (call.verification == Verification.FAILED) {
            add(Rule.SPOOFED, "Your carrier couldn't verify this caller ID")
        }
        if (enabled(Rule.FTC)) {
            sources.ftcReports(number)
                ?.takeIf { it.reports >= config.ftcMinReports }
                ?.let { add(Rule.FTC, describeFtc(it)) }
        }
        if (enabled(Rule.NEIGHBOR)) {
            neighborReason(number, config)?.let { add(Rule.NEIGHBOR, it) }
        }
        PhoneNumbers.areaCode(number)?.takeIf { it in ONE_RING_AREA_CODES }?.let {
            add(Rule.ONE_RING, "Area code $it is used in one-ring scams")
        }
        if (PhoneNumbers.isInternational(number)) {
            add(Rule.INTERNATIONAL, "International number")
        }
        // The only network lookup, so skip it when the call is already being blocked.
        if (useNetwork && enabled(Rule.COMMUNITY) && hits.none { it.action == Action.BLOCK }) {
            sources.communityLookup(number)
                ?.takeIf { it.votes >= config.communityMinVotes }
                ?.let { add(Rule.COMMUNITY, "Reported as spam ${it.votes} times on PhoneBlock") }
        }
        add(Rule.UNKNOWN, "Not in your contacts")

        val top = hits.maxByOrNull { it.action.ordinal }
            ?: return Verdict(Action.OFF, null, "No spam signals found")
        return Verdict(top.action, top.rule, top.reason)
    }

    private fun describeFtc(hit: FtcHit): String = buildString {
        append(if (hit.reports == 1) "Reported to the FTC once" else "Reported to the FTC ${hit.reports} times")
        hit.subject?.let { append(" · ").append(it) }
        if (hit.robocall) append(" · robocall")
    }

    private fun neighborReason(number: String, config: EngineConfig): String? {
        val mine = PhoneNumbers.normalize(config.myNumber)?.takeIf { PhoneNumbers.isNanp(it) } ?: return null
        if (!PhoneNumbers.isNanp(number)) return null
        if (number == mine) return "Caller ID shows your own number"
        return when (config.neighborMode) {
            NeighborMode.SAME_PREFIX ->
                if (number.take(6) == mine.take(6)) {
                    "Looks like your number (${number.take(3)}-${number.substring(3, 6)}-…)"
                } else null
            NeighborMode.SAME_AREA_CODE ->
                if (number.take(3) == mine.take(3)) "Same area code as you (${number.take(3)})" else null
        }
    }
}
