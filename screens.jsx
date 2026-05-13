// screens.jsx — Sonar, Status, Terminal screens

const HUE_BY_THEME = { green: 155, amber: 75, blue: 220 };
const HUE_ALERT = 25; // Red — used when state.pendingRequest is active.

// Helper so every screen + canvas reads the same effective hue.
function getActiveHue(state, tweaks) {
  if (state && state.pendingRequest) return HUE_ALERT;
  return HUE_BY_THEME[tweaks.theme] ?? 155;
}

// ─────────────────────────────────────────────────────────────
// Peer alert overlay — large popup inside the main bezel when a
// nearby unit hails. Stays mounted (driven by `active` prop) so
// dismiss can fade out smoothly. Doesn't block input — the
// terminal still accepts `accept` / `decline`.
// ─────────────────────────────────────────────────────────────
function PeerAlertOverlay({ peer, type, active }) {
  if (!peer) return null;
  const label = type === 'breed' ? 'BREEDING REQUEST' : 'CHALLENGE INCOMING';
  return (
    <div className={`peer-alert${active ? ' active' : ''}`}>
      <div className="peer-alert-tint" />
      <div className="peer-alert-frame">
        <div className="peer-alert-corner tl" />
        <div className="peer-alert-corner tr" />
        <div className="peer-alert-corner bl" />
        <div className="peer-alert-corner br" />
        <div className="peer-alert-warn t-mono">▲ PROXIMITY ALERT ▲</div>
        <div className="peer-alert-title t-pixel glow">NEW CONTACT</div>
        <div className="peer-alert-sub t-mono">BIOLOGIC // PROXIMAL</div>
        <div className="peer-alert-divider" />
        <div className="peer-alert-meta t-mono">
          <div><span>UNIT</span><b>{peer.name}</b></div>
          <div><span>SPECIES</span><b>{peer.species.toUpperCase()}</b></div>
          <div><span>STAGE</span><b>{peer.stage.toUpperCase()}</b></div>
          <div><span>BRG/RNG</span><b>
            {String(Math.round(peer.bearing)).padStart(3, '0')}°
            {' / '}
            {String(Math.round(peer.range * 50)).padStart(2, '0')}m
          </b></div>
        </div>
        <div className="peer-alert-req t-mono">{label}</div>
        <div className="peer-alert-hint t-mono">
          ▸ terminal: <b>accept</b> · <b>decline</b>
        </div>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Shared CRT background layers
// ─────────────────────────────────────────────────────────────
function CRTLayers({ scanlines, noise, glowStrength }) {
  return (
    <>
      <div className="crt-bg" />
      <div className="crt-grid" />
      <div className="crt-ticks" />
      {scanlines && <div className="crt-scanlines" />}
      {scanlines && <div className="crt-scanband" />}
      {noise && <div className="crt-noise" />}
      <div className="crt-vignette" />
      <div className="crt-flicker" />
    </>
  );
}

// Target brackets at 4 quadrants (like in the references)
function TargetBrackets() {
  return (
    <>
      <div className="target-bracket" style={{ top: '28%', right: '14%' }}>
        <div className="dot" />
      </div>
      <div className="target-bracket" style={{ bottom: '22%', left: '32%' }}>
        <div className="dot" />
      </div>
      <div className="target-bracket" style={{ top: '12%', left: '8%' }}>
        <div className="dot" />
      </div>
    </>
  );
}

// ─────────────────────────────────────────────────────────────
// SONAR (main creature view)
// ─────────────────────────────────────────────────────────────
function SonarScreen({ width, height, state, dispatch, tweaks }) {
  const [scanProgress, setScanProgress] = React.useState(null);
  const [pinging, setPinging] = React.useState(false);
  const [statusLine, setStatusLine] = React.useState('CONTACT // CONTAINED');

  // Ping is now triggered from the terminal (`ping` / `scan`) via a nonce
  // bumped in the reducer. Each bump kicks off the local sweep animation.
  React.useEffect(() => {
    if (!state.pingNonce) return;
    setPinging(true);
    const start = performance.now();
    let raf;
    const tick = () => {
      const e = (performance.now() - start) / 1600;
      if (e >= 1) {
        setScanProgress(null);
        setPinging(false);
        return;
      }
      setScanProgress(e);
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    setStatusLine(['CONTACT // CONTAINED', 'TARGET LOCKED', 'BIO-SIGN POSITIVE', 'SIGNATURE STABLE'][Math.floor(Math.random()*4)]);
    return () => { if (raf) cancelAnimationFrame(raf); };
  }, [state.pingNonce]);

  // Mood label
  const moodLabel = (() => {
    if (state.asleep) return 'ASLEEP';
    if (state.evolving) return 'EVOLVING';
    if (state.disciplineFlash) return 'SCOLDED';
    if (state.dirty > 0.7) return 'DISTRESSED';
    if (state.hunger > 0.75) return 'HUNGRY';
    if (state.happiness < 0.3) return 'UNHAPPY';
    if (state.energy < 0.25) return 'DROWSY';
    return 'NOMINAL';
  })();

  const stageH = height - 70; // creature stage occupies everything below the top readout
  const stageW = width;

  return (
    <div className="crt-screen" style={{ width, height, position: 'relative' }}>
      <CRTLayers scanlines={tweaks.scanlines} noise={tweaks.noise} glowStrength={tweaks.crt} />

      {/* TOP READOUT */}
      <div style={{
        position: 'absolute', top: 0, left: 0, right: 0, height: 70,
        padding: '12px 14px 8px', zIndex: 20,
        borderBottom: '1px solid var(--phos-grid)',
      }}>
        <div className="hud-corner tl" />
        <div className="hud-corner tr" />
        <div className="readout-row">
          <span><b>SONAR</b> // CH.07</span>
          <span>FREQ <b>14.2KHZ</b></span>
        </div>
        <div className="readout-row" style={{ marginTop: 3 }}>
          <span>UNIT <b>{state.name}</b></span>
          <span>AGE <b>{state.age.toString().padStart(2, '0')}D</b></span>
        </div>
        <div style={{
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
          marginTop: 6,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span className="alive-dot" />
            <span className="t-pixel glow" style={{ fontSize: 17, letterSpacing: '0.05em' }}>
              {moodLabel}
            </span>
          </div>
          <span className="t-mono" style={{ fontSize: 9, color: 'var(--phos-mid)' }}>
            {statusLine}
          </span>
        </div>
      </div>

      {/* CREATURE STAGE */}
      <div style={{
        position: 'absolute', top: 70, left: 0, right: 0, height: stageH,
        overflow: 'hidden',
      }}>
        <SonarCreature
          width={stageW}
          height={stageH}
          species={tweaks.species}
          mood={{
            happiness: state.happiness,
            energy: state.energy,
            hunger: 1 - state.hunger,
            hygiene: 1 - state.dirty,
          }}
          scanProgress={scanProgress}
          asleep={state.asleep}
          hue={getActiveHue(state, tweaks)}
          pulseInterval={tweaks.pulse}
          decayTau={tweaks.decay}
        />

        <TargetBrackets />

        {/* readouts overlaid in the corners */}
        <div style={{
          position: 'absolute', bottom: 8, left: 12,
          fontFamily: 'JetBrains Mono', fontSize: 9,
          color: 'var(--phos-dim)', lineHeight: 1.5,
        }}>
          <div>DEPTH 0182M</div>
          <div>LAT 35°N // LON 129°E</div>
          <div>RNG 0024.7M</div>
        </div>
        <div style={{
          position: 'absolute', bottom: 8, right: 12,
          fontFamily: 'JetBrains Mono', fontSize: 9,
          color: 'var(--phos-dim)', textAlign: 'right', lineHeight: 1.5,
        }}>
          <div>EVO {state.stage.toUpperCase()}</div>
          <div>GEN {state.gen.toString().padStart(2, '0')}</div>
          <div>CYC {state.cycles.toString().padStart(4, '0')}</div>
        </div>

        {/* sleeping moons */}
        {state.asleep && (
          <div style={{
            position: 'absolute', top: '30%', left: '70%',
            fontFamily: 'VT323', fontSize: 28, color: 'var(--phos)',
            textShadow: '0 0 6px var(--phos)',
            animation: 'blink 2s steps(2) infinite',
          }}>z z z</div>
        )}

        {state.evolving && (
          <div style={{
            position: 'absolute', inset: 0,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            background: 'oklch(0.5 0.2 var(--hue) / 0.15)',
            zIndex: 6,
          }}>
            <div className="t-pixel glow" style={{
              fontSize: 32, letterSpacing: '0.15em',
              animation: 'blink 0.4s steps(2) infinite',
            }}>EVOLVING…</div>
          </div>
        )}

        {/* Toast */}
        {state.toast && (
          <div style={{
            position: 'absolute', top: 20, left: '50%', transform: 'translateX(-50%)',
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

      {pinging && <div className="ping-sweep run" />}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// LINEAGE (tree) screen — Linux `tree` style ancestry view.
// Each generation is a child of GENESIS/, with stat-files inside.
// The active gen highlights and shows live mood/bond/cycles; retired
// gens render in --phos-dim with a ✟ status.
// ─────────────────────────────────────────────────────────────
function TreeScreen({ width, height, state, dispatch, tweaks }) {
  const all = [
    ...state.lineage.map(e => ({ ...e, retired: true })),
    {
      gen: state.gen,
      name: state.name,
      stage: state.stage,
      cycles: state.cycles,
      happiness: Math.round(state.happiness * 100),
      bond: Math.round(state.bond * 100),
      hatchedAt: state.hatchedAt,
      retired: false,
    },
  ];
  const totalCycles = all.reduce((a, e) => a + e.cycles, 0);

  const fmtTime = (ms) => {
    if (!ms) return '----';
    const s = Math.max(0, Math.floor((Date.now() - ms) / 1000));
    if (s < 60) return `${s}s ago`;
    const m = Math.floor(s / 60);
    if (m < 60) return `${m}m ago`;
    const h = Math.floor(m / 60);
    return `${h}h ${m % 60}m ago`;
  };

  return (
    <div className="crt-screen" style={{ width, height, position: 'relative' }}>
      <CRTLayers scanlines={tweaks.scanlines} noise={tweaks.noise} glowStrength={tweaks.crt} />

      {/* TOP READOUT */}
      <div style={{
        position: 'absolute', top: 0, left: 0, right: 0, height: 70,
        padding: '12px 14px 8px', zIndex: 20,
        borderBottom: '1px solid var(--phos-grid)',
      }}>
        <div className="hud-corner tl" />
        <div className="hud-corner tr" />
        <div className="readout-row">
          <span><b>LINEAGE</b> // ARCHIVE</span>
          <span>NODES <b>{all.length}</b></span>
        </div>
        <div className="readout-row" style={{ marginTop: 3 }}>
          <span>ROOT <b>GENESIS/</b></span>
          <span>CYC <b>{String(totalCycles).padStart(4, '0')}</b></span>
        </div>
        <div style={{
          display: 'flex', alignItems: 'center', gap: 6, marginTop: 6,
        }}>
          <span className="alive-dot" />
          <span className="t-pixel glow" style={{ fontSize: 17, letterSpacing: '0.05em' }}>
            ANCESTRY
          </span>
          <span className="t-mono" style={{ fontSize: 8, color: 'var(--phos-mid)', marginLeft: 6 }}>
            ▸ tree --gen --depth=2
          </span>
        </div>
      </div>

      {/* TREE BODY */}
      <div className="no-scrollbar" style={{
        position: 'absolute', top: 70, left: 0, right: 0, bottom: 0,
        padding: '12px 12px 28px',
        fontFamily: 'JetBrains Mono, monospace', fontSize: 10.5,
        lineHeight: 1.6, color: 'var(--phos)', overflow: 'auto',
        whiteSpace: 'pre',
      }}>
        <div className="hud-corner bl" />
        <div className="hud-corner br" />

        <div style={{ color: 'var(--phos)' }} className="glow-soft">
          <span style={{ color: 'var(--phos-mid)' }}>$</span> tree GENESIS/
        </div>
        <div style={{ height: 4 }} />
        <div style={{ color: 'var(--phos)' }}>GENESIS/</div>

        {all.map((e, i) => {
          const last = i === all.length - 1;
          const branch = last ? '└── ' : '├── ';
          const cont = last ? '    ' : '│   ';
          return (
            <React.Fragment key={i}>
              <div style={{
                color: e.retired ? 'var(--phos-dim)' : 'var(--phos)',
                textShadow: e.retired ? 'none' : '0 0 4px var(--phos-mid)',
              }}>
                <span style={{ color: 'var(--phos-mid)' }}>{branch}</span>
                <span style={{ color: e.retired ? 'var(--phos-dim)' : 'var(--phos-mid)' }}>
                  G{String(e.gen).padStart(2, '0')}_
                </span>
                {e.name}/
                {!e.retired && (
                  <span style={{ color: 'var(--phos)', marginLeft: 10, fontFamily: 'VT323' }}>
                    ◀ ACTIVE
                  </span>
                )}
              </div>
              <TreeField cont={cont} k="stage     " v={e.stage} dim={e.retired} />
              <TreeField cont={cont} k="cycles    " v={String(e.cycles).padStart(4, '0')} dim={e.retired} />
              {e.retired ? (
                <>
                  <TreeField cont={cont} k="last-mood " v={`${e.happiness}%`} dim={true} />
                  <TreeField cont={cont} k="bond      " v={`${e.bond}%`} dim={true} />
                  <TreeField
                    cont={cont}
                    k="archived  "
                    v={fmtTime(e.archivedAt)}
                    dim={true}
                  />
                  <TreeField
                    cont={cont}
                    k="status    "
                    v="✟ retired"
                    dim={true}
                    last
                  />
                </>
              ) : (
                <>
                  <TreeField cont={cont} k="happiness " v={`${e.happiness}%`} />
                  <TreeField cont={cont} k="bond      " v={`${e.bond}%`} />
                  <TreeField cont={cont} k="hatched   " v={fmtTime(e.hatchedAt)} />
                  <TreeField cont={cont} k="status    " v="● alive" alive last />
                </>
              )}
              {!last && <div style={{ color: 'var(--phos-mid)' }}>│</div>}
            </React.Fragment>
          );
        })}

        <div style={{ height: 10 }} />
        <div style={{ color: 'var(--phos-mid)' }}>
          ─────────────────────────────────────
        </div>
        <div style={{ color: 'var(--phos-dim)' }}>
          {all.length} {all.length === 1 ? 'directory' : 'directories'},{' '}
          {totalCycles} cycles total
        </div>

        <div style={{ marginTop: 10, color: 'var(--phos-dim)', fontSize: 9 }}>
          ▸ type <span style={{ color: 'var(--phos-mid)' }}>sonar</span> in terminal to return
        </div>
      </div>

      {/* Toast — same overlay UX as sonar so feedback is consistent */}
      {state.toast && (
        <div style={{
          position: 'absolute', top: 80, left: '50%', transform: 'translateX(-50%)',
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

function TreeField({ cont, k, v, dim, alive, last }) {
  const branch = last ? '└── ' : '├── ';
  return (
    <div style={{ color: 'var(--phos-mid)' }}>
      {cont}<span style={{ color: 'var(--phos-mid)' }}>{branch}</span>
      <span style={{ color: dim ? 'var(--phos-dim)' : 'var(--phos-mid)' }}>{k}</span>
      <span style={{
        color: dim ? 'var(--phos-dim)' : 'var(--phos)',
        textShadow: alive ? '0 0 4px var(--phos)' : 'none',
      }}>
        {v}
      </span>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// TERMINAL screen
// ─────────────────────────────────────────────────────────────
const COMMANDS = {
  help: () => [
    'AVAILABLE COMMANDS:',
    '  status        — vitals readout (boxed)',
    '  scan / peers  — radar sweep + nearby unit list',
    '  ping          — sonar pulse on current view',
    '  tree          — view lineage archive',
    '  sonar / back  — return to sonar view',
    '  bond <name>   — peer-bond + battle record',
    '  challenge <n> — challenge nearby unit to battle',
    '  accept        — accept incoming request',
    '  decline       — dismiss incoming request',
    '  flee          — disengage from current battle',
    '  feed [item]   — feed creature',
    '  play          — happiness +',
    '  clean         — hygiene +',
    '  sleep / wake  — toggle sleep',
    '  train         — training +',
    '  scold         — discipline +',
    '  heal          — apply biopatch',
    '  evolve        — advance stage',
    '  talk          — converse',
    '  mute          — toggle audio',
    '  name <str>    — rename unit',
    '  whoami        — operator info',
    '  history       — show log',
    '  clear         — clear screen',
    '  reset         — archive + new egg',
  ],
  whoami: () => [
    'OPERATOR_ID: NAUTILUS-7',
    'CLEARANCE: TIER 3 — CARETAKER',
    'STATION: CIC // ABYSSAL OBSERVATION POST',
  ],
};

function TerminalScreen({ width, height, state, dispatch, tweaks }) {
  const [history, setHistory] = React.useState([
    { t: 'sys', text: '◢◤ NAUTILUS // ABYSSAL OBS POST v3.2.1' },
    { t: 'sys', text: '◢◤ CARE TERMINAL / SECURE LINK ESTABLISHED' },
    { t: 'sys', text: '' },
    { t: 'out', text: `unit ${state.name} acquired — ${state.stage} stage` },
    { t: 'out', text: 'type `help` to list commands.' },
    { t: 'out', text: '' },
  ]);
  const [input, setInput] = React.useState('');
  const [cmdHistory, setCmdHistory] = React.useState([]);
  const [cmdIdx, setCmdIdx] = React.useState(-1);
  const scrollRef = React.useRef(null);
  const inputRef = React.useRef(null);
  // Track which peer-event nonces we've already echoed so re-renders don't
  // duplicate them.
  const lastEventNonceRef = React.useRef(state.peerEventNonce);

  React.useEffect(() => {
    if (state.peerEventNonce === lastEventNonceRef.current) return;
    lastEventNonceRef.current = state.peerEventNonce;
    const ev = state.peerEventLatest;
    if (!ev) return;
    const tagByKind = { challenge: 'sys', friendly: 'out', accept: 'out', decline: 'out' };
    const t = tagByKind[ev.kind] || 'out';
    setHistory((h) => [...h, ...ev.lines.map((text) => ({ t, text }))]);
  }, [state.peerEventNonce]);

  React.useEffect(() => {
    if (scrollRef.current) scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
  }, [history]);

  const focus = () => inputRef.current?.focus();

  const append = (lines) => {
    setHistory(h => [...h, ...lines.map(l => typeof l === 'string' ? { t: 'out', text: l } : l)]);
  };

  const runCommand = (raw) => {
    const cmd = raw.trim();
    if (!cmd) return;
    setCmdHistory(c => [cmd, ...c].slice(0, 30));
    setCmdIdx(-1);
    const lines = [{ t: 'in', text: cmd }];
    const [verb, ...args] = cmd.toLowerCase().split(/\s+/);

    const respond = (out) => {
      setHistory(h => [...h, ...lines, ...out.map(l => typeof l === 'string' ? { t: 'out', text: l } : l)]);
    };

    // During battle, only a handful of commands make sense. Everything else
    // gets a polite "in battle" gate so the player can't accidentally feed
    // the creature mid-engagement.
    const inBattle = !!state.battle;
    const BATTLE_ALLOWED = new Set([
      'flee', 'forfeit', 'help', 'whoami', 'clear', 'echo', 'mute', 'sound',
    ]);
    if (inBattle && !BATTLE_ALLOWED.has(verb)) {
      respond([`${verb}: locked — engagement in progress. (\`flee\` to disengage)`]);
      return;
    }

    switch (verb) {
      case 'help': respond(COMMANDS.help()); break;
      case 'whoami': respond(COMMANDS.whoami()); break;
      case 'status': {
        const bar = (v, w = 12) => {
          const filled = Math.round(Math.max(0, Math.min(1, v)) * w);
          return '█'.repeat(filled) + '░'.repeat(w - filled);
        };
        const pct = (v) => `${String(Math.round(Math.max(0, Math.min(1, v)) * 100)).padStart(3, ' ')}%`;
        respond([
          '─── UNIT ──────────────────────────────',
          `  name        ${state.name}`,
          `  stage       ${state.stage.toUpperCase()}  · gen ${String(state.gen).padStart(2, '0')}`,
          `  cycles      ${String(state.cycles).padStart(4, '0')}` + (state.asleep ? '   [sleeping]' : ''),
          '',
          '─── MOOD ──────────────────────────────',
          `  happiness   ${bar(state.happiness)}  ${pct(state.happiness)}`,
          `  energy      ${bar(state.energy)}  ${pct(state.energy)}`,
          '',
          '─── CARE ──────────────────────────────',
          `  fed         ${bar(1 - state.hunger)}  ${pct(1 - state.hunger)}`,
          `  clean       ${bar(1 - state.dirty)}  ${pct(1 - state.dirty)}`,
          '',
          '─── TRAINING ──────────────────────────',
          `  bond        ${bar(state.bond)}  ${pct(state.bond)}`,
          `  training    ${bar(state.training)}  ${pct(state.training)}`,
          `  discipline  ${bar(state.discipline)}  ${pct(state.discipline)}`,
          '',
          '─── EVOLUTION ─────────────────────────',
          `  progress    ${bar(state.evolveProgress)}  ${pct(state.evolveProgress)}` +
            (state.canEvolve ? '   ◀ READY' : ''),
        ]);
        break;
      }
      case 'tree': {
        dispatch({ type: 'setView', view: 'tree' });
        const archived = state.lineage.length;
        respond([
          '$ tree GENESIS/',
          `▸ archive contains ${archived} retired ${archived === 1 ? 'generation' : 'generations'} + 1 active.`,
          '▸ lineage rendered on primary display.',
          '  (type `sonar` to return)',
        ]);
        break;
      }
      case 'sonar': case 'back': {
        if (state.view === 'sonar') {
          respond(['▸ already on sonar.']);
        } else {
          dispatch({ type: 'setView', view: 'sonar' });
          respond(['▸ returning to sonar.']);
        }
        break;
      }
      case 'scan': case 'peers': case 'radar': {
        const wasOnRadar = state.view === 'radar';
        if (!wasOnRadar) dispatch({ type: 'setView', view: 'radar' });
        const lines = [
          '$ scan --peers @ 14.2kHz',
          `▸ ${state.peers.length} contacts detected.`,
          ...state.peers
            .slice()
            .sort((p1, p2) => p1.range - p2.range)
            .map((p) => {
              const tag = p.id.toUpperCase().padEnd(7);
              const nm = p.name.padEnd(8);
              const sp = p.species.padEnd(5);
              const st = p.stage.slice(0, 4).padEnd(4);
              const brg = String(Math.round(p.bearing)).padStart(3, '0');
              const rng = String(Math.round(p.range * 50)).padStart(2, '0');
              return `  [${tag}] ${nm} · ${sp} · ${st} · brg ${brg}° · rng ${rng}m`;
            }),
          '',
          wasOnRadar
            ? '▸ sweep continuing on primary display.'
            : '▸ radar scope active. type `sonar` to return.',
        ];
        respond(lines);
        break;
      }
      case 'ping': {
        // Keep the legacy ping behavior (sonar sweep) bound to its own verb.
        dispatch({ type: 'ping' });
        respond([
          'transmitting sonar pulse @ 14.2kHz...',
          '  ▸ contact at bearing 270°, range 24.7m',
          '  ▸ biomass signature: ORGANIC // CONFINED',
          `  ▸ unit responsive — ${state.asleep ? 'asleep' : 'active'}`,
          'PING_OK.',
        ]);
        break;
      }
      case 'accept': {
        if (!state.pendingRequest) {
          respond(['no incoming request.']);
        } else {
          const peer = state.peers.find((p) => p.id === state.pendingRequest.from);
          if (state.pendingRequest.type === 'challenge') {
            respond([
              `▸ accepted ${peer?.name || ''}'s challenge.`,
              '  ENGAGE — switching to combat console.',
            ]);
            dispatch({ type: 'battleStart', peerId: state.pendingRequest.from });
          } else {
            // Other request types (e.g. future breed) reuse the v1 accept stub
            dispatch({ type: 'acceptRequest' });
          }
        }
        break;
      }
      case 'challenge': {
        const target = (args[0] || '').toLowerCase();
        if (!target) {
          respond(['usage: challenge <name>']);
          break;
        }
        const peer = state.peers.find(
          (p) => p.name.toLowerCase() === target || p.id === target,
        );
        if (!peer) {
          respond([`no peer named ${args[0]}.`]);
          break;
        }
        // Aggressive personalities accept readily; gentle ones often decline.
        const acceptOdds = {
          aggressive: 0.85, playful: 0.65, veteran: 0.55, gentle: 0.30,
        }[peer.personality] ?? 0.5;
        if (Math.random() < acceptOdds) {
          respond([
            `▸ hailing ${peer.name}...`,
            `▸ ${peer.name} accepts. ENGAGE.`,
          ]);
          dispatch({ type: 'battleStart', peerId: peer.id });
        } else {
          respond([
            `▸ hailing ${peer.name}...`,
            `▸ ${peer.name} drifts away. (challenge declined)`,
          ]);
        }
        break;
      }
      case 'flee': case 'forfeit': {
        if (!state.battle) {
          respond(['no active engagement to flee from.']);
        } else {
          respond(['▸ disengaging — pulse withdrawn.']);
          dispatch({ type: 'battleFlee' });
        }
        break;
      }
      case 'decline': {
        if (!state.pendingRequest) {
          respond(['no incoming request.']);
        } else {
          dispatch({ type: 'declineRequest' });
          respond([]);
        }
        break;
      }
      case 'bond': {
        const target = (args[0] || '').toLowerCase();
        if (!target) {
          respond(['usage: bond <name>']);
          break;
        }
        const peer = state.peers.find(
          (p) => p.name.toLowerCase() === target || p.id === target,
        );
        if (!peer) {
          respond([`no peer named ${args[0]}.`]);
          break;
        }
        const w = peer.battlesWon || 0;
        const l = peer.battlesLost || 0;
        const bondPct = Math.round(peer.bond * 100);
        const barW = 16;
        const filled = Math.round(peer.bond * barW);
        const bar = '█'.repeat(filled) + '░'.repeat(barW - filled);
        respond([
          `${peer.name} — ${peer.species} · ${peer.stage} · ${peer.personality}`,
          `  bond     [${bar}]  ${bondPct}%`,
          `  record   ${w}W / ${l}L`,
          `  bearing  ${String(Math.round(peer.bearing)).padStart(3, '0')}°`,
          `  range    ${String(Math.round(peer.range * 50)).padStart(2, '0')}m`,
          peer.bond >= 0.7
            ? '  status   ◀ BREED-ELIGIBLE'
            : `  status   bond ≥ 70% required to breed`,
        ]);
        break;
      }
      case 'feed':
        dispatch({ type: 'feed' });
        respond([`dispensing ${args[0] || 'standard ration'}...`, '▸ unit fed. hunger -25%.']);
        break;
      case 'play':
        dispatch({ type: 'play' });
        respond(['initiating play sequence... ✓', '▸ happiness +20%.']);
        break;
      case 'clean':
        dispatch({ type: 'clean' });
        respond(['flushing tank... ✓', '▸ hygiene restored.']);
        break;
      case 'sleep': case 'wake':
        dispatch({ type: 'sleep' });
        respond([state.asleep ? '▸ unit awakened.' : '▸ lights out. unit asleep.']);
        break;
      case 'train':
        dispatch({ type: 'train' });
        respond(['running drill protocol...', '▸ training +15%. discipline +5%.']);
        break;
      case 'scold': case 'discipline':
        dispatch({ type: 'discipline' });
        respond(['issuing reprimand...', '▸ discipline +10%. happiness -8%.']);
        break;
      case 'heal':
        dispatch({ type: 'heal' });
        respond(['applying biopatch...', '▸ energy +30%. happiness +5%.']);
        break;
      case 'mute': case 'sound':
        dispatch({ type: 'toggleSound' });
        respond([state.sound ? '▸ audio muted.' : '▸ audio enabled.']);
        break;
      case 'evolve':
        if (state.canEvolve) {
          dispatch({ type: 'evolve' });
          respond(['◢◤ EVOLUTION SEQUENCE INITIATED ◢◤', 'morphological flux detected.', 'standby...']);
        } else {
          respond([`evolution unavailable. progress ${Math.round(state.evolveProgress*100)}%.`]);
        }
        break;
      case 'talk':
        respond([
          `${state.name} > ${tamagotchiTalk(state)}`,
        ]);
        break;
      case 'name':
        if (args[0]) {
          dispatch({ type: 'rename', name: args.join(' ').toUpperCase().slice(0, 12) });
          respond([`▸ unit renamed: ${args.join(' ').toUpperCase().slice(0, 12)}`]);
        } else respond(['usage: name <string>']);
        break;
      case 'history':
        respond(state.log.slice(0, 12).map(e => `[${e.t.toString().padStart(4,'0')}] ${e.msg}`));
        break;
      case 'clear':
        setHistory([{ t: 'sys', text: '◢◤ TERMINAL CLEARED' }]);
        return;
      case 'reset':
        dispatch({ type: 'reset' });
        respond(['◢◤ NEW EGG INCUBATING ◢◤']);
        break;
      case 'echo':
        respond([args.join(' ')]);
        break;
      case 'ls': case 'dir':
        respond(['./bin/', './log/', './unit.dat', './telemetry.dat', './notes.txt']);
        break;
      case 'cat':
        if (args[0] === 'notes.txt') {
          respond([
            '— field notes —',
            'subj. responds to pings between 3-7s intervals.',
            'avoids glare from main reflector. likes warm currents.',
            'last molt produced 3.4g organic dust.',
          ]);
        } else respond([`cat: ${args[0]}: no such file`]);
        break;
      default:
        respond([`${verb}: command not found. try \`help\`.`]);
    }
  };

  const onKey = (e) => {
    if (e.key === 'Enter') {
      runCommand(input);
      setInput('');
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      const next = Math.min(cmdIdx + 1, cmdHistory.length - 1);
      setCmdIdx(next);
      if (cmdHistory[next] !== undefined) setInput(cmdHistory[next]);
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      const next = Math.max(cmdIdx - 1, -1);
      setCmdIdx(next);
      setInput(next === -1 ? '' : cmdHistory[next]);
    }
  };

  return (
    <div className="crt-screen" style={{ width, height, position: 'relative' }} onClick={focus}>
      <CRTLayers scanlines={tweaks.scanlines} noise={tweaks.noise} glowStrength={tweaks.crt} />

      <div style={{ position: 'absolute', inset: 0, padding: '14px 12px 8px', display: 'flex', flexDirection: 'column' }}>
        <div className="hud-corner tl" />
        <div className="hud-corner tr" />

        {/* header */}
        <div style={{ marginBottom: 8 }}>
          <div className="readout-row">
            <span><b>TERMINAL</b> // tty0</span>
            <span>
              {state.pendingRequest ? (
                <>
                  <span className="alert-dot" />
                  <b style={{ color: 'var(--phos)' }}>
                    {(state.peers.find((p) => p.id === state.pendingRequest.from)?.name) || 'PEER'} HAILS
                  </b>
                </>
              ) : (
                <>
                  <span
                    className="alive-dot"
                    style={{ display: 'inline-block', verticalAlign: 'middle', marginRight: 4 }}
                  />{' '}
                  SECURE
                </>
              )}
            </span>
          </div>
          <div style={{
            height: 1, background: 'var(--phos-grid)', margin: '6px 0',
          }} />
        </div>

        {/* output */}
        <div ref={scrollRef} className="no-scrollbar" style={{
          flex: 1, overflow: 'auto', display: 'flex', flexDirection: 'column', gap: 1,
        }}>
          {history.map((h, i) => (
            <div key={i} className={'term-line ' + (h.t === 'sys' ? 'dim' : h.t === 'in' ? '' : 'dim')}>
              {h.t === 'in' ? (
                <><span style={{ color: 'var(--phos-mid)' }}>{state.name.toLowerCase()}@nautilus:~$</span> {h.text}</>
              ) : (
                h.text || '\u00A0'
              )}
            </div>
          ))}
          <div className="term-line" style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <span style={{ color: 'var(--phos-mid)' }}>{state.name.toLowerCase()}@nautilus:~$</span>
            <span style={{ position: 'relative', flex: 1, display: 'flex', alignItems: 'center' }}>
              <span>{input}</span>
              <span className="term-cursor" style={{ marginLeft: 1 }} />
              <input
                ref={inputRef}
                type="text"
                autoFocus
                value={input}
                onChange={e => setInput(e.target.value)}
                onKeyDown={onKey}
                style={{
                  position: 'absolute', inset: 0, opacity: 0,
                  width: '100%', background: 'transparent', border: 'none',
                  color: 'transparent', caretColor: 'transparent', outline: 'none',
                }}
              />
            </span>
          </div>
        </div>

        {/* footer hint */}
        <div className="readout-row" style={{ marginTop: 6, fontSize: 8 }}>
          <span>↑↓ history</span>
          <span>type `help`</span>
          <span>UPLINK 99.7%</span>
        </div>
      </div>
    </div>
  );
}

function tamagotchiTalk(state) {
  if (state.asleep) return 'zzZZ... (do not disturb)';
  if (state.hunger > 0.7) return 'i hear something... is that food? i hope so.';
  if (state.dirty > 0.7) return 'the water is murky. could you flush the tank?';
  if (state.happiness < 0.3) return 'it has been a long shift. i miss you.';
  if (state.energy < 0.3) return 'tired... maybe a quick rest?';
  if (state.stage === 'egg') return 'tap... tap... tap... [muffled]';
  const lines = [
    'do you ever wonder where the signal goes?',
    'i counted 1,440 pings today. i counted them all.',
    'the reef hums at 27 hertz. i hum back.',
    'i think i saw a shape. it had nine sides.',
    'is the operator there? i sensed you above.',
    'thank you for staying.',
  ];
  return lines[Math.floor(Math.random() * lines.length)];
}

Object.assign(window, {
  SonarScreen, TerminalScreen, TreeScreen, PeerAlertOverlay,
  HUE_BY_THEME, HUE_ALERT, getActiveHue,
});
