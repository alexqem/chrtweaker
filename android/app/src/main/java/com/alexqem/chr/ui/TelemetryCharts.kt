package com.alexqem.chr.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexqem.chr.core.model.SensorSample
import com.alexqem.chr.ui.theme.*
import java.util.Locale

data class ChartSeries(
    val label: String,
    val color: Color,
    val values: List<Double?>
)

@Composable
fun TelemetryChartsSection(history: List<SensorSample>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "LIVE CHARTS",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = TextSecondary,
            modifier = Modifier.padding(top = 6.dp)
        )

        TelemetryChartCard(
            title = "Temperatures (°C)",
            series = listOf(
                ChartSeries("Coolant", CoolantBlue, history.map { it.coolant }),
                ChartSeries("Oil", OilAmber, history.map { it.oilTemp }),
                ChartSeries("IAT", IatGreen, history.map { it.iat })
            )
        )

        TelemetryChartCard(
            title = "Engine Load (RPM / MAF)",
            series = listOf(
                ChartSeries("RPM", RpmRed, history.map { it.rpm }),
                ChartSeries("MAF×100", MafBlue, history.map { it.maf?.times(100.0) })
            )
        )

        TelemetryChartCard(
            title = "Fuel Trims (%)",
            series = listOf(
                ChartSeries("STFT", StftRose, history.map { it.stft }),
                ChartSeries("LTFT", LtftOrange, history.map { it.ltft })
            )
        )

        TelemetryChartCard(
            title = "Catalyst (°C) & Battery (V×20)",
            series = listOf(
                ChartSeries("Cat", CatLime, history.map { it.catalyst }),
                ChartSeries("Volt×20", VoltTeal, history.map { it.voltage?.times(20.0) })
            )
        )
    }
}

@Composable
fun TelemetryChartCard(
    title: String,
    series: List<ChartSeries>
) {
    val allValues = series.flatMap { it.values }.filterNotNull()
    val minVal = allValues.minOrNull() ?: 0.0
    val maxVal = allValues.maxOrNull() ?: 1.0
    val range = (maxVal - minVal).takeIf { it > 1e-3 } ?: 1.0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
            .border(1.dp, BorderLine, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            if (allValues.isNotEmpty()) {
                Text(
                    text = String.format(Locale.US, "%.1f .. %.1f", minVal, maxVal),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = ChartTick
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Legend
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            series.forEach { s ->
                val latest = s.values.lastOrNull { it != null }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(s.color)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (latest != null) {
                            "${s.label}: ${String.format(Locale.US, "%.1f", latest)}"
                        } else {
                            s.label
                        },
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (allValues.size < 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(CardBgElevated, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Waiting for telemetry samples...",
                    fontSize = 11.sp,
                    color = ChartTick
                )
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                val w = size.width
                val h = size.height
                val gridLines = 3
                for (i in 0..gridLines) {
                    val y = h * i / gridLines.toFloat()
                    drawLine(
                        color = ChartGrid,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                series.forEach { s ->
                    val count = s.values.size
                    if (count >= 2) {
                        val path = Path()
                        var started = false
                        s.values.forEachIndexed { idx, v ->
                            if (v != null) {
                                val x = (idx.toFloat() / (count - 1).coerceAtLeast(1)) * w
                                val norm = ((v - minVal) / range).toFloat().coerceIn(0f, 1f)
                                val y = h - norm * (h - 8f) - 4f
                                if (!started) {
                                    path.moveTo(x, y)
                                    started = true
                                } else {
                                    path.lineTo(x, y)
                                }
                            } else {
                                started = false
                            }
                        }
                        drawPath(
                            path = path,
                            color = s.color,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }
    }
}
