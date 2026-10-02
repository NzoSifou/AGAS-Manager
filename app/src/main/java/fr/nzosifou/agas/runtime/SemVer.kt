package fr.nzosifou.agas.runtime

/** Numéro de version « 1.2.3 » ; un préfixe « v » et un suffixe (« -beta ») sont ignorés. */
data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int =
        compareValuesBy(this, other, SemVer::major, SemVer::minor, SemVer::patch)

    override fun toString() = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)(?:\.(\d+))?""", RegexOption.IGNORE_CASE)

        fun parse(text: String?): SemVer? {
            val m = PATTERN.find(text?.trim() ?: return null) ?: return null
            val (major, minor, patch) = m.destructured
            return SemVer(major.toInt(), minor.toInt(), patch.toIntOrNull() ?: 0)
        }
    }
}
