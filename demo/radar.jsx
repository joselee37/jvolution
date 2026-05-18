// radar.jsx — Circular radar scope with rotating sweep arm + peer blips.
// Phosphor decay on blips so they linger after the sweep crosses them.
// Self is the center dot; peers live at (bearing, range) in polar space.

const RADAR_SWEEP_S = 6;       // seconds per full revolution
const RADAR_CONE_DEG = 50;     // illuminated cone behind the leading edge
const RADAR_DECAY_S = 1.6;     // phosphor decay tau for blips

// Convert bearing (0°=N, 90°=E, clockwise) to canvas-space angle
// where 0 = right and angles grow clockwise (canvas Y is flipped).
const bearingToCanvas = (bearing) => ((bearing - 90) * Math.PI) / 180;

function RadarScreen({ width, height, state, dispatch, tweaks }) {
  const canvasRef = React.useRef(null);
  const peersRef = React.useRef(state.peers);
  const themeRef = React.useRef(tweaks.theme);
  const alertRef = React.useRef(!!state.pendingRequest);
  // Lerped hue so transitions match the CSS @property animation on --hue.
  const hueRef = React.useRef(HUE_BY_THEME[tweaks.theme] ?? 155);
  const blipsRef = React.useRef(new Map()); // id → { b, lit }
  const startRef = React.useRef(performance.now());

  // Reserve chrome above + below the round scope.
  const HEADER_H = 42;
  const FOOTER_H = 24;
  const canvasW = width;
  const canvasH = Math.max(80, height - HEADER_H - FOOTER_H);

  // Keep refs fresh without re-running the rAF effect.
  React.useEffect(() => { peersRef.current = state.peers; });
  React.useEffect(() => { themeRef.current = tweaks.theme; });
  React.useEffect(() => { alertRef.current = !!state.pendingRequest; });

  React.useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = canvasW * dpr;
    canvas.height = canvasH * dpr;
    canvas.style.width = `${canvasW}px`;
    canvas.style.height = `${canvasH}px`;
    ctx.scale(dpr, dpr);

    let raf;
    const loop = (now) => {
      const t = (now - startRef.current) / 1000;
      // Shortest-path lerp so 155 (green) → 25 (red) goes the short way,
      // not all the way around the color wheel.
      const target = alertRef.current ? 25 : (HUE_BY_THEME[themeRef.current] ?? 155);
      const diff = ((target - hueRef.current + 540) % 360) - 180;
      hueRef.current += diff * 0.08;
      const hue = hueRef.current;
      const peers = peersRef.current;

      // Geometry
      const cx = canvasW / 2;
      const cy = canvasH / 2;
      const r = Math.min(canvasW, canvasH) / 2 - 14;

      ctx.clearRect(0, 0, canvasW, canvasH);

      // ── Scope background ─────────────────────────────────
      const bg = ctx.createRadialGradient(cx, cy, 0, cx, cy, r);
      bg.addColorStop(0, `oklch(0.18 0.10 ${hue} / 0.45)`);
      bg.addColorStop(0.7, `oklch(0.10 0.06 ${hue} / 0.35)`);
      bg.addColorStop(1, `oklch(0.05 0.03 ${hue} / 0.55)`);
      ctx.beginPath();
      ctx.arc(cx, cy, r, 0, Math.PI * 2);
      ctx.fillStyle = bg;
      ctx.fill();

      // Inner clip so rings/sweep stay round
      ctx.save();
      ctx.beginPath();
      ctx.arc(cx, cy, r, 0, Math.PI * 2);
      ctx.clip();

      // ── Concentric rings ─────────────────────────────────
      ctx.strokeStyle = `oklch(0.55 0.16 ${hue} / 0.5)`;
      ctx.lineWidth = 0.8;
      for (let i = 1; i <= 4; i++) {
        ctx.beginPath();
        ctx.arc(cx, cy, (r * i) / 4, 0, Math.PI * 2);
        ctx.stroke();
      }

      // Polar grid spokes every 30°
      ctx.strokeStyle = `oklch(0.45 0.10 ${hue} / 0.35)`;
      ctx.lineWidth = 0.6;
      for (let deg = 0; deg < 360; deg += 30) {
        const a = bearingToCanvas(deg);
        ctx.beginPath();
        ctx.moveTo(cx, cy);
        ctx.lineTo(cx + Math.cos(a) * r, cy + Math.sin(a) * r);
        ctx.stroke();
      }

      // Crosshair (slightly brighter than spokes — N/E/S/W axis)
      ctx.strokeStyle = `oklch(0.65 0.18 ${hue} / 0.7)`;
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(cx - r, cy); ctx.lineTo(cx + r, cy);
      ctx.moveTo(cx, cy - r); ctx.lineTo(cx, cy + r);
      ctx.stroke();

      // ── Sweep cone (gradient trail behind leading edge) ──
      const sweepRate = 360 / RADAR_SWEEP_S; // deg/sec
      const sweepBearing = (t * sweepRate) % 360;
      const sweepA = bearingToCanvas(sweepBearing);
      const trailA = bearingToCanvas(sweepBearing - RADAR_CONE_DEG);

      // Wedge from (sweep - cone) → (sweep), with conic-style gradient
      // approximated by stacking thin wedges.
      const steps = 18;
      for (let i = 0; i < steps; i++) {
        const a0 = trailA + ((sweepA - trailA) * i) / steps;
        const a1 = trailA + ((sweepA - trailA) * (i + 1)) / steps;
        const alpha = 0.05 + (i / steps) ** 2 * 0.5;
        ctx.beginPath();
        ctx.moveTo(cx, cy);
        ctx.arc(cx, cy, r, a0, a1);
        ctx.closePath();
        ctx.fillStyle = `oklch(0.78 0.22 ${hue} / ${alpha})`;
        ctx.fill();
      }

      // Leading edge — bright line
      ctx.strokeStyle = `oklch(0.98 0.22 ${hue} / 1)`;
      ctx.lineWidth = 1.6;
      ctx.shadowColor = `oklch(0.85 0.22 ${hue})`;
      ctx.shadowBlur = 10;
      ctx.beginPath();
      ctx.moveTo(cx, cy);
      ctx.lineTo(cx + Math.cos(sweepA) * r, cy + Math.sin(sweepA) * r);
      ctx.stroke();
      ctx.shadowBlur = 0;

      // ── Peer blips ───────────────────────────────────────
      const blips = blipsRef.current;
      peers.forEach((p) => {
        const a = bearingToCanvas(p.bearing);
        const dist = p.range * r;
        const px = cx + Math.cos(a) * dist;
        const py = cy + Math.sin(a) * dist;

        // Is this peer inside the sweep cone right now?
        let diff = sweepBearing - p.bearing;
        diff = ((diff % 360) + 360) % 360;
        const inCone = diff <= RADAR_CONE_DEG;

        const cur = blips.get(p.id) || { b: 0, lit: -10 };
        if (inCone) cur.lit = t;
        const sinceLit = t - cur.lit;
        cur.b = Math.max(0, Math.exp(-sinceLit / RADAR_DECAY_S));
        blips.set(p.id, cur);

        if (cur.b <= 0.04) return;

        // Outer halo
        const haloR = 7 + cur.b * 6;
        const halo = ctx.createRadialGradient(px, py, 0, px, py, haloR);
        halo.addColorStop(0, `oklch(0.92 0.22 ${hue} / ${cur.b * 0.85})`);
        halo.addColorStop(1, `oklch(0.92 0.22 ${hue} / 0)`);
        ctx.fillStyle = halo;
        ctx.beginPath();
        ctx.arc(px, py, haloR, 0, Math.PI * 2);
        ctx.fill();

        // Core
        ctx.beginPath();
        ctx.fillStyle = `oklch(0.97 0.20 ${hue} / ${cur.b})`;
        ctx.arc(px, py, 2.2 + cur.b * 1.2, 0, Math.PI * 2);
        ctx.fill();

        // Label — only while bright enough to read
        if (cur.b > 0.32) {
          ctx.font = '600 9.5px JetBrains Mono, monospace';
          ctx.fillStyle = `oklch(0.92 0.18 ${hue} / ${cur.b})`;
          ctx.textAlign = 'left';
          // Flip label to the left if too close to right edge
          const lx = px + 7 > cx + r * 0.78 ? px - 7 : px + 7;
          const align = px + 7 > cx + r * 0.78 ? 'right' : 'left';
          ctx.textAlign = align;
          ctx.fillText(p.name, lx, py - 4);
          ctx.font = '8.5px JetBrains Mono, monospace';
          ctx.fillStyle = `oklch(0.65 0.14 ${hue} / ${cur.b * 0.85})`;
          ctx.fillText(`${p.species}·${p.stage.slice(0, 4)}`, lx, py + 7);
        }
      });

      ctx.restore(); // end clip

      // ── Outer tick ring ──────────────────────────────────
      ctx.strokeStyle = `oklch(0.65 0.16 ${hue} / 0.85)`;
      ctx.lineWidth = 1.2;
      for (let deg = 0; deg < 360; deg += 3) {
        const a = bearingToCanvas(deg);
        const minor = deg % 30 === 0 ? 10 : deg % 15 === 0 ? 6 : 3;
        const r0 = r + 1;
        const r1 = r + 1 + minor;
        ctx.beginPath();
        ctx.moveTo(cx + Math.cos(a) * r0, cy + Math.sin(a) * r0);
        ctx.lineTo(cx + Math.cos(a) * r1, cy + Math.sin(a) * r1);
        ctx.stroke();
      }

      // Outer scope ring
      ctx.strokeStyle = `oklch(0.75 0.18 ${hue} / 0.9)`;
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.arc(cx, cy, r, 0, Math.PI * 2);
      ctx.stroke();

      // ── Self dot (pulsing) ───────────────────────────────
      const pulse = 0.7 + Math.sin(t * 2.4) * 0.3;
      const selfHalo = ctx.createRadialGradient(cx, cy, 0, cx, cy, 12);
      selfHalo.addColorStop(0, `oklch(0.95 0.22 ${hue} / ${0.6 * pulse})`);
      selfHalo.addColorStop(1, `oklch(0.95 0.22 ${hue} / 0)`);
      ctx.fillStyle = selfHalo;
      ctx.beginPath();
      ctx.arc(cx, cy, 12, 0, Math.PI * 2);
      ctx.fill();
      ctx.beginPath();
      ctx.fillStyle = `oklch(1 0.10 ${hue})`;
      ctx.arc(cx, cy, 3, 0, Math.PI * 2);
      ctx.fill();
      // small bracket around self
      ctx.strokeStyle = `oklch(0.85 0.18 ${hue} / 0.9)`;
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.arc(cx, cy, 8, 0, Math.PI * 2);
      ctx.stroke();

      // ── Compass labels (N/E/S/W) ─────────────────────────
      const lbl = (text, dx, dy) => {
        ctx.font = '600 10px JetBrains Mono, monospace';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillStyle = `oklch(0.82 0.18 ${hue} / 0.85)`;
        ctx.fillText(text, cx + dx, cy + dy);
      };
      const lblR = r + 18;
      lbl('N', 0, -lblR);
      lbl('E', lblR, 0);
      lbl('S', 0, lblR);
      lbl('W', -lblR, 0);

      raf = requestAnimationFrame(loop);
    };
    raf = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(raf);
  }, [canvasW, canvasH]);

  // ── Surrounding chrome (header + readouts) ───────────────
  const incoming = state.pendingRequest
    ? state.peers.find((p) => p.id === state.pendingRequest.from)
    : null;

  return (
    <div className="crt-screen" style={{ width, height, position: 'relative' }}>
      <CRTLayers
        scanlines={tweaks.scanlines}
        noise={tweaks.noise}
        glowStrength={tweaks.crt}
      />

      {/* TOP READOUT (compact — radar circle needs the space) */}
      <div style={{
        position: 'absolute', top: 0, left: 0, right: 0,
        padding: '10px 14px 6px', zIndex: 20,
      }}>
        <div className="hud-corner tl" />
        <div className="hud-corner tr" />
        <div className="readout-row">
          <span><b>LRRS-RADAR</b> // CH.07</span>
          <span>SWEEP <b>{RADAR_SWEEP_S}s/rev</b></span>
        </div>
        <div className="readout-row" style={{ marginTop: 3 }}>
          <span>CONTACTS <b>{state.peers.length}</b></span>
          <span className="t-mono">
            {incoming ? (
              <>
                <span className="alert-dot" />
                <b style={{ color: 'var(--phos)' }}>{incoming.name} HAILS</b>
              </>
            ) : (
              <>
                <span className="alive-dot" style={{
                  display: 'inline-block', verticalAlign: 'middle', marginRight: 5,
                }} />
                BROADCASTING
              </>
            )}
          </span>
        </div>
      </div>

      {/* RADAR CANVAS */}
      <canvas ref={canvasRef} style={{
        position: 'absolute', left: 0, right: 0, top: HEADER_H,
        display: 'block', margin: '0 auto',
      }} />

      {/* BOTTOM READOUTS */}
      <div style={{
        position: 'absolute', bottom: 8, left: 12,
        fontFamily: 'JetBrains Mono', fontSize: 9,
        color: 'var(--phos-dim)', lineHeight: 1.5,
      }}>
        <div className="hud-corner bl" />
        <div>FREQ 14.2KHZ</div>
        <div>GAIN <span style={{ color: 'var(--phos-mid)' }}>AUTO</span></div>
      </div>
      <div style={{
        position: 'absolute', bottom: 8, right: 12,
        fontFamily: 'JetBrains Mono', fontSize: 9,
        color: 'var(--phos-dim)', textAlign: 'right', lineHeight: 1.5,
      }}>
        <div className="hud-corner br" />
        <div>RNG 0050M</div>
        <div>type `sonar`</div>
      </div>

      {/* Toast */}
      {state.toast && (
        <div style={{
          position: 'absolute', top: 70, left: '50%', transform: 'translateX(-50%)',
          background: 'oklch(0.16 0.06 var(--hue))',
          border: '1px solid var(--phos)',
          padding: '5px 12px',
          fontFamily: 'JetBrains Mono', fontSize: 10,
          color: 'var(--phos)', letterSpacing: '0.06em',
          boxShadow: '0 0 8px var(--phos-mid)',
          zIndex: 30,
        }}>
          ▸ {state.toast}
        </div>
      )}
    </div>
  );
}

window.RadarScreen = RadarScreen;
