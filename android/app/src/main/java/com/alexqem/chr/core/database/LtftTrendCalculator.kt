package com.alexqem.chr.core.database

import java.util.Locale

object LtftTrendCalculator {

    fun formatTrendReport(stats: List<SessionLtftStat>): String {
        if (stats.isEmpty()) {
            return "No sessions in database yet. Run a capture session to track trends."
        }

        val sb = StringBuilder()
        sb.append("# LTFT trend (chr.db)\n\n")
        sb.append("| date | mode | start ECT | warm-up | LTFT mean | LTFT min..max | rows |\n")
        sb.append("|---|---|---|---|---|---|---|\n")

        val validMeans = mutableListOf<Double>()
        for (s in stats) {
            val dateStr = if (s.startedAt.length >= 16) s.startedAt.substring(0, 16).replace('T', ' ') else s.startedAt
            val startEct = if (s.startCoolantC != null) String.format(Locale.US, "%.1f°C", s.startCoolantC) else "—"
            val warmUp = if (s.warmAtS != null) String.format(Locale.US, "%.1f min", s.warmAtS / 60.0) else "—"

            if (s.countLtft == 0 || s.avgLtft == null) {
                sb.append("| $dateStr | ${s.mode} | $startEct | $warmUp | — | — | 0 |\n")
                continue
            }

            validMeans.add(s.avgLtft)
            val avg = String.format(Locale.US, "%+.1f%%", s.avgLtft)
            val minMax = String.format(Locale.US, "%+.1f%%…%+.1f%%", s.minLtft ?: 0.0, s.maxLtft ?: 0.0)
            sb.append("| $dateStr | ${s.mode} | $startEct | $warmUp | $avg | $minMax | ${s.countLtft} |\n")
        }

        if (validMeans.size >= 2) {
            val first = validMeans.first()
            val last = validMeans.last()
            val drift = last - first
            sb.append("\nLTFT drift: ")
            sb.append(String.format(Locale.US, "%+.1f%% → %+.1f%% (%+.1f pp over %d sessions)", first, last, drift, validMeans.size))
        }

        return sb.toString()
    }
}
