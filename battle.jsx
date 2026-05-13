// battle.jsx — Mabinogi-style RPS combat between the active unit and a peer.
// Two SonarCreatures face each other, simultaneous move reveal, waveform clash
// in the middle, damage = both an HP bar and a dot-density drop.
//
// Mapping
//   PING    ← 평타     (normal · base 1.0)
//   CHARGE  ← 스매시   (smash · base 1.8)
//   DODGE   ← 디펜스   (defense · counters PING)
//   SCREECH ← 카운터   (counter · reflects PING)
//
// Result matrix lives in BATTLE_OUTCOMES below.

const BATTLE_ACTIONS = ['ping', 'charge', 'dodge', 'screech'];

const BATTLE_ACTION_META = {
  ping:    { label: 'PING',    key: '←',  glyph: '⊙' },
  charge:  { label: 'CHARGE',  key: '↑',  glyph: '▶▶' },
  dodge:   { label: 'DODGE',   key: '→',  glyph: '·' },
  screech: { label: 'SCREECH', key: '↓',  glyph: '✺' },
};

// You × Them → { tag, me (dmg you take), them (dmg they take) }.
const BATTLE_OUTCOMES = {
  ping_ping:      { tag: 'INTERFERENCE', me: 0.5, them: 0.5 },
  ping_charge:    { tag: 'INTERRUPT',    me: 0,   them: 1.0 },
  ping_dodge:     { tag: 'BLOCKED',      me: 0,   them: 0   },
  ping_screech:   { tag: 'COUNTERED',    me: 0.6, them: 0   },

  charge_ping:    { tag: 'INTERRUPT',    me: 1.0, them: 0   },
  charge_charge:  { tag: 'CLASH',        me: 0.9, them: 0.9 },
  charge_dodge:   { tag: 'BROKEN',       me: 0,   them: 1.5 },
  charge_screech: { tag: 'BROKEN',       me: 0,   them: 1.5 },

  dodge_ping:     { tag: 'BLOCKED',      me: 0,   them: 0   },
  dodge_charge:   { tag: 'BROKEN',       me: 1.5, them: 0   },
  dodge_dodge:    { tag: 'MISS',         me: 0,   them: 0   },
  dodge_screech:  { tag: 'MISS',         me: 0,   them: 0   },

  screech_ping:   { tag: 'COUNTERED',    me: 0,   them: 0.6 },
  screech_charge: { tag: 'BROKEN',       me: 1.5, them: 0   },
  screech_dodge:  { tag: 'MISS',         me: 0,   them: 0   },
  screech_screech:{ tag: 'FEEDBACK',     me: 0.3, them: 0.3 },
};

const PEER_POWER_BY_STAGE = { egg: 0.5, larva: 0.7, juvenile: 1.0, adult: 1.3 };

const NPC_MOVE_PROFILES = {
  aggressive: { ping: 0.30, charge: 0.50, dodge: 0.05, screech: 0.15 },
  gentle:     { ping: 0.25, charge: 0.05, dodge: 0.40, screech: 0.30 },
  playful:    { ping: 0.25, charge: 0.25, dodge: 0.25, screech: 0.25 },
  // veteran handled separately — reads recent player moves and counters them
};

// Counters used by veteran AI to read & react.
const COUNTER_TABLE = {
  ping: 'dodge',
  charge: 'ping',
  dodge: 'charge',
  screech: 'charge',
};

function pickNpcMove(peer, battle) {
  if (peer.personality === 'veteran' && battle?.myMoveHistory?.length) {
    const recent = battle.myMoveHistory.slice(-3);
    const counts = recent.reduce((acc, m) => {
      acc[m] = (acc[m] || 0) + 1;
      return acc;
    }, {});
    const mostCommon = Object.keys(counts).sort((a, b) => counts[b] - counts[a])[0];
    // Veteran isn't perfect — 70% counter the read, 30% mix in something else.
    if (Math.random() < 0.7) return COUNTER_TABLE[mostCommon] || 'charge';
  }
  const profile = NPC_MOVE_PROFILES[peer.personality] || NPC_MOVE_PROFILES.playful;
  const r = Math.random();
  let acc = 0;
  for (const move of BATTLE_ACTIONS) {
    acc += profile[move] || 0;
    if (r < acc) return move;
  }
  return 'ping';
}

// Snapshot of (myMove, theirMove) + player/peer stats → final damage numbers.
// Player training boosts attack; player discipline reduces incoming damage.
// Peer power comes from their developmental stage. RESONANCE crit (5%) doubles
// any non-zero damage on either side and pumps a flash sigil into the log.
function resolveBattleTurn(myMove, theirMove, state, peer) {
  const key = `${myMove}_${theirMove}`;
  const out = BATTLE_OUTCOMES[key] || { tag: 'MISS', me: 0, them: 0 };
  const myAttackMult = 1 + (state.training || 0) * 0.5;
  const myDefMult = 1 - (state.discipline || 0) * 0.3;
  const peerAttackMult = PEER_POWER_BY_STAGE[peer.stage] ?? 1.0;
  let me = out.me * peerAttackMult * myDefMult;
  let them = out.them * myAttackMult;
  let crit = false;
  if (Math.random() < 0.05 && (me > 0 || them > 0)) {
    crit = true;
    me *= 2;
    them *= 2;
  }
  return {
    tag: crit ? 'RESONANCE' : out.tag,
    me: Math.round(me * 10) / 10,
    them: Math.round(them * 10) / 10,
    crit,
  };
}

// ─────────────────────────────────────────────────────────────
// Waveform clash overlay — animates two attack signatures from
// either side toward center, then a brief flash on contact.
// ─────────────────────────────────────────────────────────────
function BattleClash({ width, height, myMove, theirMove, tag, crit, phase, meX, themX, midX: midXProp, dmgMe = 0, dmgThem = 0 }) {
  const canvasRef = React.useRef(null);
  const startRef = React.useRef(performance.now());
  const themeHueRef = React.useRef(null);

  React.useEffect(() => {
    if (phase !== 'reveal' && phase !== 'damage' && phase !== 'myCast' && phase !== 'theirCast') {
      return undefined;
    }
    const canvas = canvasRef.current;
    if (!canvas) return undefined;
    const ctx = canvas.getContext('2d');
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = width * dpr;
    canvas.height = height * dpr;
    canvas.style.width = `${width}px`;
    canvas.style.height = `${height}px`;
    ctx.scale(dpr, dpr);
    startRef.current = performance.now();
    let raf;
    const loop = (now) => {
      // Cast phases play in place at the attacker's anchor; reveal & damage use
      // their original timings.
      const phaseDur =
        phase === 'reveal' ? 600 :
        phase === 'damage' ? 400 :
        600; // myCast / theirCast
      const t = Math.min(1, (now - startRef.current) / phaseDur);
      const hue = parseFloat(
        getComputedStyle(document.documentElement).getPropertyValue('--hue'),
      ) || 155;
      ctx.clearRect(0, 0, width, height);
      const cy = height / 2;
      const midX = midXProp ?? width / 2;
      // Wave launch points: just inside each creature, defaulting to the
      // canvas edges if anchors weren't supplied (back-compat).
      const startMe   = (meX   ?? 24)         + 28;
      const startThem = (themX ?? width - 24) - 28;

      // Cast phases: only the active attacker's signature draws, anchored at
      // their position with a small forward push so it reads as a wind-up.
      // Reveal: both signatures travel from each creature toward midX.
      // Damage: a single flash centered on midX expanding outward.
      if (phase === 'myCast' || phase === 'theirCast') {
        const isMine = phase === 'myCast';
        const anchorX = isMine ? (meX ?? startMe) : (themX ?? startThem);
        const move = isMine ? myMove : theirMove;
        // Small forward push (~14px) toward the opponent over the beat.
        const dir = isMine ? +1 : -1;
        const drawX = anchorX + dir * 14 * t;
        drawSignature(ctx, move, hue, drawX, cy, t, !isMine);
        // Move label above the attacker so the beat is unambiguous.
        const meta = BATTLE_ACTION_META[move];
        if (meta) {
          ctx.font = '600 14px VT323, JetBrains Mono, monospace';
          ctx.textAlign = 'center';
          ctx.textBaseline = 'middle';
          ctx.fillStyle = `oklch(0.95 0.20 ${hue} / ${Math.min(1, 0.3 + t)})`;
          ctx.shadowColor = `oklch(0.85 0.22 ${hue})`;
          ctx.shadowBlur = 10;
          ctx.fillText(meta.label, anchorX, cy - 56);
          ctx.shadowBlur = 0;
        }
      } else if (phase === 'reveal') {
        drawSignature(ctx, myMove,    hue, startMe   + (midX - startMe)   * t, cy, t, false);
        drawSignature(ctx, theirMove, hue, startThem - (startThem - midX) * t, cy, t, true);
        // Collision flash near the midpoint when they meet
        if (t > 0.85) {
          const flashR = (t - 0.85) * 80;
          const grd = ctx.createRadialGradient(midX, cy, 0, midX, cy, flashR + 12);
          grd.addColorStop(0, `oklch(0.98 0.22 ${hue} / ${(1 - (t - 0.85) * 6.6) * 0.9})`);
          grd.addColorStop(1, `oklch(0.95 0.22 ${hue} / 0)`);
          ctx.fillStyle = grd;
          ctx.beginPath();
          ctx.arc(midX, cy, flashR + 12, 0, Math.PI * 2);
          ctx.fill();
        }
      } else if (phase === 'damage') {
        // Flash anchors at the defender(s) so the impact lands where the
        // camera is panning to. A short projectile trail from midX leads the
        // flash, visually continuing the wave that left the clash.
        const targets = [];
        if (dmgMe > 0)   targets.push(meX ?? midX);
        if (dmgThem > 0) targets.push(themX ?? midX);
        if (targets.length === 0) targets.push(midX); // BLOCKED / MISS — soft ping
        const maxR = crit ? Math.max(width, height) * 0.7 : 80;
        for (const fx of targets) {
          // Projectile trail (only while flash is small) — bridges midX→fx.
          if (t < 0.4 && fx !== midX) {
            const trailT = t / 0.4;
            const trailX = midX + (fx - midX) * trailT;
            ctx.save();
            ctx.strokeStyle = `oklch(0.92 0.20 ${hue} / ${(1 - trailT) * 0.7})`;
            ctx.lineWidth = 3;
            ctx.shadowColor = `oklch(0.85 0.22 ${hue})`;
            ctx.shadowBlur = 6;
            ctx.beginPath();
            ctx.moveTo(midX, cy);
            ctx.lineTo(trailX, cy);
            ctx.stroke();
            ctx.restore();
          }
          // Impact flash at fx
          const flashR = t * maxR;
          const grd = ctx.createRadialGradient(fx, cy, 0, fx, cy, flashR);
          grd.addColorStop(0, `oklch(0.98 0.22 ${hue} / ${(1 - t) * (crit ? 0.9 : 0.6)})`);
          grd.addColorStop(0.7, `oklch(0.85 0.22 ${hue} / ${(1 - t) * 0.3})`);
          grd.addColorStop(1, `oklch(0.85 0.22 ${hue} / 0)`);
          ctx.fillStyle = grd;
          ctx.beginPath();
          ctx.arc(fx, cy, flashR, 0, Math.PI * 2);
          ctx.fill();
          if (crit) {
            for (let i = 0; i < 4; i++) {
              const r = ((t * 1.6 - i * 0.1) % 1) * (maxR * 0.9);
              if (r <= 0) continue;
              ctx.strokeStyle = `oklch(0.95 0.22 ${hue} / ${0.6 * (1 - r / (maxR * 0.9))})`;
              ctx.lineWidth = 2;
              ctx.beginPath();
              ctx.arc(fx, cy, r, 0, Math.PI * 2);
              ctx.stroke();
            }
          }
        }
      }

      // Center tag text on collision frames. During reveal it sits at midX
      // (camera centered). During damage it follows the single-defender flash
      // so the label is visible in the viewport (camera panned to defender).
      if ((phase === 'reveal' && t > 0.9) || phase === 'damage') {
        ctx.font = '600 18px VT323, JetBrains Mono, monospace';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillStyle = `oklch(0.98 0.20 ${hue})`;
        ctx.shadowColor = `oklch(0.85 0.22 ${hue})`;
        ctx.shadowBlur = 12;
        let tagX = midX;
        if (phase === 'damage') {
          if (dmgMe > 0 && dmgThem === 0)      tagX = meX ?? midX;
          else if (dmgThem > 0 && dmgMe === 0) tagX = themX ?? midX;
        }
        ctx.fillText(tag, tagX, cy);
        ctx.shadowBlur = 0;
      }

      if (t < 1) raf = requestAnimationFrame(loop);
    };
    raf = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(raf);
  }, [phase, myMove, theirMove, tag, crit, width, height, meX, themX, midXProp, dmgMe, dmgThem]);

  return (
    <canvas ref={canvasRef} style={{
      position: 'absolute', inset: 0, pointerEvents: 'none', zIndex: 10,
    }} />
  );
}

// Each move has a distinct silhouette. Position is the "head" of the wave;
// dir flips the orientation so right-side attacker mirrors.
function drawSignature(ctx, move, hue, x, y, t, mirror) {
  const sign = mirror ? -1 : 1;
  const alpha = Math.min(1, 0.4 + t);
  ctx.save();
  ctx.translate(x, y);
  ctx.strokeStyle = `oklch(0.95 0.22 ${hue} / ${alpha})`;
  ctx.fillStyle = `oklch(0.95 0.22 ${hue} / ${alpha})`;
  ctx.lineWidth = 2;
  ctx.shadowColor = `oklch(0.85 0.22 ${hue})`;
  ctx.shadowBlur = 6;

  if (move === 'ping') {
    // expanding concentric arcs in the direction of travel
    for (let i = 0; i < 3; i++) {
      const r = 6 + i * 6 + (t * 8 % 6);
      ctx.beginPath();
      ctx.arc(0, 0, r, -Math.PI / 2 - sign * 0.7, -Math.PI / 2 + sign * 0.7);
      ctx.stroke();
    }
  } else if (move === 'charge') {
    // big arrow head
    ctx.beginPath();
    ctx.moveTo(sign * 14, 0);
    ctx.lineTo(-sign * 10, -8);
    ctx.lineTo(-sign * 6, 0);
    ctx.lineTo(-sign * 10, 8);
    ctx.closePath();
    ctx.fill();
    // trailing dashes
    for (let i = 0; i < 4; i++) {
      const dx = -sign * (14 + i * 8);
      ctx.fillRect(dx, -1, sign * 5, 2);
    }
  } else if (move === 'dodge') {
    // dashed/translucent — barely-there mark
    ctx.setLineDash([3, 4]);
    ctx.strokeStyle = `oklch(0.85 0.18 ${hue} / ${alpha * 0.6})`;
    ctx.beginPath();
    ctx.arc(0, 0, 10, 0, Math.PI * 2);
    ctx.stroke();
    ctx.setLineDash([]);
  } else if (move === 'screech') {
    // jagged sawtooth waveform
    ctx.beginPath();
    for (let i = -10; i <= 10; i++) {
      const px = sign * i * 2;
      const py = (i % 2 === 0 ? -1 : 1) * 6;
      if (i === -10) ctx.moveTo(px, py);
      else ctx.lineTo(px, py);
    }
    ctx.stroke();
  }
  ctx.restore();
}

// ─────────────────────────────────────────────────────────────
// HP bar — also conveys dot-density loss as the value drops.
// ─────────────────────────────────────────────────────────────
function HpBar({ label, hp, hpMax, align = 'left', flashKey }) {
  // flashKey changes whenever damage lands; we run a brief shake animation off
  // it. Using a key on the wrapper resets the CSS animation cleanly.
  const segments = Array.from({ length: hpMax });
  return (
    <div className="battle-hp" style={{ textAlign: align }}>
      <div className="battle-hp-name t-pixel glow" style={{ textAlign: align }}>
        {label}
      </div>
      <div className="battle-hp-track" key={flashKey}>
        {segments.map((_, i) => (
          <div
            key={i}
            className={'battle-hp-cell' + (i < hp ? ' on' : '')}
          />
        ))}
        <span className="battle-hp-num t-mono">
          {Math.max(0, Math.ceil(hp))}/{hpMax}
        </span>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Action menu — 4 chips with cursor highlight + key hints.
// Disabled when phase ≠ 'choose' so the player can't input mid-reveal.
// ─────────────────────────────────────────────────────────────
function BattleMenu({ cursor, disabled, onSelect }) {
  return (
    <div className="battle-menu">
      {BATTLE_ACTIONS.map((move, i) => {
        const meta = BATTLE_ACTION_META[move];
        const active = i === cursor;
        return (
          <button
            key={move}
            className={'battle-menu-btn' + (active ? ' active' : '') + (disabled ? ' disabled' : '')}
            onClick={() => !disabled && onSelect(i)}
            disabled={disabled}
          >
            <span className="battle-menu-glyph">{meta.glyph}</span>
            <span className="battle-menu-label">{meta.label}</span>
          </button>
        );
      })}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// BattleScreen — full bezel layout. Header (HP) / stage (creatures
// + waveform clash) / log (outcome) / menu.
// ─────────────────────────────────────────────────────────────
function BattleScreen({ width, height, state, dispatch, tweaks }) {
  const battle = state.battle;
  if (!battle) return null;
  const peer = state.peers.find((p) => p.id === battle.peerId);
  if (!peer) return null;

  const HEAD_H = 56;
  const MENU_H = 92;
  const LOG_H = 46;
  const stageH = height - HEAD_H - MENU_H - LOG_H;

  // Intra-reveal substage. 'clash' = waves meeting at midX with the camera
  // centered. 'trail' = projectile leaving the clash toward the defender;
  // camera target shifts to the defender so the pan is already in motion when
  // the damage phase begins — that's what removes the seam between reveal and
  // damage. 500ms aligns with the canvas clash flash (t ≈ 0.83 of 600ms).
  const [revealStage, setRevealStage] = React.useState('clash');
  React.useEffect(() => {
    if (battle.phase !== 'reveal') {
      setRevealStage('clash');
      return undefined;
    }
    const id = setTimeout(() => setRevealStage('trail'), 500);
    return () => clearTimeout(id);
  }, [battle.phase]);

  // Drive cursor + commit from window keyboard.
  React.useEffect(() => {
    const onKey = (e) => {
      if (battle.phase !== 'choose') return;
      if (e.key === 'ArrowLeft' || e.key === 'a') {
        e.preventDefault();
        dispatch({ type: 'battleCursor', delta: -1 });
      } else if (e.key === 'ArrowRight' || e.key === 'd') {
        e.preventDefault();
        dispatch({ type: 'battleCursor', delta: +1 });
      } else if (e.key === 'Enter' || e.key === ' ') {
        // Don't steal Enter when the terminal input owns focus.
        const tag = document.activeElement?.tagName;
        if (tag === 'INPUT' || tag === 'TEXTAREA') return;
        e.preventDefault();
        dispatch({ type: 'battleCommit' });
      } else if (e.key === 'Escape') {
        e.preventDefault();
        dispatch({ type: 'battleFlee' });
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [battle.phase, dispatch]);

  // Creature mood reflects HP — energy dims as you lose health, so dot density
  // visibly drops along with the bar.
  const myEnergy = Math.max(0.15, battle.hpMe / battle.hpMaxMe);
  const theirEnergy = Math.max(0.15, battle.hpThem / battle.hpMaxThem);

  const lastOutcome = battle.log[0]; // newest first

  const hue = getActiveHue ? getActiveHue(state, tweaks) : (HUE_BY_THEME[tweaks.theme] ?? 155);

  return (
    <div className="crt-screen" style={{ width, height, position: 'relative' }}>
      <CRTLayers scanlines={tweaks.scanlines} noise={tweaks.noise} glowStrength={tweaks.crt} />

      {/* HEADER — HP bars + turn counter */}
      <div className="battle-head" style={{ height: HEAD_H }}>
        <div className="hud-corner tl" />
        <div className="hud-corner tr" />
        <HpBar label={state.name} hp={battle.hpMe} hpMax={battle.hpMaxMe}
               align="left" flashKey={`me-${battle.flashNonceMe || 0}`} />
        <div className="battle-turn t-mono">
          <div>ENGAGEMENT</div>
          <div className="battle-turn-num">R.{String(battle.turn).padStart(2, '0')}</div>
        </div>
        <HpBar label={peer.name} hp={battle.hpThem} hpMax={battle.hpMaxThem}
               align="right" flashKey={`them-${battle.flashNonceThem || 0}`} />
      </div>

      {/* STAGE — wide side-scrolling arena. Camera pans to the action so the
           two creatures are never both centered at once. */}
      {(() => {
        const ARENA_FACTOR = 1.9;
        const arenaW = Math.round(width * ARENA_FACTOR);
        const creatureW = Math.min(stageH, 160);
        const meAnchorX = Math.round(arenaW * 0.18);
        const themAnchorX = Math.round(arenaW * 0.82);

        // Camera target: positive translateX shifts the arena right (camera left).
        const FOCUS_ME = (width / 2) - meAnchorX;
        const FOCUS_THEM = (width / 2) - themAnchorX;
        const FOCUS_CENTER = (width - arenaW) / 2;
        // The "follow the projectile" focus — used by both reveal-trail and
        // damage so the CSS transform doesn't change at the phase boundary.
        const dmgMe = (battle.lastDmgMe || 0) > 0;
        const dmgThem = (battle.lastDmgThem || 0) > 0;
        const projectileFocus =
          (dmgMe && !dmgThem) ? FOCUS_ME :
          (dmgThem && !dmgMe) ? FOCUS_THEM :
          FOCUS_CENTER; // mutual or no damage stays centered
        let camX = FOCUS_CENTER;
        if (battle.phase === 'choose' || battle.phase === 'myCast') camX = FOCUS_ME;
        else if (battle.phase === 'theirCast') camX = FOCUS_THEM;
        else if (battle.phase === 'reveal') {
          camX = revealStage === 'trail' ? projectileFocus : FOCUS_CENTER;
        }
        else if (battle.phase === 'damage') camX = projectileFocus;

        return (
          <div className="battle-stage" style={{ height: stageH, top: HEAD_H }}>
            <div
              className="battle-arena"
              style={{
                width: arenaW,
                height: stageH,
                transform: `translateX(${camX}px)`,
              }}
            >
              <div
                className={'battle-creature left' + (battle.phase === 'damage' && battle.lastDmgMe > 0 ? ' hit' : '')}
                style={{ left: meAnchorX - creatureW / 2, width: creatureW, height: creatureW }}
              >
                <SonarCreature
                  width={creatureW}
                  height={creatureW}
                  species={tweaks.species}
                  mood={{
                    happiness: state.happiness,
                    energy: myEnergy,
                    hunger: 1 - state.hunger,
                    hygiene: 1 - state.dirty,
                  }}
                  scanProgress={null}
                  asleep={false}
                  hue={hue}
                  pulseInterval={3}
                  decayTau={1.2}
                />
              </div>
              <div
                className={'battle-creature right' + (battle.phase === 'damage' && battle.lastDmgThem > 0 ? ' hit' : '')}
                style={{ left: themAnchorX - creatureW / 2, width: creatureW, height: creatureW }}
              >
                <SonarCreature
                  width={creatureW}
                  height={creatureW}
                  species={peer.species}
                  mood={{
                    happiness: peer.bond,
                    energy: theirEnergy,
                    hunger: 0.7,
                    hygiene: 0.7,
                  }}
                  scanProgress={null}
                  asleep={false}
                  hue={hue}
                  pulseInterval={3}
                  decayTau={1.2}
                />
              </div>

              <BattleClash
                width={arenaW}
                height={stageH}
                meX={meAnchorX}
                themX={themAnchorX}
                midX={arenaW / 2}
                myMove={battle.myMove}
                theirMove={battle.theirMove}
                tag={lastOutcome?.tag || ''}
                crit={!!lastOutcome?.crit}
                phase={battle.phase}
                dmgMe={battle.lastDmgMe || 0}
                dmgThem={battle.lastDmgThem || 0}
              />
            </div>
          </div>
        );
      })()}

      {/* LOG — beat-by-beat narration. Cast phases announce each fighter's
           chosen move before the clash; outcome only displays once damage
           lands. The previous-turn outcome lingers through 'choose' so the
           player can read the last result before committing to the next. */}
      <div className="battle-log" style={{ height: LOG_H, top: HEAD_H + stageH }}>
        {battle.phase === 'choose' && !lastOutcome && (
          <div className="battle-log-line dim">▸ select an action — ← → enter</div>
        )}
        {battle.phase === 'myCast' && battle.myMove && (
          <>
            <div className="battle-log-tag">
              {state.name} → {BATTLE_ACTION_META[battle.myMove].label}
            </div>
            <div className="battle-log-line dim">▸ casting…</div>
          </>
        )}
        {battle.phase === 'theirCast' && battle.theirMove && (
          <>
            <div className="battle-log-tag">
              {peer.name} → {BATTLE_ACTION_META[battle.theirMove].label}
            </div>
            <div className="battle-log-line dim">▸ casting…</div>
          </>
        )}
        {battle.phase === 'reveal' && (
          <div className="battle-log-line dim">▸ resolving…</div>
        )}
        {(battle.phase === 'choose' || battle.phase === 'damage' || battle.phase === 'end')
          && lastOutcome && (
          <>
            <div className={'battle-log-tag ' + (lastOutcome.crit ? 'crit' : '')}>
              {lastOutcome.tag}
              {lastOutcome.crit && <span className="battle-log-crit"> · RESONANCE</span>}
            </div>
            <div className="battle-log-line">
              {lastOutcome.line}
            </div>
          </>
        )}
        {battle.phase === 'end' && battle.result && (
          <div className={'battle-log-tag result-' + battle.result}>
            {battle.result === 'win' ? '◉ ENGAGEMENT WON' :
             battle.result === 'lose' ? '✟ KO — UNIT DOWN' :
             battle.result === 'flee' ? '↩ DISENGAGED' : '◯ STALEMATE'}
          </div>
        )}
      </div>

      {/* MENU */}
      <div className="battle-menu-wrap" style={{ height: MENU_H, top: HEAD_H + stageH + LOG_H }}>
        <BattleMenu
          cursor={battle.cursor}
          disabled={battle.phase !== 'choose'}
          onSelect={(i) => {
            dispatch({ type: 'battleCursor', set: i });
            dispatch({ type: 'battleCommit' });
          }}
        />
        <div className="battle-menu-hint t-mono">
          ← → cycle · enter commit · esc flee
        </div>
      </div>

      {/* Toast */}
      {state.toast && (
        <div style={{
          position: 'absolute', top: HEAD_H + 8, left: '50%', transform: 'translateX(-50%)',
          background: 'oklch(0.16 0.06 var(--hue))',
          border: '1px solid var(--phos)',
          padding: '5px 12px',
          fontFamily: 'JetBrains Mono', fontSize: 10,
          color: 'var(--phos)', letterSpacing: '0.06em',
          boxShadow: '0 0 8px var(--phos-mid)',
          zIndex: 20,
        }}>
          ▸ {state.toast}
        </div>
      )}
    </div>
  );
}

Object.assign(window, {
  BattleScreen, BATTLE_ACTIONS, BATTLE_ACTION_META, BATTLE_OUTCOMES,
  pickNpcMove, resolveBattleTurn,
});
