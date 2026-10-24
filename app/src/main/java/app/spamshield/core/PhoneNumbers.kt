package app.spamshield.core

/**
 * Every number the app stores or compares is in one canonical form:
 *  - US / Canada (NANP): 10 digits, e.g. "2125551234"
 *  - other countries: "+" then digits, e.g. "+447700900123"
 *  - anything else (short codes): digits only
 */
object PhoneNumbers {

    fun normalize(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        var digits = text.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        var international = text.startsWith("+")
        if (!international && digits.startsWith("011") && digits.length > 7) {
            digits = digits.substring(3)
            international = true
        }
        if (international) {
            return if (digits.length == 11 && digits.startsWith("1")) digits.substring(1) else "+$digits"
        }
        if (digits.length == 11 && digits.startsWith("1")) return digits.substring(1)
        return digits
    }

    fun isNanp(number: String): Boolean =
        number.length == 10 && number.all { it.isDigit() } && number[0] in '2'..'9'

    fun isInternational(number: String): Boolean = number.startsWith("+")

    fun areaCode(number: String): String? = if (isNanp(number)) number.substring(0, 3) else null

    fun display(number: String?): String = when {
        number == null -> "Private number"
        isNanp(number) -> "(${number.substring(0, 3)}) ${number.substring(3, 6)}-${number.substring(6)}"
        else -> number
    }

    /** The ways a number can be written, so a pattern like 1800* or +1800* still matches. */
    fun variants(number: String): List<String> = when {
        isNanp(number) -> listOf(number, "1$number", "+1$number")
        isInternational(number) -> listOf(number, number.substring(1))
        else -> listOf(number)
    }
}
