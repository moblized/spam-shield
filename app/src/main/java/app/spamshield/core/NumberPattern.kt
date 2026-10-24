package app.spamshield.core

/**
 * A block- or allow-list entry: either an exact number or a wildcard pattern.
 *  - `*` matches any run of digits:  800*  →  every number starting with 800
 *  - `?` matches exactly one digit:  212555????
 */
class NumberPattern private constructor(
    /** What gets saved in the database. */
    val stored: String,
    private val exact: String?,
    private val regex: Regex?,
) {
    val isWildcard: Boolean get() = regex != null

    val display: String get() = exact?.let { PhoneNumbers.display(it) } ?: stored

    fun matches(number: String): Boolean =
        if (exact != null) exact == number
        else PhoneNumbers.variants(number).any { regex!!.matches(it) }

    companion object {
        fun parse(input: String): NumberPattern? {
            val cleaned = input.trim().filter { it.isDigit() || it == '*' || it == '?' || it == '+' }
            if (cleaned.none { it.isDigit() }) return null
            if (cleaned.lastIndexOf('+') > 0) return null

            val wildcard = cleaned.any { it == '*' || it == '?' }
            if (!wildcard) {
                val exact = PhoneNumbers.normalize(cleaned) ?: return null
                return NumberPattern(exact, exact, null)
            }

            val regex = buildString {
                append('^')
                for (c in cleaned) {
                    when (c) {
                        '*' -> append(".*")
                        '?' -> append('.')
                        '+' -> append("\\+")
                        else -> append(c)
                    }
                }
                append('$')
            }.toRegex()
            return NumberPattern(cleaned, null, regex)
        }
    }
}
