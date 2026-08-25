package com.softdread.widgets.domain.selection

/**
 * Runtime variable substitution for Content Bible response strings.
 *
 * The Bible's rule is that `{braces}` mark runtime variables and the app
 * replaces them before rendering. This implementation adds the guarantee the
 * product requires: an unresolved placeholder never reaches the user. A response
 * whose required variables cannot all be resolved is filtered out during
 * selection; [interpolate] additionally strips anything that slipped through, so
 * the worst case is a slightly terser sentence rather than a visible `{percent}`.
 */
object Interpolation {

    private val PLACEHOLDER = Regex("""\{([a-z_]+)\}""")

    /** Variables referenced by [template]. */
    fun variablesIn(template: String): Set<String> =
        PLACEHOLDER.findAll(template).map { it.groupValues[1] }.toSet()

    /** True when every variable [template] needs has a non-blank value. */
    fun canResolve(template: String, variables: Map<String, String>): Boolean =
        variablesIn(template).all { !variables[it].isNullOrBlank() }

    /**
     * Substitutes [variables] into [template]. Any placeholder still unresolved
     * is removed and the surrounding whitespace and punctuation tidied, so the
     * output is always presentable prose.
     */
    fun interpolate(template: String, variables: Map<String, String>): String {
        val substituted = PLACEHOLDER.replace(template) { match ->
            variables[match.groupValues[1]]?.takeIf { it.isNotBlank() } ?: ""
        }
        return tidy(substituted)
    }

    private fun tidy(text: String): String = text
        .replace(Regex("""\s{2,}"""), " ")
        .replace(Regex("""\s+([,.;:%°])"""), "$1")
        .replace(Regex("""^[\s,.;:—-]+"""), "")
        .trim()
}
