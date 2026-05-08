// screens.jsx — Sonar, Status, Terminal screens

const HUE_BY_THEME = { green: 155, amber: 75, blue: 220 };

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
          hue={HUE_BY_THEME[tweaks.theme]}
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
// TERMINAL screen
// ─────────────────────────────────────────────────────────────
const COMMANDS = {
  help: () => [
    'AVAILABLE COMMANDS:',
    '  status        — vitals readout',
    '  scan / ping   — sonar sweep',
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
    '  reset         — new egg',
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

    switch (verb) {
      case 'help': respond(COMMANDS.help()); break;
      case 'whoami': respond(COMMANDS.whoami()); break;
      case 'status':
        respond([
          `unit       ${state.name}`,
          `stage      ${state.stage} // gen ${state.gen.toString().padStart(2,'0')}`,
          `happiness  ${'█'.repeat(Math.round(state.happiness*10)).padEnd(10,'·')}  ${Math.round(state.happiness*100)}%`,
          `energy     ${'█'.repeat(Math.round(state.energy*10)).padEnd(10,'·')}  ${Math.round(state.energy*100)}%`,
          `fed        ${'█'.repeat(Math.round((1-state.hunger)*10)).padEnd(10,'·')}  ${Math.round((1-state.hunger)*100)}%`,
          `clean      ${'█'.repeat(Math.round((1-state.dirty)*10)).padEnd(10,'·')}  ${Math.round((1-state.dirty)*100)}%`,
        ]);
        break;
      case 'scan': case 'ping':
        dispatch({ type: 'ping' });
        respond([
          'transmitting sonar pulse @ 14.2kHz...',
          '  ▸ contact at bearing 270°, range 24.7m',
          '  ▸ biomass signature: ORGANIC // CONFINED',
          `  ▸ unit responsive — ${state.asleep ? 'asleep' : 'active'}`,
          'PING_OK.',
        ]);
        break;
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
            <span><span className="alive-dot" style={{ display: 'inline-block', verticalAlign: 'middle', marginRight: 4 }} /> SECURE</span>
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
  SonarScreen, TerminalScreen, HUE_BY_THEME,
});
