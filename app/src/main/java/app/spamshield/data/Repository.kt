package app.spamshield.data

import android.content.Context
import app.spamshield.core.Action
import app.spamshield.core.CommunityHit
import app.spamshield.core.FtcHit
import app.spamshield.core.IncomingCall
import app.spamshield.core.ListMatch
import app.spamshield.core.NumberPattern
import app.spamshield.core.NumberSources
import app.spamshield.core.PhoneNumbers
import app.spamshield.core.RulesEngine
import app.spamshield.core.Verdict
import app.spamshield.notify.Notifier
import kotlinx.coroutines.withTimeoutOrNull

class Repository(
    private val context: Context,
    val db: AppDatabase,
    val settings: SettingsStore,
) {

    suspend fun evaluate(call: IncomingCall, useNetwork: Boolean = true): Verdict {
        val s = settings.current()
        return RulesEngine(Sources(s)).evaluate(call, s.engineConfig(), useNetwork)
    }

    /** Logs a screened call and, if it was blocked, posts a notification. */
    suspend fun record(call: IncomingCall, verdict: Verdict) {
        val s = settings.current()
        if (verdict.action == Action.OFF && !s.logAllowed) return
        db.callLog().insert(
            LoggedCall(
                number = call.number,
                timestamp = System.currentTimeMillis(),
                action = verdict.action.name,
                rule = verdict.rule?.name,
                reason = verdict.reason,
            )
        )
        db.callLog().trim()
        if (verdict.action == Action.BLOCK && s.notifyOnBlock) {
            Notifier.blocked(context, call.number, verdict)
        }
    }

    /** Adds a number or pattern; returns false if it isn't valid. */
    suspend fun addEntry(kind: String, input: String, label: String?): Boolean {
        val pattern = NumberPattern.parse(input) ?: return false
        val other = if (kind == ListKind.BLOCK) ListKind.ALLOW else ListKind.BLOCK
        db.lists().deletePattern(other, pattern.stored)
        db.lists().insert(
            ListEntry(
                kind = kind,
                pattern = pattern.stored,
                label = label?.trim()?.ifBlank { null },
                createdAt = System.currentTimeMillis(),
            )
        )
        return true
    }

    suspend fun allowNumber(number: String) = addEntry(ListKind.ALLOW, number, null)
    suspend fun blockNumber(number: String) = addEntry(ListKind.BLOCK, number, null)

    private inner class Sources(private val s: AppSettings) : NumberSources {
        override suspend fun allowListMatch(number: String) = match(ListKind.ALLOW, number)
        override suspend fun blockListMatch(number: String) = match(ListKind.BLOCK, number)

        private suspend fun match(kind: String, number: String): ListMatch? =
            db.lists().all(kind).firstNotNullOfOrNull { entry ->
                NumberPattern.parse(entry.pattern)
                    ?.takeIf { it.matches(number) }
                    ?.let { ListMatch(entry.pattern, entry.label) }
            }

        override suspend fun ftcReports(number: String): FtcHit? {
            if (!PhoneNumbers.isNanp(number)) return null
            val since = FtcSync.cutoff(s.ftcDays)
            val dao = db.ftc()
            val count = dao.reportCount(number, since)
            if (count == 0) return null
            val latest = dao.latest(number, since)
            return FtcHit(count, latest?.day ?: since, latest?.subject, dao.anyRobocall(number, since))
        }

        override suspend fun communityLookup(number: String): CommunityHit? = withTimeoutOrNull(2_500) {
            runCatching { PhoneBlockClient.lookup(number, s.communityToken) }.getOrNull()
        }
    }
}
