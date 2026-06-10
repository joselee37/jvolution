package today.superb.jvl.ui.radar

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import today.superb.jvl.core.Peer
import today.superb.jvl.ui.text.MonoText
import today.superb.jvl.ui.theme.LocalDisplayFont
import today.superb.jvl.ui.theme.LocalPalette

/**
 * 근접 경보 오버레이 — 데모 `PeerAlertOverlay` 1:1. 도전 요청이 활성인 동안 메인 베젤 위에 뜬다.
 *
 * 입력을 막지 않는다(터미널은 별도 영역에서 계속 `accept`/`decline`을 받음). 프레임이 깜빡이고,
 * 전체 색조는 [today.superb.jvl.ui.theme.Hue.Alert] 팔레트(App에서 pendingRequest로 전환)로 적색이다.
 *
 * ACCEPT/DECLINE 버튼 탭 = 터미널 매크로(App이 배선) — 타이핑과 동일 경로.
 */
@Composable
fun PeerAlertOverlay(
    peer: Peer,
    onAccept: () -> Unit = {},
    onDecline: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val blink by rememberInfiniteTransition(label = "alert-frame").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alert-blink",
    )

    Box(
        modifier.fillMaxSize().background(palette.bg.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(0.82f).border(2.dp, palette.phos.copy(alpha = blink)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MonoText("▲ PROXIMITY ALERT ▲", color = palette.phos, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            MonoText("NEW CONTACT", color = palette.phos, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = LocalDisplayFont.current)
            MonoText("BIOLOGIC // PROXIMAL", color = palette.phosDim, fontSize = 9.sp)
            Spacer(Modifier.height(8.dp))

            meta("UNIT", peer.name)
            meta("SPECIES", peer.species.name.uppercase())
            meta("STAGE", peer.stage.name.uppercase())
            meta(
                "BRG/RNG",
                "${peer.bearing.toInt().toString().padStart(3, '0')}° / ${rangeMeters(peer.range).toString().padStart(2, '0')}m",
            )

            Spacer(Modifier.height(8.dp))
            MonoText("CHALLENGE INCOMING", color = palette.phos, fontSize = 12.sp)

            Row(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                alertButton("▸ ACCEPT", onAccept)
                alertButton("✕ DECLINE", onDecline)
            }
            MonoText("▸ terminal: accept · decline", color = palette.phosDim, fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun alertButton(label: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        Modifier.border(1.dp, palette.phos).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        MonoText(label, color = palette.phos, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun meta(label: String, value: String) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth(0.7f).padding(vertical = 1.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        MonoText(label, color = palette.phosDim, fontSize = 10.sp)
        MonoText(value, color = palette.phos, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
