// app.jsx — Sonar Tamagotchi root + state machine

const TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
  "theme": "green",
  "crt": 0.7,
  "species": "ghost",
  "scanlines": true,
  "noise": true,
  "sound": false,
  "bezel": "military",
  "pulse": 5,
  "decay": 1
}/*EDITMODE-END*/;

const STAGES = ['egg', 'larva', 'juvenile', 'adult'];

const NAMES = ['NAUTI', 'KAIJU', 'BLEEP', 'MORSE', 'PROBE', 'KRILL'];

function initialState() {
  return {
    name: NAMES[Math.floor(Math.random() * NAMES.length)],
    age: 0,           // days
    cycles: 0,        // care-cycles
    gen: 1,
    stage: 'egg',
    happiness: 0.6,
    energy: 0.7,
    hunger: 0.45,     // 0=full, 1=starving
    dirty: 0.3,       // 0=clean, 1=filthy
    bond: 0.4,
    training: 0.1,
    discipline: 0.2,
    asleep: false,
    evolveProgress: 0,
    canEvolve: false,
    evolving: false,
    disciplineFlash: false,
    pingNonce: 0,
    log: [],
    toast: null,
    sound: false,
  };
}

const clamp = (v) => Math.max(0, Math.min(1, v));

function reduce(s, a) {
  const log = (msg) => [{ t: s.cycles + 1, msg }, ...s.log].slice(0, 20);
  const flash = (msg, ms = 1400) => {
    setTimeout(() => __setToast(null), ms);
    return msg;
  };
  switch (a.type) {
    case 'tick': {
      // Slow drift over time
      const dt = a.dt;
      let happiness = s.happiness;
      let energy = s.energy;
      let hunger = s.hunger;
      let dirty = s.dirty;
      if (s.asleep) {
        energy = clamp(energy + 0.02 * dt);
        hunger = clamp(hunger + 0.005 * dt);
      } else {
        hunger = clamp(hunger + 0.012 * dt);
        dirty = clamp(dirty + 0.008 * dt);
        energy = clamp(energy - 0.01 * dt);
        if (hunger > 0.7 || dirty > 0.7) happiness = clamp(happiness - 0.015 * dt);
      }
      const evolveProgress = clamp(s.evolveProgress + 0.005 * dt);
      const canEvolve = STAGES.indexOf(s.stage) < STAGES.length - 1 && evolveProgress >= 1;
      return { ...s, happiness, energy, hunger, dirty, evolveProgress, canEvolve };
    }
    case 'ping':
      return {
        ...s,
        cycles: s.cycles + 1,
        bond: clamp(s.bond + 0.03),
        pingNonce: s.pingNonce + 1,
      };
    case 'feed':
      return {
        ...s,
        cycles: s.cycles + 1,
        hunger: clamp(s.hunger - 0.25),
        happiness: clamp(s.happiness + 0.05),
        dirty: clamp(s.dirty + 0.03),
        log: log('FEED — ration dispensed'),
        toast: 'NOM NOM',
      };
    case 'play':
      return {
        ...s,
        cycles: s.cycles + 1,
        happiness: clamp(s.happiness + 0.2),
        energy: clamp(s.energy - 0.08),
        hunger: clamp(s.hunger + 0.04),
        bond: clamp(s.bond + 0.04),
        log: log('PLAY — bond sequence'),
        toast: 'YIPPEE',
      };
    case 'clean':
      return {
        ...s,
        cycles: s.cycles + 1,
        dirty: 0,
        happiness: clamp(s.happiness + 0.05),
        log: log('CLEAN — tank flushed'),
        toast: 'TANK FLUSHED',
      };
    case 'sleep':
      return {
        ...s,
        cycles: s.cycles + 1,
        asleep: !s.asleep,
        log: log(s.asleep ? 'WAKE' : 'SLEEP — lights out'),
        toast: s.asleep ? 'AWAKE' : 'GOOD NIGHT',
      };
    case 'train':
      return {
        ...s,
        cycles: s.cycles + 1,
        training: clamp(s.training + 0.15),
        discipline: clamp(s.discipline + 0.05),
        energy: clamp(s.energy - 0.08),
        hunger: clamp(s.hunger + 0.04),
        evolveProgress: clamp(s.evolveProgress + 0.04),
        log: log('TRAIN — drill complete'),
        toast: 'DRILL OK',
      };
    case 'discipline':
      return {
        ...s,
        cycles: s.cycles + 1,
        discipline: clamp(s.discipline + 0.1),
        happiness: clamp(s.happiness - 0.08),
        bond: clamp(s.bond - 0.02),
        log: log('SCOLD — reprimand'),
        toast: 'SCOLDED',
      };
    case 'heal':
      return {
        ...s,
        cycles: s.cycles + 1,
        energy: clamp(s.energy + 0.3),
        happiness: clamp(s.happiness + 0.05),
        log: log('HEAL — biopatch'),
        toast: 'PATCHED',
      };
    case 'evolve': {
      const i = STAGES.indexOf(s.stage);
      if (i >= STAGES.length - 1) return s;
      return {
        ...s,
        evolving: true,
        toast: 'EVOLVING',
      };
    }
    case 'evolveComplete': {
      const i = STAGES.indexOf(s.stage);
      const next = STAGES[Math.min(i + 1, STAGES.length - 1)];
      return {
        ...s,
        cycles: s.cycles + 1,
        stage: next,
        evolveProgress: 0,
        canEvolve: false,
        evolving: false,
        log: log(`EVOLVE — ${next.toUpperCase()} stage`),
        toast: `→ ${next.toUpperCase()}`,
      };
    }
    case 'rename':
      return { ...s, name: a.name, log: log('RENAME — ' + a.name) };
    case 'reset':
      return { ...initialState(), gen: s.gen + 1 };
    case 'toggleSound':
      return { ...s, sound: !s.sound, toast: s.sound ? 'MUTED' : 'SOUND ON' };
    case 'toast':
      return { ...s, toast: a.msg };
    default:
      return s;
  }
}

let __setToast = () => {};

// ─────────────────────────────────────────────────────────────
// Bezel — wraps the inner CRT in a physical-feeling housing
// ─────────────────────────────────────────────────────────────
function Bezel({ variant, children, label }) {
  if (variant === 'minimal') {
    return (
      <div style={{
        background: '#000',
        padding: 4,
        border: '1px solid #1a1a1a',
        borderRadius: 4,
      }}>
        {children}
      </div>
    );
  }

  const colors = variant === 'vintage'
    ? {
        outer: 'linear-gradient(180deg, #4a3d2c 0%, #2e261c 100%)',
        bolt: '#1a140d',
        text: '#9d8867',
        innerBorder: '#1a1208',
      }
    : {
        outer: 'linear-gradient(180deg, #2c2f26 0%, #16180f 100%)',
        bolt: '#0a0c08',
        text: '#7a8068',
        innerBorder: '#080a05',
      };

  return (
    <div style={{
      background: colors.outer,
      padding: '12px 10px',
      borderRadius: 8,
      position: 'relative',
      boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.05), inset 0 -1px 0 rgba(0,0,0,0.4), 0 0 0 1px rgba(0,0,0,0.6)',
    }}>
      {/* bolts */}
      {[[6, 6], [null, 6, 6, null], [6, null, null, 6]].slice(0, 1).map(() => null)}
      <div style={boltStyle(colors.bolt, 6, 6)} />
      <div style={boltStyle(colors.bolt, 6, null, null, 6)} />
      <div style={boltStyle(colors.bolt, null, 6, 6, null)} />
      <div style={boltStyle(colors.bolt, null, null, 6, 6)} />

      {/* label strip */}
      <div style={{
        textAlign: 'center',
        fontFamily: 'JetBrains Mono, monospace',
        fontSize: 8,
        letterSpacing: '0.3em',
        color: colors.text,
        textTransform: 'uppercase',
        marginBottom: 6,
        marginTop: 2,
      }}>
        {label}
      </div>

      <div style={{
        border: `2px solid ${colors.innerBorder}`,
        borderRadius: 3,
        background: '#000',
        padding: 0,
        overflow: 'hidden',
      }}>
        {children}
      </div>

      {/* indicator LEDs */}
      <div style={{
        display: 'flex', gap: 5, justifyContent: 'flex-end',
        marginTop: 6, marginRight: 4,
      }}>
        <Led color="oklch(0.85 0.22 var(--hue))" />
        <Led color="oklch(0.85 0.22 35)" off />
      </div>
    </div>
  );
}

function boltStyle(color, t, l, b, r) {
  return {
    position: 'absolute',
    width: 5, height: 5, borderRadius: '50%',
    background: color,
    boxShadow: 'inset 0 1px 1px rgba(0,0,0,0.5), 0 1px 0 rgba(255,255,255,0.1)',
    top: t, left: l, bottom: b, right: r,
  };
}

function Led({ color, off }) {
  return (
    <div style={{
      width: 5, height: 5, borderRadius: '50%',
      background: off ? 'rgba(80,40,40,0.5)' : color,
      boxShadow: off ? 'none' : `0 0 4px ${color}`,
    }} />
  );
}

// ─────────────────────────────────────────────────────────────
// App
// ─────────────────────────────────────────────────────────────
function App() {
  const [t, setTweak] = useTweaks(TWEAK_DEFAULTS);
  const [state, dispatch] = React.useReducer(reduce, null, initialState);

  React.useEffect(() => {
    document.documentElement.style.setProperty('--hue', HUE_BY_THEME[t.theme]);
    document.documentElement.style.setProperty('--scan-strength', t.scanlines ? 0.18 * t.crt : 0);
    document.documentElement.style.setProperty('--noise-strength', t.noise ? 0.12 * t.crt : 0);
    document.documentElement.style.setProperty('--glow-strength', t.crt);
  }, [t.theme, t.scanlines, t.noise, t.crt]);

  // toast clearing
  __setToast = (msg) => dispatch({ type: 'toast', msg });
  React.useEffect(() => {
    if (state.toast) {
      const id = setTimeout(() => dispatch({ type: 'toast', msg: null }), 1400);
      return () => clearTimeout(id);
    }
  }, [state.toast]);

  // Tick — drift over time
  React.useEffect(() => {
    const id = setInterval(() => dispatch({ type: 'tick', dt: 1 }), 1500);
    return () => clearInterval(id);
  }, []);

  // Evolution sequence
  React.useEffect(() => {
    if (state.evolving) {
      const id = setTimeout(() => dispatch({ type: 'evolveComplete' }), 2200);
      return () => clearTimeout(id);
    }
  }, [state.evolving]);

  // iPhone (402 × 874) — status bar 60, header 32, bezel pad-top 8,
  // bezel chrome ~57, 1:1 inner 362, terminal pad-top 6, home indicator 34.
  // Sum: 60+32+8+57+362+6+terminalH+34 = 874  →  terminalH = 315
  const SCREEN_W = 402;
  const SCREEN_H = 874;
  const TOP = 60;
  const innerSquare = 362;
  const terminalW = SCREEN_W - 16;
  const terminalH = 315;

  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <IOSDevice width={SCREEN_W} height={SCREEN_H} dark>
        <div style={{
          position: 'absolute', inset: 0, paddingTop: TOP,
          display: 'flex', flexDirection: 'column',
        }}>
          {/* Header strip */}
          <div style={{
            padding: '4px 14px 6px',
            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
            borderBottom: '1px solid var(--phos-grid)',
            background: '#000',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
              <span className="alive-dot" />
              <span className="t-pixel glow" style={{ fontSize: 18, letterSpacing: '0.1em' }}>
                NAUTILUS
              </span>
              <span className="t-mono" style={{ fontSize: 8, color: 'var(--phos-dim)' }}>
                v3.2.1
              </span>
            </div>
            <div style={{ display: 'flex', gap: 5 }}>
              <span className="tag on">LINK</span>
              <span className="tag">REC</span>
            </div>
          </div>

          {/* 1:1 SONAR bezel */}
          <div style={{ padding: '8px 8px 0' }}>
            <Bezel variant={t.bezel} label={`SONAR-OBS · MK.III · ${state.stage.toUpperCase()}`}>
              <SonarScreen
                width={innerSquare}
                height={innerSquare}
                state={state}
                dispatch={dispatch}
                tweaks={t}
              />
            </Bezel>
          </div>

          {/* Inline terminal */}
          <div style={{ padding: '6px 8px 0' }}>
            <TerminalScreen
              width={terminalW}
              height={terminalH}
              state={state}
              dispatch={dispatch}
              tweaks={t}
            />
          </div>
        </div>
      </IOSDevice>

      {/* Tweaks panel */}
      <TweaksPanel title="Tweaks">
        <TweakSection label="Display" />
        <TweakRadio label="Theme" value={t.theme}
          options={['green', 'amber', 'blue']}
          onChange={(v) => setTweak('theme', v)} />
        <TweakSlider label="CRT intensity" value={t.crt} min={0} max={1.4} step={0.1}
          onChange={(v) => setTweak('crt', v)} />
        <TweakToggle label="Scanlines" value={t.scanlines}
          onChange={(v) => setTweak('scanlines', v)} />
        <TweakToggle label="Noise / static" value={t.noise}
          onChange={(v) => setTweak('noise', v)} />

        <TweakSection label="Bezel" />
        <TweakRadio label="Housing" value={t.bezel}
          options={['military', 'vintage', 'minimal']}
          onChange={(v) => setTweak('bezel', v)} />

        <TweakSection label="Creature" />
        <TweakSelect label="Species" value={t.species}
          options={['ghost', 'blob', 'jelly', 'squid', 'pixel']}
          onChange={(v) => setTweak('species', v)} />

        <TweakSection label="Sonar" />
        <TweakSlider label="Pulse period" value={t.pulse} min={2} max={12} step={0.5} unit="s"
          onChange={(v) => setTweak('pulse', v)} />
        <TweakSlider label="Phosphor decay" value={t.decay} min={0.3} max={4} step={0.1} unit="s"
          onChange={(v) => setTweak('decay', v)} />

        <TweakSection label="Audio" />
        <TweakToggle label="SFX" value={t.sound}
          onChange={(v) => setTweak('sound', v)} />

        <TweakSection label="Care" />
        <TweakButton onClick={() => dispatch({ type: 'reset' })}>Hatch new egg</TweakButton>
      </TweaksPanel>
    </div>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<App />);
