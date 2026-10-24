package app.spamshield.core

data class FtcAggregate(val reports: Int, val subject: String?, val robocall: Boolean)

/**
 * Parses one of the FTC's daily "DNC_Complaint_Numbers_YYYY-MM-DD.csv" files:
 * Company_Phone_Number,Created_Date,Violation_Date,Consumer_City,Consumer_State,
 * Consumer_Area_Code,Subject,Recorded_Message_Or_Robocall
 */
object FtcCsv {

    class FormatException(message: String) : Exception(message)

    fun parse(lines: Sequence<String>): Map<String, FtcAggregate> {
        val iterator = lines.iterator()
        if (!iterator.hasNext()) return emptyMap()

        val header = splitLine(iterator.next().removePrefix("﻿")).map { it.trim().lowercase() }
        val numberCol = header.indexOf("company_phone_number")
        if (numberCol < 0) throw FormatException("The FTC file wasn't in the expected format")
        val subjectCol = header.indexOf("subject")
        val robocallCol = header.indexOf("recorded_message_or_robocall")

        val counts = HashMap<String, MutableAggregate>()
        while (iterator.hasNext()) {
            val line = iterator.next()
            if (line.isBlank()) continue
            val fields = splitLine(line)
            val number = PhoneNumbers.normalize(fields.getOrNull(numberCol)) ?: continue
            if (!PhoneNumbers.isNanp(number)) continue

            val subject = fields.getOrNull(subjectCol)?.trim()?.takeIf { it.isNotEmpty() }
            val robocall = fields.getOrNull(robocallCol)?.trim().equals("Y", ignoreCase = true)
            val agg = counts.getOrPut(number) { MutableAggregate() }
            agg.reports++
            if (subject != null) agg.subject = subject
            agg.robocall = agg.robocall || robocall
        }
        return counts.mapValues { (_, a) -> FtcAggregate(a.reports, a.subject, a.robocall) }
    }

    /** Splits one CSV line, honouring double quotes and "" escapes. */
    fun splitLine(line: String): List<String> {
        val fields = ArrayList<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields += current.toString()
                    current.setLength(0)
                }
                else -> current.append(c)
            }
            i++
        }
        fields += current.toString()
        return fields
    }

    private class MutableAggregate(var reports: Int = 0, var subject: String? = null, var robocall: Boolean = false)
}
