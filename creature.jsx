// creature.jsx — Sonar dot-creature renderer
// Renders a creature silhouette as a grid of bright dots with a moving scan line.
// Different "species" are different shape functions over (x,y) → density [0..1].

const SPECIES = {
  // Half-disc blob — bulges to the right, flat edge is the scan front
  blob: (u, v, t, mood) => {
    // Center the creature slightly left so flat edge sits center-right
    const cx = -0.25;
    const dx = u - cx;
    const dy = v;
    // Flat right edge — only show dots LEFT of cx + radius (creature occupies left side, growing right)
    const wave = Math.sin(v * 6 + t * 0.5) * 0.04;
    const radius = 0.78 + wave + (mood.energy - 0.5) * 0.05;
    const r = Math.sqrt(dx*dx + dy*dy);
    if (r > radius) return 0;
    // Eyes: 2 darker negative-space spots
    const e1 = Math.sqrt((dx + 0.15)**2 + (dy + 0.20)**2);
    const e2 = Math.sqrt((dx + 0.15)**2 + (dy - 0.20)**2);
    if (e1 < 0.06 || e2 < 0.06) return 0;
    // Density falls toward edge — bright at the right (flat) edge to match scan return
    const d = 1 - r / radius;
    // Boost density near the flat front (rightmost arc)
    const frontBoost = Math.max(0, 1 - Math.abs(dx - radius * 0.85) * 4) * 0.4;
    return Math.min(1, 0.4 + d * 0.55 + frontBoost);
  },

  // Jellyfish — bell + tendrils
  jelly: (u, v, t, mood) => {
    // Bell on left half (u < 0)
    const cx = -0.15;
    const cy = 0;
    const du = u - cx;
    const dv = v - cy;
    // top half ellipse for bell
    if (du < 0 && du > -0.7) {
      const ny = dv / 0.55;
      const nx = du / 0.55;
      const r = Math.sqrt(nx*nx + ny*ny);
      if (r < 1) {
        // bell ridges
        const ridge = Math.sin(nx * 12 + t * 0.4) * 0.04;
        if (r < 0.95 + ridge) return Math.min(1, 0.4 + (1 - r) * 0.7);
      }
    }
    // tendrils to the right
    if (du > -0.05 && du < 0.85) {
      const tendrilCount = 5;
      for (let i = 0; i < tendrilCount; i++) {
        const ty = (i - (tendrilCount - 1) / 2) * 0.22;
        const sway = Math.sin(du * 5 + t * 0.8 + i) * 0.07 * mood.energy;
        if (Math.abs(dv - ty - sway) < 0.025 + Math.random() * 0.015) {
          return 0.7 - du * 0.6;
        }
      }
    }
    return 0;
  },

  // Squid — body + tentacles
  squid: (u, v, t, mood) => {
    // mantle (top, pointed)
    const headCx = 0;
    const headCy = -0.35;
    const dh = Math.sqrt((u - headCx)**2 / 0.22 + (v - headCy)**2 / 0.18);
    if (dh < 1) return Math.min(1, 0.5 + (1 - dh) * 0.6);
    // eyes
    const e1 = Math.sqrt((u + 0.18)**2 + (v - headCy + 0.05)**2);
    const e2 = Math.sqrt((u - 0.18)**2 + (v - headCy + 0.05)**2);
    if (e1 < 0.05 || e2 < 0.05) return 0;
    // tentacles below
    if (v > headCy + 0.15 && v < 0.95) {
      const phase = t * 0.6;
      for (let i = 0; i < 7; i++) {
        const baseX = (i - 3) * 0.12;
        const sway = Math.sin(v * 4 + phase + i * 0.5) * 0.08 * mood.energy;
        if (Math.abs(u - baseX - sway) < 0.025) {
          return 0.6 + Math.sin(v * 8 + i) * 0.2;
        }
      }
    }
    return 0;
  },

  // Pac-Man style ghost — domed top, 4-tooth scalloped bottom,
  // negative-space eyes with bright pupil dots that drift gently.
  ghost: (u, v, t, mood) => {
    const halfW = 0.62;
    const topY = -0.82;
    const baseBottom = 0.55;
    const toothAmp = 0.20;

    // Horizontal bounds
    if (u < -halfW || u > halfW) return 0;

    // Top dome — circle of radius halfW centered at (0, topY+halfW)
    const domeCY = topY + halfW;
    if (v < domeCY) {
      const r = Math.sqrt(u * u + (v - domeCY) ** 2);
      if (r > halfW) return 0;
    }

    // Bottom scallop — 4 teeth at u = ±halfW/4, ±3·halfW/4
    // toothFactor = 1 at tooth tips, 0 in valleys
    const toothFactor = (1 - Math.cos((4 * Math.PI * u) / halfW)) / 2;
    const bottomY = baseBottom + toothAmp * toothFactor;
    if (v > bottomY) return 0;
    // also clamp left/right edges between dome and bottom (vertical sides)
    if (v >= domeCY && (u < -halfW || u > halfW)) return 0;

    // Eyes — symmetric, centered horizontally
    const happy = (mood && mood.happiness != null) ? mood.happiness : 0.7;
    const eyeY = -0.18;
    const eyeOffset = 0.27;
    const eyeR = 0.17;
    const pupilR = 0.07;

    // Pupil drift — slow side-to-side, slight up if happy
    const lookX = Math.sin(t * 0.4) * 0.05;
    const lookY = (happy - 0.5) * 0.05;

    const lEx = u + eyeOffset, lEy = v - eyeY;
    const rEx = u - eyeOffset, rEy = v - eyeY;
    const lEyeDist  = Math.sqrt(lEx * lEx + lEy * lEy);
    const rEyeDist  = Math.sqrt(rEx * rEx + rEy * rEy);
    const lPupDist  = Math.sqrt((lEx - lookX) ** 2 + (lEy - lookY) ** 2);
    const rPupDist  = Math.sqrt((rEx - lookX) ** 2 + (rEy - lookY) ** 2);

    // Bright pupil dots inside the eye holes
    if (lPupDist < pupilR || rPupDist < pupilR) return 1;
    // Negative space for eye whites (creates "holes" in the silhouette)
    if (lEyeDist < eyeR || rEyeDist < eyeR) return 0;

    // Body density — slightly brighter near the silhouette edge for a
    // sonar-return feel, dimmer toward the center.
    const distToEdge = Math.min(
      halfW - Math.abs(u),
      bottomY - v,
      v >= domeCY ? 99 : (halfW - Math.sqrt(u * u + (v - domeCY) ** 2)),
    );
    const edgeBoost = Math.max(0, 1 - distToEdge * 3) * 0.25;
    return Math.min(1, 0.45 + edgeBoost);
  },

  // Pixelated lifeform — 8-bit style
  pixel: (u, v, t, mood) => {
    // simple bitmap creature, 12x12 cells in [-1..1]
    const px = Math.floor((u + 1) * 6);
    const py = Math.floor((v + 1) * 6);
    if (px < 0 || px > 11 || py < 0 || py > 11) return 0;
    // body
    const bitmap = [
      "............",
      "....####....",
      "...######...",
      "..########..",
      ".#.######.#.",
      ".##.####.##.",
      ".##.####.##.",
      ".##########.",
      "..########..",
      "..##.##.##..",
      ".##..##..##.",
      "............",
    ];
    return bitmap[py][px] === '#' ? 1 : 0;
  },
};

// Cached point arrays per species — built once, reused
const pointCache = new Map();

function buildPoints(species, cols, rows) {
  const key = `${species}-${cols}-${rows}`;
  if (pointCache.has(key)) return pointCache.get(key);
  const pts = [];
  for (let j = 0; j < rows; j++) {
    for (let i = 0; i < cols; i++) {
      // Add slight noise to grid position for organic feel
      const u = ((i + 0.5) / cols) * 2 - 1;
      const v = ((j + 0.5) / rows) * 2 - 1;
      // Random sparsity factor for each cell
      const sparsity = Math.random();
      pts.push({ i, j, u, v, sparsity });
    }
  }
  pointCache.set(key, pts);
  return pts;
}

function SonarCreature({
  width,
  height,
  species = 'blob',
  mood = { happiness: 0.7, energy: 0.7, hunger: 0.7, hygiene: 0.7 },
  scanProgress, // 0..1 when sweeping, null when idle
  asleep = false,
  hue = 155,
}) {
  const canvasRef = React.useRef(null);
  const dotsRef = React.useRef(new Map()); // id -> { brightness, lastLitT }
  const startRef = React.useRef(performance.now());
  const lastScanRef = React.useRef(0);

  React.useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = width * dpr;
    canvas.height = height * dpr;
    canvas.style.width = `${width}px`;
    canvas.style.height = `${height}px`;
    ctx.scale(dpr, dpr);

    const cellSize = 6.5;
    const cols = Math.floor(width / cellSize);
    const rows = Math.floor(height / cellSize);
    const offX = (width - cols * cellSize) / 2;
    const offY = (height - rows * cellSize) / 2;

    // Sonar pulses on its own. A user `ping` (scanProgress prop) overrides
    // the auto-pulse for the duration of that sweep. Dots are dark by
    // default and only light up when the beam crosses them, then decay
    // along an exponential phosphor trail — so the creature is *only*
    // visible where the beam has recently been.
    const PULSE_INTERVAL = 5.0;     // s between auto-pulses
    const PULSE_DURATION = 1.6;     // s for one sweep to cross
    const BEAM_HALFWIDTH = 0.05;    // u-space half-width of the leading edge
    const TRAIL_REACH = 0.45;       // u-space soft trail behind the beam
    const PHOSPHOR_TAU = 1.0;       // s — 1/e decay time for lit dots

    let raf;
    const loop = (now) => {
      const t = (now - startRef.current) / 1000;
      ctx.clearRect(0, 0, width, height);

      const points = buildPoints(species, cols, rows);
      const shapeFn = SPECIES[species] || SPECIES.blob;

      // Beam position in u-space (-1..1). User ping takes priority; otherwise
      // run the auto-pulse on the wall clock.
      let scanU = null;
      if (scanProgress !== null && scanProgress !== undefined) {
        scanU = scanProgress * 2 - 1;
        lastScanRef.current = t;
      } else {
        const phase = (t % PULSE_INTERVAL) / PULSE_DURATION;
        if (phase < 1) scanU = phase * 2 - 1;
      }

      const dots = dotsRef.current;
      const breathU = Math.sin(t * 0.6) * 0.015;
      const breathV = Math.cos(t * 0.4) * 0.012;
      const sleepFactor = asleep ? 0.4 : 1;

      points.forEach((p) => {
        const u = p.u + breathU;
        const v = p.v + breathV;
        let density = shapeFn(u, v, t, mood);
        const id = `${p.i}_${p.j}`;
        const cur = dots.get(id) || { b: 0, lit: -10 };

        // Edge jitter: rough up the silhouette boundary the way a real
        // sonar-return would. Inside-edge dots randomly drop out; just
        // outside the mask, a small fraction "splatters" in at low density.
        const jitter = p.sparsity;
        if (density === 0) {
          const angle = jitter * Math.PI * 2;
          const reach = 0.05;
          const sampled = shapeFn(
            u + Math.cos(angle) * reach,
            v + Math.sin(angle) * reach,
            t, mood,
          );
          if (sampled > 0.25 && jitter > 0.86) density = 0.22;
        } else if (density < 0.5 && jitter > 0.7) {
          density *= 0.35;
        }

        // Per-dot stochastic presence based on density — denser at the
        // silhouette interior, sparse near the edges.
        const present = density > 0 && p.sparsity < density * 0.92 + 0.08;

        // Stamp lit-time when the beam's leading edge crosses a present dot.
        // The trail ahead/behind shows up via the smooth `target` below.
        if (scanU !== null && present) {
          const d = u - scanU;
          if (d > -BEAM_HALFWIDTH && d < BEAM_HALFWIDTH) cur.lit = t;
        }

        // brightness = mask × phosphorTrail(t - lit)
        // Adds a small live-beam boost so the leading edge reads brighter
        // than the residual phosphor.
        let beamBoost = 0;
        if (scanU !== null && present) {
          const d = u - scanU;
          if (d > -BEAM_HALFWIDTH && d < BEAM_HALFWIDTH) {
            beamBoost = 1 - Math.abs(d) / BEAM_HALFWIDTH;
          } else if (d < 0 && d > -TRAIL_REACH) {
            beamBoost = Math.max(0, 0.5 + d / TRAIL_REACH * 0.5);
          }
        }
        const sinceLit = t - cur.lit;
        const trail = Math.exp(-sinceLit / PHOSPHOR_TAU);
        const target = present
          ? Math.min(1, Math.max(trail, beamBoost) * sleepFactor)
          : 0;

        cur.b += (target - cur.b) * 0.4;
        dots.set(id, cur);

        if (cur.b < 0.02) return;

        const cx = offX + p.i * cellSize + cellSize / 2;
        const cy = offY + p.j * cellSize + cellSize / 2;

        const twinkle = 1 + Math.sin(t * 2 + p.i * 0.7 + p.j * 1.3) * 0.06;
        const b = Math.max(0, Math.min(1, cur.b * twinkle));
        if (b < 0.02) return;

        const radius = 1.4 + b * 1.8;
        ctx.beginPath();
        ctx.fillStyle = `oklch(${0.55 + b * 0.4} ${0.18 + b * 0.05} ${hue} / ${b})`;
        ctx.arc(cx, cy, radius, 0, Math.PI * 2);
        ctx.fill();

        if (b > 0.5) {
          ctx.beginPath();
          ctx.fillStyle = `oklch(0.96 0.10 ${hue} / ${(b - 0.5) * 2})`;
          ctx.arc(cx, cy, 0.9, 0, Math.PI * 2);
          ctx.fill();
        }
      });

      // Beam — wide soft halo + saturated trail + bright leading edge.
      if (scanU !== null) {
        const x = offX + ((scanU + 1) / 2) * (cols * cellSize);

        // Wide bloom — soft phosphor wash trailing the front
        const halo = ctx.createLinearGradient(x - 120, 0, x + 6, 0);
        halo.addColorStop(0, `oklch(0.7 0.20 ${hue} / 0)`);
        halo.addColorStop(0.85, `oklch(0.85 0.22 ${hue} / 0.16)`);
        halo.addColorStop(1, `oklch(0.95 0.22 ${hue} / 0.4)`);
        ctx.fillStyle = halo;
        ctx.fillRect(x - 120, 0, 126, height);

        // Tight saturated body
        const body = ctx.createLinearGradient(x - 24, 0, x + 4, 0);
        body.addColorStop(0, `oklch(0.85 0.22 ${hue} / 0)`);
        body.addColorStop(1, `oklch(0.97 0.18 ${hue} / 0.85)`);
        ctx.fillStyle = body;
        ctx.fillRect(x - 24, 0, 28, height);

        // Bright leading-edge spike
        ctx.fillStyle = `oklch(0.99 0.08 ${hue} / 1)`;
        ctx.fillRect(x - 1.5, 0, 3, height);
      }

      raf = requestAnimationFrame(loop);
    };
    raf = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(raf);
  }, [width, height, species, mood.happiness, mood.energy, mood.hunger, mood.hygiene, scanProgress, asleep, hue]);

  return <canvas ref={canvasRef} style={{ display: 'block', position: 'absolute', inset: 0 }} />;
}

window.SonarCreature = SonarCreature;
