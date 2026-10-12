package io.github.wkoller.glucosetracker.domain.dosing

/**
 * One row of a sliding scale. [upperTenths] is the highest reading in the band, in tenths of mmol/L,
 * or null for the open top band (for example "above 20").
 */
data class ScaleBand(val upperTenths: Int?, val units: Int)

sealed interface ScaleProblem {
    data object Empty : ScaleProblem
    data object LastBandNotOpen : ScaleProblem
    data class OpenBandNotLast(val index: Int) : ScaleProblem
    data class NotAscending(val index: Int) : ScaleProblem
    data class UpperOutOfRange(val index: Int) : ScaleProblem
    data class UnitsOutOfRange(val index: Int) : ScaleProblem
}

/**
 * A sliding scale for a correction insulin. A reading falls in the first band whose upper limit is
 * greater than or equal to it, which matches the paper order (0–10, 10.1–12, ...) and the
 * spreadsheet formula IFS(F<=10,0,F<=12,2,...).
 */
data class SlidingScale(val bands: List<ScaleBand>) {

    fun problems(): List<ScaleProblem> {
        if (bands.isEmpty()) return listOf(ScaleProblem.Empty)
        val problems = mutableListOf<ScaleProblem>()
        bands.forEachIndexed { index, band ->
            val upper = band.upperTenths
            if (upper == null && index != bands.lastIndex) problems += ScaleProblem.OpenBandNotLast(index)
            if (upper != null && upper !in 1..MAX_UPPER_TENTHS) problems += ScaleProblem.UpperOutOfRange(index)
            if (band.units !in 0..MAX_UNITS) problems += ScaleProblem.UnitsOutOfRange(index)
            val previousUpper = bands.getOrNull(index - 1)?.upperTenths
            if (upper != null && previousUpper != null && upper <= previousUpper) {
                problems += ScaleProblem.NotAscending(index)
            }
        }
        if (bands.last().upperTenths != null) problems += ScaleProblem.LastBandNotOpen
        return problems
    }

    val isValid: Boolean get() = problems().isEmpty()

    fun bandIndexFor(tenths: Int): Int {
        check(isValid) { "The sliding scale is not valid: ${problems()}" }
        return bands.indexOfFirst { it.upperTenths == null || tenths <= it.upperTenths }
    }

    fun unitsFor(tenths: Int): Int = bands[bandIndexFor(tenths)].units

    /** The lowest reading in a band: 0 for the first band, otherwise 0.1 above the previous band. */
    fun lowerTenths(index: Int): Int =
        if (index == 0) 0 else (bands[index - 1].upperTenths ?: error("Open band before index $index")) + 1

    fun isTopBand(index: Int): Boolean = index == bands.lastIndex

    companion object {
        const val MAX_UNITS = 50
        const val MAX_UPPER_TENTHS = 333

        /** A common starting point. The user must check it against their own order before it is saved. */
        val COMMON = SlidingScale(
            listOf(
                ScaleBand(100, 0),
                ScaleBand(120, 2),
                ScaleBand(140, 4),
                ScaleBand(170, 6),
                ScaleBand(200, 8),
                ScaleBand(null, 10),
            ),
        )
    }
}
