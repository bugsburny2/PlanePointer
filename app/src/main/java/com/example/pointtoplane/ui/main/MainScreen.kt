package com.example.pointtoplane.ui.main

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import com.example.pointtoplane.model.FlightInfo
import com.example.pointtoplane.model.OrientationState
import kotlin.math.cos
import kotlin.math.sin

// ── Design tokens ─────────────────────────────────────────────────────────────
private val ColorBackground    = Color(0xFF060612)
private val ColorAccentBlue    = Color(0xFF00D4FF)
private val ColorAccentAmber   = Color(0xFFFFB347)
private val ColorAccentGreen   = Color(0xFF00FF88)
private val ColorAccentOrange  = Color(0xFFFF6B35)
private val ColorTextPrimary   = Color(0xFFE8F4FD)
private val ColorTextSecondary = Color(0xFF7A9BB5)
private val ColorDimBlue       = Color(0xFF0D2033)

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val uiState     by viewModel.uiState.collectAsStateWithLifecycle()
    val orientation by viewModel.orientation.collectAsStateWithLifecycle()
    val calibratedElev by viewModel.correctedElevation.collectAsStateWithLifecycle()
    val calibratedAz   by viewModel.correctedAzimuth.collectAsStateWithLifecycle()
    val radarAircraft   by viewModel.radarAircraft.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier.fillMaxSize().background(ColorBackground),
        contentAlignment = Alignment.Center
    ) {
        when (val state = uiState) {
            is PlaneFinderUiState.GpsAcquiring  -> GpsAcquiringScreen()
            is PlaneFinderUiState.NeedCalibration -> NeedCalibrationScreen(
                onStart = { viewModel.startCalibration() }
            )
            is PlaneFinderUiState.Calibrating   -> CalibrationScreen(
                secondsLeft = state.secondsLeft,
                rawElevation = state.rawElevation,
                onCancel = { viewModel.cancelCalibration() }
            )
            else -> {
                MainPager(
                    uiState = state,
                    orientation = orientation,
                    calibratedElevation = calibratedElev,
                    calibratedAzimuth = calibratedAz,
                    radarAircraft = radarAircraft,
                    viewModel = viewModel
                )
            }
        }
    }
}

// ── GPS Acquiring Screen ──────────────────────────────────────────────────────

@Composable
private fun GpsAcquiringScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "gps")
    val ring1Alpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "ring1"
    )
    val ring2Alpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing, delayMillis = 500)),
        label = "ring2"
    )
    val ring3Alpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing, delayMillis = 1000)),
        label = "ring3"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val baseR = size.minDimension / 6
                listOf(ring1Alpha to 1f, ring2Alpha to 1.6f, ring3Alpha to 2.2f)
                    .forEach { (alpha, mult) ->
                        drawCircle(
                            color = Color(0xFF00D4FF).copy(alpha = alpha * 0.6f),
                            radius = baseR * mult,
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                // Centre dot
                drawCircle(Color(0xFF00D4FF), radius = baseR * 0.5f, center = center)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("Getting location…", fontSize = 13.sp, color = ColorTextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text("Waiting for GPS fix", fontSize = 10.sp, color = ColorTextSecondary, textAlign = TextAlign.Center)
    }
}

// ── Need Calibration Screen ───────────────────────────────────────────────────

@Composable
private fun NeedCalibrationScreen(onStart: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "cal_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.7f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "pulse"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("✈", fontSize = 28.sp, modifier = Modifier.alpha(pulse).rotate(-45f))
        Spacer(Modifier.height(8.dp))
        Text(
            text = "First-time setup",
            fontSize = 13.sp, color = ColorAccentBlue, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Point arm at the sky to calibrate",
            fontSize = 10.sp, color = ColorTextSecondary, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.size(width = 90.dp, height = 32.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF003344))
        ) {
            Text("Calibrate", fontSize = 11.sp, color = ColorAccentBlue)
        }
    }
}

@Composable
private fun CalibrationScreen(secondsLeft: Int, rawElevation: Float, onCancel: () -> Unit) {
    val isReady = rawElevation > 55f
    val elevColor = when {
        rawElevation > 75f -> ColorAccentGreen
        rawElevation > 55f -> ColorAccentAmber
        else               -> ColorTextSecondary
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Big countdown number
        Text(
            text = "$secondsLeft",
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            color = ColorAccentAmber,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Point arm straight\nat the sky!",
            fontSize = 12.sp, color = ColorTextPrimary,
            textAlign = TextAlign.Center, lineHeight = 16.sp
        )

        Spacer(Modifier.height(8.dp))

        // Live raw elevation readout
        Text(
            text = "Elevation: ${rawElevation.toInt()}°",
            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = elevColor
        )
        Text(
            text = if (isReady) "Hold steady..." else "Raise arm higher",
            fontSize = 10.sp, color = elevColor
        )

        Spacer(Modifier.height(10.dp))

        // Cancel / Abort button
        Button(
            onClick = onCancel,
            modifier = Modifier.size(width = 75.dp, height = 28.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF331A1A))
        ) {
            Text("Cancel", fontSize = 10.sp, color = Color(0xFFFF6666))
        }
    }
}

// ── Idle Screen ───────────────────────────────────────────────────────────────

@Composable
private fun IdleScreen(
    orientation: OrientationState,
    calibratedElevation: Float,
    onRecalibrate: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "idle")
    val rotationAnim by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "ring_rotation"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "pulse"
    )

    val isPointingUp = calibratedElevation > 10f

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRadarRing(rotationAnim, orientation.azimuthDeg)
                }
                Text(
                    text = "✈", fontSize = 32.sp,
                    color = ColorAccentBlue.copy(alpha = pulseAlpha),
                    modifier = Modifier.rotate(-45f)
                )
            }

            Spacer(Modifier.height(8.dp))

            Text("Point at a plane", fontSize = 13.sp, color = ColorTextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(2.dp))
            Text("Raise wrist · hold 2s", fontSize = 10.sp, color = ColorTextSecondary.copy(alpha = 0.6f), textAlign = TextAlign.Center)

            Spacer(Modifier.height(8.dp))

            // Calibrated elevation indicator
            Text(
                text = "${calibratedElevation.toInt()}° ↑",
                fontSize = 10.sp,
                color = if (isPointingUp) ColorAccentGreen else ColorTextSecondary,
                modifier = Modifier.alpha(if (isPointingUp) 1f else 0.5f)
            )
        }

        // Recalibrate button — enlarged for easy tapping on watch screens
        Box(
            modifier = Modifier.fillMaxSize().padding(top = 28.dp, end = 28.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Button(
                onClick = onRecalibrate,
                modifier = Modifier.size(38.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = ColorDimBlue.copy(alpha = 0.8f))
            ) {
                Text("⚙", fontSize = 18.sp, color = ColorTextPrimary)
            }
        }
    }
}

private fun DrawScope.drawRadarRing(rotationDeg: Float, azimuthDeg: Float) {
    val center = Offset(size.width / 2, size.height / 2)
    val radius = size.minDimension / 2 - 4.dp.toPx()

    drawCircle(color = Color(0xFF00D4FF).copy(alpha = 0.15f), radius = radius, style = Stroke(1.5.dp.toPx()))
    drawCircle(color = Color(0xFF00D4FF).copy(alpha = 0.08f), radius = radius * 0.6f, style = Stroke(1.dp.toPx()))

    rotate(rotationDeg, pivot = center) {
        drawArc(
            brush = Brush.sweepGradient(
                0f to Color.Transparent, 0.25f to Color(0xFF00D4FF).copy(alpha = 0.4f), 1f to Color.Transparent,
                center = center
            ),
            startAngle = -90f, sweepAngle = 90f, useCenter = true,
        )
    }

    listOf(0f, 90f, 180f, 270f).forEach { angleDeg ->
        val adjusted = Math.toRadians((angleDeg - azimuthDeg).toDouble())
        drawLine(
            color = Color(0xFF00D4FF).copy(alpha = 0.5f),
            start = Offset(center.x + (radius - 8.dp.toPx()) * sin(adjusted).toFloat(), center.y - (radius - 8.dp.toPx()) * cos(adjusted).toFloat()),
            end   = Offset(center.x + radius * sin(adjusted).toFloat(), center.y - radius * cos(adjusted).toFloat()),
            strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round
        )
    }

    val northAngleRad = Math.toRadians(-azimuthDeg.toDouble())
    drawCircle(
        color = Color(0xFFFF4444), radius = 3.dp.toPx(),
        center = Offset(center.x + (radius - 4.dp.toPx()) * sin(northAngleRad).toFloat(), center.y - (radius - 4.dp.toPx()) * cos(northAngleRad).toFloat())
    )
}

// ── Scanning Screen ───────────────────────────────────────────────────────────

@Composable
private fun ScanningScreen(
    calibratedElevation: Float,
    calibratedAzimuth: Float,
    orientation: OrientationState
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "ring"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val baseR = size.minDimension / 4
                drawCircle(Color(0xFF00D4FF).copy(alpha = ringAlpha * 0.6f), baseR * (1f + (1f - ringAlpha) * 1.5f), center, style = Stroke(2.dp.toPx()))
                drawCircle(Color(0xFF00D4FF), baseR * pulseScale, center, style = Stroke(2.dp.toPx()))
                drawLine(Color(0xFF00D4FF).copy(alpha = 0.5f), Offset(center.x - baseR * 1.5f, center.y), Offset(center.x + baseR * 1.5f, center.y), 1.dp.toPx())
                drawLine(Color(0xFF00D4FF).copy(alpha = 0.5f), Offset(center.x, center.y - baseR * 1.5f), Offset(center.x, center.y + baseR * 1.5f), 1.dp.toPx())
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Scanning sky…", fontSize = 13.sp, color = ColorAccentBlue, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text("${calibratedAzimuth.toInt()}° · ${calibratedElevation.toInt()}° ↑", fontSize = 10.sp, color = ColorTextSecondary)
    }
}

// ── Found Screen ──────────────────────────────────────────────────────────────

@Composable
private fun FoundScreen(flight: FlightInfo, onScanAgain: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text("✈", fontSize = 14.sp, color = ColorAccentBlue, modifier = Modifier.rotate(-45f).padding(end = 4.dp))
            Text(flight.displayCallsign, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ColorAccentBlue, letterSpacing = 1.5.sp)
        }
        Spacer(Modifier.height(3.dp))
        Text(flight.displayAircraftType, fontSize = 11.sp, color = ColorAccentAmber, textAlign = TextAlign.Center)
        if (flight.airline.isNotEmpty()) {
            Text(flight.airline, fontSize = 10.sp, color = ColorTextSecondary, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(8.dp))
        if (flight.hasRoute) RouteDisplay(flight)
        else Text("Route unknown", fontSize = 10.sp, color = ColorTextSecondary.copy(alpha = 0.6f))
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            DataChip("ALT", "${flight.altitudeFeet.formatWithComma()} ft")
            DataChip("SPD", "${flight.speedKnots.toInt()} kt")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onScanAgain, modifier = Modifier.size(width = 80.dp, height = 28.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = ColorDimBlue)) {
            Text("Again", fontSize = 10.sp, color = ColorAccentBlue)
        }
    }
}

@Composable
private fun RouteDisplay(flight: FlightInfo) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(flight.originIata, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorTextPrimary)
            if (flight.originName.isNotEmpty()) Text(flight.originName.take(10), fontSize = 8.sp, color = ColorTextSecondary)
        }
        Canvas(modifier = Modifier.weight(1f).height(20.dp).padding(horizontal = 4.dp)) {
            val y = size.height / 2; val startX = 8.dp.toPx(); val endX = size.width - 8.dp.toPx()
            var x = startX
            while (x < endX - 8.dp.toPx()) { drawLine(Color(0xFF00D4FF).copy(alpha = 0.5f), Offset(x, y), Offset(x + 6.dp.toPx(), y), 1.5.dp.toPx()); x += 10.dp.toPx() }
            drawLine(Color(0xFF00D4FF), Offset(endX - 6.dp.toPx(), y - 4.dp.toPx()), Offset(endX, y), 1.5.dp.toPx(), cap = StrokeCap.Round)
            drawLine(Color(0xFF00D4FF), Offset(endX - 6.dp.toPx(), y + 4.dp.toPx()), Offset(endX, y), 1.5.dp.toPx(), cap = StrokeCap.Round)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(flight.destinationIata, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorTextPrimary)
            if (flight.destinationName.isNotEmpty()) Text(flight.destinationName.take(10), fontSize = 8.sp, color = ColorTextSecondary)
        }
    }
}

@Composable
private fun DataChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(CircleShape).background(ColorDimBlue).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(label, fontSize = 8.sp, color = ColorTextSecondary, letterSpacing = 1.sp)
        Text(value, fontSize = 11.sp, color = ColorTextPrimary, fontWeight = FontWeight.Medium)
    }
}

// ── Not Found Screen ──────────────────────────────────────────────────────────

@Composable
private fun NotFoundScreen(onRetry: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("🔭", fontSize = 28.sp)
        Spacer(Modifier.height(8.dp))
        Text("No aircraft\ndetected", fontSize = 13.sp, color = ColorTextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text("Try pointing more\ndirectly at the plane", fontSize = 10.sp, color = ColorTextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBack, modifier = Modifier.size(width = 60.dp, height = 26.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = ColorDimBlue)) {
                Text("Back", fontSize = 9.sp, color = ColorTextSecondary)
            }
            Button(onClick = onRetry, modifier = Modifier.size(width = 60.dp, height = 26.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF003344))) {
                Text("Retry", fontSize = 9.sp, color = ColorAccentBlue)
            }
        }
    }
}

// ── Error Screen ──────────────────────────────────────────────────────────────

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("⚠", fontSize = 24.sp, color = ColorAccentAmber)
        Spacer(Modifier.height(6.dp))
        Text(message, fontSize = 11.sp, color = ColorTextSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry, modifier = Modifier.size(width = 70.dp, height = 28.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF331A00))) {
            Text("Retry", fontSize = 10.sp, color = ColorAccentAmber)
        }
    }
}

private fun Int.formatWithComma() = "%,d".format(this)

// ── Pager & Tabs ──────────────────────────────────────────────────────────────

@Composable
private fun MainPager(
    uiState: PlaneFinderUiState,
    orientation: OrientationState,
    calibratedElevation: Float,
    calibratedAzimuth: Float,
    radarAircraft: List<RadarPlane>,
    viewModel: MainViewModel
) {
    val pagerState = rememberPagerState(pageCount = { 2 })

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> {
                    when (uiState) {
                        is PlaneFinderUiState.Idle          -> IdleScreen(
                            orientation = orientation,
                            calibratedElevation = calibratedElevation,
                            onRecalibrate = { viewModel.recalibrate() }
                        )
                        is PlaneFinderUiState.Scanning      -> ScanningScreen(
                            calibratedElevation = calibratedElevation,
                            calibratedAzimuth = calibratedAzimuth,
                            orientation = orientation
                        )
                        is PlaneFinderUiState.Found         -> FoundScreen(
                            flight = uiState.flight,
                            onScanAgain = { viewModel.reset() }
                        )
                        is PlaneFinderUiState.NotFound      -> NotFoundScreen(
                            onRetry = { viewModel.forceRefresh() },
                            onBack  = { viewModel.reset() }
                        )
                        is PlaneFinderUiState.Error         -> ErrorScreen(
                            message = uiState.message,
                            onRetry = { viewModel.forceRefresh() }
                        )
                        else -> {}
                    }
                }
                1 -> {
                    val radarRange by viewModel.radarRangeKm.collectAsStateWithLifecycle()
                    RadarScreen(
                        orientation = orientation,
                        radarAircraft = radarAircraft,
                        rangeKm = radarRange,
                        onToggleRange = { viewModel.toggleRadarRange() }
                    )
                }
            }
        }

        HorizontalPageIndicator(
            pagerState = pagerState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)
        )
    }
}

@Composable
private fun HorizontalPageIndicator(
    pagerState: PagerState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(2) { index ->
            val isSelected = pagerState.currentPage == index
            val color = if (isSelected) ColorAccentBlue else ColorTextSecondary.copy(alpha = 0.4f)
            val size = if (isSelected) 6.dp else 4.dp
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

// ── Radar Screen ──────────────────────────────────────────────────────────────

@Composable
private fun RadarScreen(
    orientation: OrientationState,
    radarAircraft: List<RadarPlane>,
    rangeKm: Float,
    onToggleRange: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_sweep")
    val rotationAnim by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)),
        label = "sweep_rotation"
    )

    val textMeasurer = rememberTextMeasurer()
    val visibleAircraft = radarAircraft.filter { it.distanceKm <= rangeKm }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onToggleRange() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(130.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val maxRadius = size.minDimension / 2 - 4.dp.toPx()

                // 1. Radar background grid rings
                drawCircle(color = Color(0xFF00D4FF).copy(alpha = 0.12f), radius = maxRadius, style = Stroke(1.5.dp.toPx()))
                drawCircle(color = Color(0xFF00D4FF).copy(alpha = 0.08f), radius = maxRadius * 2 / 3, style = Stroke(1.dp.toPx()))
                drawCircle(color = Color(0xFF00D4FF).copy(alpha = 0.05f), radius = maxRadius / 3, style = Stroke(1.dp.toPx()))

                // 2. Heading ticks rotating with compass azimuth (Track-Up mode)
                val azimuth = orientation.azimuthDeg
                listOf(0f to "N", 90f to "E", 180f to "S", 270f to "W").forEach { (angle, label) ->
                    val angleRad = Math.toRadians((angle - azimuth).toDouble())
                    val startX = center.x + maxRadius * sin(angleRad).toFloat()
                    val startY = center.y - maxRadius * cos(angleRad).toFloat()
                    val endX = center.x + (maxRadius - 5.dp.toPx()) * sin(angleRad).toFloat()
                    val endY = center.y - (maxRadius - 5.dp.toPx()) * cos(angleRad).toFloat()

                    drawLine(
                        color = if (label == "N") Color(0xFFFF4444) else ColorAccentBlue.copy(alpha = 0.5f),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }

                // 3. Rotating radar sweep line with trailing gradient
                rotate(rotationAnim, pivot = center) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0f to Color.Transparent, 0.25f to Color(0xFF00D4FF).copy(alpha = 0.35f), 1f to Color.Transparent,
                            center = center
                        ),
                        startAngle = -90f, sweepAngle = 90f, useCenter = true
                    )
                }

                // 4. Center dot (user)
                drawCircle(ColorAccentBlue, radius = 3.dp.toPx(), center = center)
                drawCircle(ColorAccentBlue.copy(alpha = 0.2f), radius = 6.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))

                // 5. Plot aircraft dots within rangeKm
                visibleAircraft.forEach { plane ->
                    val normalizedDist = (plane.distanceKm / rangeKm).coerceAtMost(1f)
                    val r = normalizedDist * maxRadius
                    val relativeBearingRad = Math.toRadians((plane.bearingDeg - azimuth).toDouble())
                    val px = center.x + r * sin(relativeBearingRad).toFloat()
                    val py = center.y - r * cos(relativeBearingRad).toFloat()

                    // Glowing amber plane dot
                    drawCircle(ColorAccentAmber, radius = 3.dp.toPx(), center = Offset(px, py))
                    drawCircle(ColorAccentAmber.copy(alpha = 0.2f), radius = 6.dp.toPx(), center = Offset(px, py), style = Stroke(1.dp.toPx()))

                    // Heading indicator line
                    val headingRad = Math.toRadians((plane.trackDeg - azimuth).toDouble())
                    drawLine(
                        color = ColorAccentAmber.copy(alpha = 0.5f),
                        start = Offset(px, py),
                        end = Offset(
                            px + 7.dp.toPx() * sin(headingRad).toFloat(),
                            py - 7.dp.toPx() * cos(headingRad).toFloat()
                        ),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Draw flight callsign/number next to the dot
                    if (plane.callsign.isNotBlank()) {
                        drawText(
                            textMeasurer = textMeasurer,
                            text = plane.callsign.trim(),
                            style = TextStyle(
                                color = ColorAccentAmber,
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            topLeft = Offset(px + 5.dp.toPx(), py - 5.dp.toPx())
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Radar (${rangeKm.toInt()}km)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = ColorAccentBlue
        )
        Text(
            text = if (visibleAircraft.isEmpty()) "Searching for flights..." else "${visibleAircraft.size} flights nearby",
            fontSize = 9.sp,
            color = ColorTextSecondary
        )
    }
}
