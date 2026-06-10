package today.superb.jvl.ui.radar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import today.superb.jvl.core.GameState
import today.superb.jvl.core.Peer
import today.superb.jvl.ui.text.MonoText
import today.superb.jvl.ui.theme.LocalDisplayFont
import today.superb.jvl.ui.theme.LocalMonoFont
import today.superb.jvl.ui.theme.LocalPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

// 데모 radar.jsx 상수 1:1.
private const val SWEEP_S = 6f      // 1회전 시간(초)
private const val CONE_DEG = 50f    // 스윕 선행 엣지 뒤 조명 콘 각도
private const val DECAY_S = 1.6f    // 블립 phosphor 감쇠 시상수
private const val RINGS = 4

/** 방위(0°=N, 시계방향) → 캔버스 각(라디안, 0=오른쪽). 데모 `bearingToCanvas` 1:1. */
private fun bearingToCanvas(bearing: Float): Float = (bearing - 90f) * (PI.toFloat() / 180f)

/**
 * 레이더 스코프 — 데모 `RadarScreen` 충실 포팅.
 *
 * 회전 스윕 암(6s/rev) + 50° 조명 콘 + 피어 블립(스윕에 닿으면 켜지고 1.6s exp 감쇠). 동심원 4·
 * 30° 스포크·3° 외곽 눈금·NESW 십자선·중심 self 점(맥동). frame-state(스윕 위치·블립 잔광)는
 * GameState 밖에서 [withFrameNanos]로 자율 동작하고, 피어 위치만 GameState에서 읽는다([DotCreatureCanvas] 패턴).
 * 블립 탭 = 선택 + `bond <name>` 매크로(App이 배선). 선택된 블립은 잔광과 무관한 타깃 링.
 * 빈 영역 탭 = 선택 해제.
 */
@Composable
fun RadarScreen(
    state: GameState,
    selectedPeerId: String? = null,
    onSelectPeer: (Peer?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val incoming = state.pendingRequest?.let { req -> state.peers.find { it.id == req.from } }

    Column(modifier.fillMaxSize().padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MonoText("LRRS-RADAR · CH.07", color = palette.phos, fontFamily = LocalDisplayFont.current, fontSize = 15.sp)
            MonoText("SWEEP 6s/rev", color = palette.phosDim, fontSize = 10.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            MonoText("CONTACTS ${state.peers.size}", color = palette.phosMid, fontSize = 10.sp)
            MonoText(
                if (incoming != null) "${incoming.name} HAILS" else "● BROADCASTING",
                color = if (incoming != null) palette.phos else palette.phosMid,
                fontSize = 10.sp,
            )
        }

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            RadarScope(state.peers, selectedPeerId, onSelectPeer, Modifier.fillMaxSize())

            // 나침반 라벨 — 캔버스 텍스트 대신 정렬 오버레이로(공통 Compose에서 안전).
            MonoText("N", Modifier.align(Alignment.TopCenter), color = palette.phosMid, fontSize = 11.sp)
            MonoText("S", Modifier.align(Alignment.BottomCenter), color = palette.phosMid, fontSize = 11.sp)
            MonoText("W", Modifier.align(Alignment.CenterStart), color = palette.phosMid, fontSize = 11.sp)
            MonoText("E", Modifier.align(Alignment.CenterEnd), color = palette.phosMid, fontSize = 11.sp)

            state.toast?.let { toast ->
                Box(
                    Modifier.align(Alignment.TopCenter).padding(top = 8.dp).background(palette.phosDim),
                ) {
                    MonoText(
                        "▸ $toast",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = palette.phos,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        fontFamily = LocalDisplayFont.current,
                    )
                }
            }
        }
    }
}

@Composable
private fun RadarScope(
    peers: List<Peer>,
    selectedPeerId: String?,
    onSelectPeer: (Peer?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val measurer = rememberTextMeasurer()
    val labelFont = LocalMonoFont.current
    val curPeers by rememberUpdatedState(peers)

    val litTime = remember { mutableMapOf<String, Float>() } // peerId → 마지막으로 스윕에 닿은 t
    var renderT by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            val nanos = withFrameNanos { it }
            val t = (nanos - start) / 1_000_000_000f
            val sweepBearing = (t * (360f / SWEEP_S)) % 360f
            for (p in curPeers) {
                var diff = (sweepBearing - p.bearing) % 360f
                if (diff < 0f) diff += 360f
                if (diff <= CONE_DEG) litTime[p.id] = t
            }
            renderT = t
        }
    }

    Canvas(
        modifier.pointerInput(Unit) {
            detectTapGestures { tap ->
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = minOf(size.width, size.height) / 2f - 16f
                if (r <= 0f) return@detectTapGestures
                val hitRadius = 22.dp.toPx()   // 최소 44dp 히트 타깃의 반경
                val t = renderT
                // 보이는(잔광 살아있는) 블립 중 탭에 가장 가까운 것.
                val hit = curPeers
                    .filter { p -> exp(-(t - (litTime[p.id] ?: -10f)) / DECAY_S) > 0.04f }
                    .map { p ->
                        val a = bearingToCanvas(p.bearing)
                        val pos = Offset(cx + cos(a) * p.range * r, cy + sin(a) * p.range * r)
                        p to (pos - tap).getDistance()
                    }
                    .filter { (_, d) -> d <= hitRadius }
                    .minByOrNull { (_, d) -> d }
                    ?.first
                onSelectPeer(hit)
            }
        },
    ) {
        val t = renderT  // snapshot 읽기로 매 프레임 재그리기 구동.
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(size.width, size.height) / 2f - 16f
        if (r <= 0f) return@Canvas

        fun polar(bearing: Float, radius: Float): Offset {
            val a = bearingToCanvas(bearing)
            return Offset(cx + cos(a) * radius, cy + sin(a) * radius)
        }

        // 동심원
        for (i in 1..RINGS) {
            drawCircle(palette.phosDim, radius = r * i / RINGS, center = Offset(cx, cy), style = Stroke(0.8f), alpha = 0.6f)
        }
        // 30° 스포크
        var deg = 0f
        while (deg < 360f) {
            drawLine(palette.phosGrid, Offset(cx, cy), polar(deg, r), strokeWidth = 0.6f)
            deg += 30f
        }
        // N/E/S/W 십자선 (스포크보다 밝게)
        drawLine(palette.phosMid, Offset(cx - r, cy), Offset(cx + r, cy), strokeWidth = 1f, alpha = 0.7f)
        drawLine(palette.phosMid, Offset(cx, cy - r), Offset(cx, cy + r), strokeWidth = 1f, alpha = 0.7f)

        // 스윕 콘 — trail→leading edge를 얇은 부채꼴로 적층(데모 18스텝 conic 근사).
        val sweepBearing = (t * (360f / SWEEP_S)) % 360f
        val sweepDeg = sweepBearing - 90f
        val trailDeg = sweepDeg - CONE_DEG
        val steps = 18
        for (i in 0 until steps) {
            val a0 = trailDeg + (CONE_DEG * i) / steps
            val frac = i.toFloat() / steps
            val alpha = 0.05f + frac * frac * 0.5f
            drawArc(
                color = palette.phos,
                startAngle = a0,
                sweepAngle = CONE_DEG.toFloat() / steps,
                useCenter = true,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2f, r * 2f),
                alpha = alpha,
            )
        }
        // 선행 엣지 — 밝은 선
        drawLine(palette.phos, Offset(cx, cy), polar(sweepBearing, r), strokeWidth = 1.6f)

        // 피어 블립 — 콘에 닿은 뒤 exp 감쇠. 충분히 밝을 때만 라벨.
        for (p in curPeers) {
            val sinceLit = t - (litTime[p.id] ?: -10f)
            val b = exp(-sinceLit / DECAY_S)
            if (b <= 0.04f) continue
            val pos = polar(p.bearing, p.range * r)
            drawCircle(palette.phos, radius = 7f + b * 6f, center = pos, alpha = b * 0.35f) // halo
            drawCircle(palette.phos, radius = 2.2f + b * 1.2f, center = pos, alpha = b)      // core
            if (b > 0.32f) {
                val label = "${p.name}\n${p.species.name.lowercase()}·${p.stage.name.lowercase().take(4)}"
                drawText(
                    textMeasurer = measurer,
                    text = label,
                    topLeft = Offset(pos.x + 7f, pos.y - 14f),
                    style = TextStyle(color = palette.phos.copy(alpha = b), fontSize = 8.sp, fontFamily = labelFont),
                )
            }
        }

        // 선택된 블립 — 잔광과 무관한 타깃 링(위치는 실시간 피어 위치 추적).
        selectedPeerId?.let { sel ->
            curPeers.find { it.id == sel }?.let { p ->
                val pos = polar(p.bearing, p.range * r)
                drawCircle(palette.phos, radius = 14f, center = pos, style = Stroke(1.6f), alpha = 0.95f)
                drawCircle(palette.phos, radius = 20f, center = pos, style = Stroke(0.8f), alpha = 0.45f)
            }
        }

        // 외곽 눈금 — 3°마다, 30°=길게 / 15°=중간 / 그 외=짧게.
        var td = 0f
        while (td < 360f) {
            val minor = if (td % 30f == 0f) 10f else if (td % 15f == 0f) 6f else 3f
            drawLine(palette.phosMid, polar(td, r + 1f), polar(td, r + 1f + minor), strokeWidth = 1f, alpha = 0.85f)
            td += 3f
        }
        // 외곽 스코프 링
        drawCircle(palette.phos, radius = r, center = Offset(cx, cy), style = Stroke(1f), alpha = 0.9f)

        // self 점 — 맥동 halo + 점 + 브래킷
        val pulse = 0.7f + sin(t * 2.4f) * 0.3f
        drawCircle(palette.phos, radius = 12f, center = Offset(cx, cy), alpha = 0.5f * pulse)
        drawCircle(palette.phos, radius = 3f, center = Offset(cx, cy))
        drawCircle(palette.phosMid, radius = 8f, center = Offset(cx, cy), style = Stroke(1f), alpha = 0.9f)
    }
}

/** 표시용 거리(m) — 데모 `range × 50`. (라벨/리드아웃 공용) */
internal fun rangeMeters(range: Float): Int = (range * 50).roundToInt()
