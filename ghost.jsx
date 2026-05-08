// ghost.jsx — Pixel ghost gang renderer (original sprite, not Pac-Man's ghosts).
// One main ghost in the foreground + 3 smaller ones drifting around like a gang.

const GHOST_PALETTE = {
  pink:   { body: '#ff9bcc', tint: '#ffd3e8' },
  red:    { body: '#ff4242', tint: '#ff8a8a' },
  orange: { body: '#ffaa44', tint: '#ffd28a' },
  cyan:   { body: '#5cefd4', tint: '#a8f6e6' },
};
const GHOST_KEYS = ['pink', 'red', 'orange', 'cyan'];

// 14 wide × 14 tall bitmap.
//   B = body    W = eye-white   P = pupil   . = transparent
// Symmetric Pac-Man-style ghost: rounded stair-step dome,
// two centered eyes (4-wide whites with 2-wide pupils),
// and a 4-tooth scallop bottom.
const GHOST_BMP = [
  '....BBBBBB....',
  '..BBBBBBBBBB..',
  '.BBBBBBBBBBBB.',
  'BBBBBBBBBBBBBB',
  'BBWWWWBBWWWWBB',
  'BBWWWWBBWWWWBB',
  'BBWPPWBBWPPWBB',
  'BBWPPWBBWPPWBB',
  'BBWWWWBBWWWWBB',
  'BBBBBBBBBBBBBB',
  'BBBBBBBBBBBBBB',
  'BBBBBBBBBBBBBB',
  'BBB.BBB.BBB.BB',
  'BB...BB...BB..',
];

const GHOST_W = 14;
const GHOST_H = 14;

function drawGhostSprite(ctx, x, y, scale, colorKey, mood, t, asleep) {
  const pal = GHOST_PALETTE[colorKey] || GHOST_PALETTE.pink;
  const happy = mood?.happiness ?? 0.7;
  // Pupil offset based on mood — looking up if happy, down if sad
  const lookY = asleep ? 0 : (happy - 0.5) * 1.2;
  const lookX = Math.sin(t * 0.6) * 0.4;

  for (let py = 0; py < GHOST_H; py++) {
    for (let px = 0; px < GHOST_W; px++) {
      const c = GHOST_BMP[py][px];
      if (c === '.') continue;
      let fill;
      if (c === 'B') fill = pal.body;
      else if (c === 'W') fill = asleep ? pal.tint : '#ffffff';
      else if (c === 'P') {
        // Render pupils with a small offset for "looking" — but keep them on a grid
        const dx = Math.round(lookX);
        const dy = Math.round(lookY);
        if (asleep) {
          fill = '#1a2a4a';
        } else {
          // Move the pupil pixel one cell over within its eye-white area
          const targetX = px + dx;
          const targetY = py + dy;
          if (GHOST_BMP[targetY] && (GHOST_BMP[targetY][targetX] === 'W' || GHOST_BMP[targetY][targetX] === 'P')) {
            ctx.fillStyle = '#ffffff';
            ctx.fillRect(x + px * scale, y + py * scale, scale, scale);
            ctx.fillStyle = '#1a2a4a';
            ctx.fillRect(x + targetX * scale, y + targetY * scale, scale, scale);
            continue;
          }
          fill = '#1a2a4a';
        }
      }
      ctx.fillStyle = fill;
      ctx.fillRect(x + px * scale, y + py * scale, scale, scale);

      // Sleep eyelids — overlay a body-color line through eye area
      if (asleep && (c === 'W' || c === 'P')) {
        ctx.fillStyle = pal.body;
        if (py === 6 || py === 7) {
          ctx.fillRect(x + px * scale, y + py * scale, scale, scale);
        }
      }
    }
  }

  // Soft outer glow on the body silhouette
  ctx.save();
  ctx.globalCompositeOperation = 'lighter';
  ctx.fillStyle = pal.body;
  ctx.globalAlpha = 0.10;
  for (let py = 0; py < GHOST_H; py++) {
    for (let px = 0; px < GHOST_W; px++) {
      if (GHOST_BMP[py][px] === 'B') {
        ctx.fillRect(x + px * scale - scale*0.4, y + py * scale - scale*0.4, scale*1.8, scale*1.8);
      }
    }
  }
  ctx.restore();
}

function GhostScene({ width, height, mainColor = 'pink', mood, asleep, scanProgress, hue }) {
  const canvasRef = React.useRef(null);
  const startRef = React.useRef(performance.now());
  const ghostsRef = React.useRef(null);

  if (!ghostsRef.current) {
    // 3 follower ghosts at different positions/phases — spawn the colors NOT chosen as main
    const others = GHOST_KEYS.filter(k => k !== mainColor).slice(0, 3);
    ghostsRef.current = others.map((color, i) => ({
      color,
      baseX: 0.18 + i * 0.32,   // 0..1 across width
      baseY: 0.78 - (i % 2) * 0.18,
      phase: i * 1.7,
      scale: 0.55 + (i % 2) * 0.05,
    }));
  } else {
    // Update colors if mainColor changed
    const others = GHOST_KEYS.filter(k => k !== mainColor).slice(0, 3);
    ghostsRef.current.forEach((g, i) => g.color = others[i]);
  }

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
    ctx.imageSmoothingEnabled = false;

    let raf;
    const loop = (now) => {
      const t = (now - startRef.current) / 1000;
      ctx.clearRect(0, 0, width, height);

      // Subtle scan line behind everything
      if (scanProgress !== null && scanProgress !== undefined) {
        const x = scanProgress * width;
        const grad = ctx.createLinearGradient(x - 80, 0, x + 4, 0);
        grad.addColorStop(0, `oklch(0.85 0.22 ${hue} / 0)`);
        grad.addColorStop(1, `oklch(0.95 0.22 ${hue} / 0.4)`);
        ctx.fillStyle = grad;
        ctx.fillRect(x - 80, 0, 84, height);
        ctx.fillStyle = `oklch(0.97 0.20 ${hue} / 0.7)`;
        ctx.fillRect(x - 1, 0, 2, height);
      }

      // Followers — 3 smaller ghosts drifting in background
      ghostsRef.current.forEach((g) => {
        const driftX = g.baseX * width + Math.sin(t * 0.5 + g.phase) * 30;
        const driftY = g.baseY * height + Math.cos(t * 0.8 + g.phase) * 14;
        const scale = g.scale * Math.min(width, height) / 14 * 0.6;
        const wobble = Math.sin(t * 2.5 + g.phase) * (scale * 0.15);
        drawGhostSprite(ctx, driftX - GHOST_W * scale / 2, driftY + wobble - GHOST_H * scale / 2, scale, g.color, mood, t + g.phase, asleep);
      });

      // Main ghost — large, center-ish
      const mainScale = Math.floor(Math.min(width / (GHOST_W + 4), height / (GHOST_H + 4)) * 0.78);
      const mainX = (width - GHOST_W * mainScale) / 2;
      const wobble = Math.sin(t * 1.6) * (mainScale * 0.4);
      const mainY = (height - GHOST_H * mainScale) / 2 + wobble;
      drawGhostSprite(ctx, mainX, mainY, mainScale, mainColor, mood, t, asleep);

      // After-trail of dots from scan
      raf = requestAnimationFrame(loop);
    };
    raf = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(raf);
  }, [width, height, mainColor, mood?.happiness, mood?.energy, asleep, scanProgress, hue]);

  return <canvas ref={canvasRef} style={{ display: 'block', position: 'absolute', inset: 0 }} />;
}

window.GhostScene = GhostScene;
window.GHOST_KEYS = GHOST_KEYS;
