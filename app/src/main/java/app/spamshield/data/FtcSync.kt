package app.spamshield.data

import android.content.Context
import app.spamshield.SpamShieldApp
import app.spamshield.core.Action
import app.spamshield.core.FtcAggregate
import app.spamshield.core.FtcCsv
import app.spamshield.core.Rule
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Downloads the FTC's daily Do Not Call complaint files
 * (https://www.ftc.gov/policy-notices/open-government/data-sets/do-not-call-data)
 * and keeps the last N days on the phone, so calls are checked offline.
 */
object FtcSync {
    private val zone: ZoneId = ZoneId.of("America/New_York")
    private val dayLabel = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) SpamShield/1.0"

    private val _progress = MutableStateFlow<String?>(null)
    /** Non-null while a download is running. */
    val progress: StateFlow<String?> = _progress.asStateFlow()

    private val mutex = Mutex()

    /** The first day (inclusive) of a window of [days] days ending today. */
    fun cutoff(days: Int): String = LocalDate.now(zone).minusDays((days - 1).toLong()).toString()

    suspend fun run(context: Context, force: Boolean): Unit = mutex.withLock {
        val app = SpamShieldApp.get(context)
        val settings = app.settings.current()
        if (!force && settings.action(Rule.FTC) == Action.OFF) return@withLock

        val dao = app.db.ftc()
        val today = LocalDate.now(zone)
        val first = today.minusDays((settings.ftcDays - 1).toLong())
        val known = dao.days().associateBy { it.day }
        val now = System.currentTimeMillis()

        val pending = generateSequence(today) { it.minusDays(1) }
            .takeWhile { !it.isBefore(first) }
            .filter { known[it.toString()] == null }
            .toList()
        // Weekend complaints are published in Monday's file, so there are no weekend files.
        val (weekends, weekdays) = pending.partition {
            it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY
        }
        weekends.forEach { dao.upsertDay(FtcDay(it.toString(), FtcDay.MISSING, 0, now)) }

        var loaded = 0
        var failures = 0
        var lastError = ""
        try {
            weekdays.forEachIndexed { index, date ->
                val key = date.toString()
                _progress.value = "Downloading ${date.format(dayLabel)} (${index + 1} of ${weekdays.size})"
                try {
                    val data = download(key)
                    if (data == null) {
                        // Today's file appears around noon; older gaps are holidays.
                        if (date.isBefore(today.minusDays(4))) {
                            dao.upsertDay(FtcDay(key, FtcDay.MISSING, 0, now))
                        }
                    } else {
                        val rows = data.map { (number, agg) ->
                            FtcReport(number, key, agg.reports, agg.subject, agg.robocall)
                        }
                        dao.replaceDay(key, rows, FtcDay(key, FtcDay.LOADED, rows.size, now))
                        loaded++
                    }
                } catch (e: IOException) {
                    failures++
                    lastError = e.message ?: "network error"
                } catch (e: FtcCsv.FormatException) {
                    failures++
                    lastError = e.message ?: "unexpected file"
                }
            }
            dao.pruneReports(first.toString())
            dao.pruneDays(first.toString())
        } finally {
            _progress.value = null
        }

        val error = if (failures > 0) "Couldn't download $failures day(s): $lastError" else ""
        app.settings.setFtcResult(System.currentTimeMillis(), error)
        if (failures > 0 && loaded == 0) throw IOException(lastError)
        Unit
    }

    private suspend fun download(day: String): Map<String, FtcAggregate>? = withContext(Dispatchers.IO) {
        val url = URL("https://www.ftc.gov/sites/default/files/DNC_Complaint_Numbers_$day.csv")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "text/csv,*/*")
        try {
            when (val code = conn.responseCode) {
                200 -> conn.inputStream.bufferedReader().useLines { FtcCsv.parse(it) }
                404, 410 -> null
                else -> throw IOException("FTC site returned HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }
}
