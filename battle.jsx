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
function BattleClash({ width, height, myMove, theirMove, tag, crit, phase }) {
  const canvasRef = React.useRef(null);
  const startRef = React.useRef(performance.now());
  const themeHueRef = React.useRef(null);

  React.useEffect(() => {
    if (phase !== 'reveal' && phase !== 'damage') return undefined;
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
      const t = Math.min(1, (now - startRef.current) / (phase === 'reveal' ? 600 : 400));
      const hue = parseFloat(
        getComputedStyle(document.documentElement).getPropertyValue('--hue'),
      ) || 155;
      ctx.clearRect(0, 0, width, height);
      const cy = height / 2;
      const midX = width / 2;

      // Reveal: both signatures travel from edges toward midX.
      // Damage: a single flash centered on midX expanding outward.
      if (phase === 'reveal') {
        drawSignature(ctx, myMove,    hue, midX - (1 - t) * (midX - 24), cy, t, false);
        drawSignature(ctx, theirMove, hue, midX + (1 - t) * (midX - 24), cy, t, true);
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
        // Damage flash — bigger for crit
        const maxR = crit ? Math.max(width, height) * 0.7 : 80;
        const flashR = t * maxR;
        const grd = ctx.createRadialGradient(midX, cy, 0, midX, cy, flashR);
        grd.addColorStop(0, `oklch(0.98 0.22 ${hue} / ${(1 - t) * (crit ? 0.9 : 0.6)})`);
        grd.addColorStop(0.7, `oklch(0.85 0.22 ${hue} / ${(1 - t) * 0.3})`);
        grd.addColorStop(1, `oklch(0.85 0.22 ${hue} / 0)`);
        ctx.fillStyle = grd;
        ctx.beginPath();
        ctx.arc(midX, cy, flashR, 0, Math.PI * 2);
        ctx.fill();
        // Crit: extra concentric rings
        if (crit) {
          for (let i = 0; i < 4; i++) {
            const r = ((t * 1.6 - i * 0.1) % 1) * (maxR * 0.9);
            if (r <= 0) continue;
            ctx.strokeStyle = `oklch(0.95 0.22 ${hue} / ${0.6 * (1 - r / (maxR * 0.9))})`;
            ctx.lineWidth = 2;
            ctx.beginPath();
            ctx.arc(midX, cy, r, 0, Math.PI * 2);
            ctx.stroke();
          }
        }
      }

      // Center tag text on collision frames
      if ((phase === 'reveal' && t > 0.9) || phase === 'damage') {
        ctx.font = '600 18px VT323, JetBrains Mono, monospace';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillStyle = `oklch(0.98 0.20 ${hue})`;
        ctx.shadowColor = `oklch(0.85 0.22 ${hue})`;
        ctx.shadowBlur = 12;
        ctx.fillText(tag, midX, cy);
        ctx.shadowBlur = 0;
      }

      if (t < 1) raf = requestAnimationFrame(loop);
    };
    raf = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(raf);
  }, [phase, myMove, theirMove, tag, crit, width, height]);

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

      {/* STAGE — two creatures + clash overlay */}
      <div className="battle-stage" style={{ height: stageH, top: HEAD_H }}>
        <div className={'battle-creature left' + (battle.phase === 'damage' && battle.lastDmgMe > 0 ? ' hit' : '')}>
          <SonarCreature
            width={Math.min(stageH, 160)}
            height={Math.min(stageH, 160)}
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
        <div className={'battle-creature right' + (battle.phase === 'damage' && battle.lastDmgThem > 0 ? ' hit' : '')}>
          <SonarCreature
            width={Math.min(stageH, 160)}
            height={Math.min(stageH, 160)}
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
          width={width}
          height={stageH}
          myMove={battle.myMove}
          theirMove={battle.theirMove}
          tag={lastOutcome?.tag || ''}
          crit={!!lastOutcome?.crit}
          phase={battle.phase}
        />
      </div>

      {/* LOG — outcome tag + narration line */}
      <div className="battle-log" style={{ height: LOG_H, top: HEAD_H + stageH }}>
        {battle.phase === 'choose' && !lastOutcome && (
          <div className="battle-log-line dim">▸ select an action — ← → enter</div>
        )}
        {lastOutcome && (
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
