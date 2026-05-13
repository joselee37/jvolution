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

// Personality dice — roll bucket → AI action.
// Remaining probability mass is "idle" (no action this tick).
const PERSONALITIES = {
  aggressive: { challenge: 0.65, friendly: 0.10 },
  gentle:     { challenge: 0.08, friendly: 0.55 },
  playful:    { challenge: 0.35, friendly: 0.35 },
  veteran:    { challenge: 0.25, friendly: 0.15 },
};

// Fixed mock roster — drifts around the radar; bonds/records build over time.
const NPC_ROSTER = [
  { id: 'lumen',  name: 'LUMEN-3', species: 'jelly', stage: 'juvenile', personality: 'gentle' },
  { id: 'hrrk',   name: 'HRRK',    species: 'squid', stage: 'adult',    personality: 'aggressive' },
  { id: 'blink',  name: 'BLINK',   species: 'pixel', stage: 'larva',    personality: 'playful' },
  { id: 'morrow', name: 'MORROW',  species: 'ghost', stage: 'adult',    personality: 'veteran' },
  { id: 'sift',   name: 'SIFT',    species: 'blob',  stage: 'juvenile', personality: 'playful' },
  { id: 'arc9',   name: 'ARC-9',   species: 'squid', stage: 'adult',    personality: 'aggressive' },
  { id: 'nimbus', name: 'NIMBUS',  species: 'jelly', stage: 'larva',    personality: 'gentle' },
];

function makePeers() {  const n = NPC_ROSTER.length;
  return NPC_ROSTER.map((roster, i) => ({
    ...roster,
    bearing: ((i * 360) / n + (Math.random() - 0.5) * 30 + 360) % 360,
    range: 0.32 + Math.random() * 0.55,
    bearingVel: (Math.random() - 0.5) * 2.2,    // deg/sec drift
    rangeVel: (Math.random() - 0.5) * 0.004,    // bounces between 0.20–0.92
    bond: 0,
    battlesWon: 0,
    battlesLost: 0,
    cooldown: 20 + Math.random() * 40,          // initial silence so they don't fire instantly
  }));
}

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
    view: 'sonar',      // 'sonar' | 'tree' | 'radar' | 'battle'
    lineage: [],        // archived previous generations
    hatchedAt: Date.now(),
    // — Peer / multiplayer (mocked) —
    peers: makePeers(),
    pendingRequest: null,        // { from: peerId, type: 'challenge' | 'breed' }
    peerEventNonce: 0,           // bumped on every event the terminal should announce
    peerEventLatest: null,       // { kind, peerId, lines: string[] }
    dnd: false,                  // do-not-disturb — suppress incoming challenges.
                                 // Effective DND is also implied by an active battle.
    // — Battle —
    battle: null,                // see makeBattle() below
  };
}

// Battle state factory. HP=5, cursor on PING by default.
function makeBattle(peerId) {
  return {
    peerId,
    hpMe: 5,
    hpMaxMe: 5,
    hpThem: 5,
    hpMaxThem: 5,
    cursor: 0,                 // 0..3 in BATTLE_ACTIONS
    myMove: null,
    theirMove: null,
    phase: 'choose',           // 'choose' | 'reveal' | 'damage' | 'end'
    log: [],                   // newest first; each { tag, line, crit, dmgMe, dmgThem }
    result: null,              // 'win' | 'lose' | 'flee' | 'draw'
    turn: 1,
    myMoveHistory: [],         // for veteran AI read & react
    lastDmgMe: 0,
    lastDmgThem: 0,
    flashNonceMe: 0,
    flashNonceThem: 0,
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
    case 'setView':
      return { ...s, view: a.view };
    case 'peerTick': {
      const dt = a.dt;
      let pendingRequest = s.pendingRequest;
      let peerEventNonce = s.peerEventNonce;
      let peerEventLatest = s.peerEventLatest;
      let toast = s.toast;

      const peers = s.peers.map((p) => {
        // Drift position
        let bearing = ((p.bearing + p.bearingVel * dt) % 360 + 360) % 360;
        let range = p.range + p.rangeVel * dt;
        let rangeVel = p.rangeVel;
        if (range <= 0.20) { range = 0.20; rangeVel = Math.abs(rangeVel); }
        if (range >= 0.92) { range = 0.92; rangeVel = -Math.abs(rangeVel); }
        let cooldown = Math.max(0, p.cooldown - dt);
        let bond = p.bond;

        // Roll AI only if cooldown elapsed, no pending request, and dice say so.
        // The 0.06/tick rate (with 1s ticks) means ~3.6%/sec across the whole
        // roster — comfortable cadence without being spammy.
        // DND (explicit or implied by active battle) suppresses challenges; the
        // roll outcome is treated as a no-op with the short skip cooldown so we
        // don't redirect would-be challenges into friendly drifts.
        const dndActive = s.dnd || !!s.battle;
        if (cooldown === 0 && !pendingRequest && Math.random() < 0.06) {
          const profile = PERSONALITIES[p.personality] || PERSONALITIES.playful;
          const roll = Math.random();
          const wantsChallenge = roll < profile.challenge;
          const wantsFriendly = !wantsChallenge && roll < profile.challenge + profile.friendly;
          if (wantsChallenge && dndActive) {
            // Challenge muted by DND — short cooldown so this peer re-rolls soon
            // once DND clears.
            cooldown = 30 + Math.random() * 40;
          } else if (wantsChallenge) {
            pendingRequest = { from: p.id, type: 'challenge' };
            toast = `${p.name} CHALLENGES`;
            peerEventNonce += 1;
            peerEventLatest = {
              kind: 'challenge',
              peerId: p.id,
              lines: [
                '',
                '▸▸▸ INCOMING ▸▸▸',
                `  ${p.name} (${p.species} · ${p.stage}) pings the channel.`,
                '  type `accept` to engage, `decline` to dismiss.',
                '',
              ],
            };
            cooldown = 90 + Math.random() * 60;
          } else if (wantsFriendly) {
            bond = Math.min(1, bond + 0.05);
            toast = `${p.name} APPROACHES`;
            peerEventNonce += 1;
            peerEventLatest = {
              kind: 'friendly',
              peerId: p.id,
              lines: [
                `▸ ${p.name} drifts close. peer-bond +5% → ${Math.round(bond * 100)}%.`,
              ],
            };
            cooldown = 60 + Math.random() * 40;
          } else {
            cooldown = 30 + Math.random() * 40;
          }
        }

        return { ...p, bearing, range, rangeVel, cooldown, bond };
      });

      return {
        ...s, peers, pendingRequest, toast,
        peerEventNonce, peerEventLatest,
      };
    }
    case 'peerSetBond':
      return {
        ...s,
        peers: s.peers.map((p) =>
          p.id === a.id ? { ...p, bond: Math.max(0, Math.min(1, p.bond + a.delta)) } : p,
        ),
      };
    case 'acceptRequest': {
      // Phase 1 stub: bond + log; Phase 2 will launch the real battle.
      const req = s.pendingRequest;
      if (!req) return s;
      const peer = s.peers.find((p) => p.id === req.from);
      const peerEventNonce = s.peerEventNonce + 1;
      const peerEventLatest = {
        kind: 'accept',
        peerId: req.from,
        lines: [
          `▸ accepted ${peer?.name || req.from}'s ${req.type}.`,
          '  [BATTLE MODULE PENDING — engagement queued for v2]',
        ],
      };
      return {
        ...s,
        pendingRequest: null,
        peerEventNonce,
        peerEventLatest,
        peers: s.peers.map((p) =>
          p.id === req.from
            ? { ...p, bond: Math.min(1, p.bond + 0.04) }
            : p,
        ),
        toast: 'ENGAGEMENT QUEUED',
      };
    }
    case 'declineRequest': {
      const req = s.pendingRequest;
      if (!req) return s;
      const peer = s.peers.find((p) => p.id === req.from);
      const peerEventNonce = s.peerEventNonce + 1;
      const peerEventLatest = {
        kind: 'decline',
        peerId: req.from,
        lines: [`▸ declined ${peer?.name || req.from}.`],
      };
      return {
        ...s,
        pendingRequest: null,
        peerEventNonce,
        peerEventLatest,
        toast: null,
      };
    }
    // ── Battle ────────────────────────────────────────────────
    case 'battleStart': {
      // Clears the pending challenge that triggered us so the alert overlay
      // dismisses, and swaps the view to 'battle'.
      const peerId = a.peerId;
      const peer = s.peers.find((p) => p.id === peerId);
      if (!peer) return s;
      return {
        ...s,
        view: 'battle',
        battle: makeBattle(peerId),
        pendingRequest: null,
        toast: `ENGAGE — ${peer.name}`,
      };
    }
    case 'battleCursor': {
      if (!s.battle || s.battle.phase !== 'choose') return s;
      const n = 4;
      let cursor = s.battle.cursor;
      if (a.set != null) cursor = a.set;
      else if (a.delta) cursor = (cursor + a.delta + n) % n;
      return { ...s, battle: { ...s.battle, cursor } };
    }
    case 'battleCommit': {
      if (!s.battle || s.battle.phase !== 'choose') return s;
      const peer = s.peers.find((p) => p.id === s.battle.peerId);
      const my = BATTLE_ACTIONS[s.battle.cursor];
      const their = pickNpcMove(peer, s.battle);
      return {
        ...s,
        battle: {
          ...s.battle,
          myMove: my,
          theirMove: their,
          phase: 'reveal',
          myMoveHistory: [...s.battle.myMoveHistory, my],
        },
      };
    }
    case 'battleResolve': {
      if (!s.battle || s.battle.phase !== 'reveal') return s;
      const peer = s.peers.find((p) => p.id === s.battle.peerId);
      const out = resolveBattleTurn(s.battle.myMove, s.battle.theirMove, s, peer);
      const narration = battleNarration(s.battle.myMove, s.battle.theirMove, out, s.name, peer.name);
      return {
        ...s,
        battle: {
          ...s.battle,
          phase: 'damage',
          lastDmgMe: out.me,
          lastDmgThem: out.them,
          log: [
            { tag: out.tag, crit: out.crit, dmgMe: out.me, dmgThem: out.them, line: narration },
            ...s.battle.log,
          ].slice(0, 6),
          flashNonceMe: s.battle.flashNonceMe + (out.me > 0 ? 1 : 0),
          flashNonceThem: s.battle.flashNonceThem + (out.them > 0 ? 1 : 0),
        },
      };
    }
    case 'battleApplyDamage': {
      if (!s.battle || s.battle.phase !== 'damage') return s;
      const hpMe = Math.max(0, s.battle.hpMe - s.battle.lastDmgMe);
      const hpThem = Math.max(0, s.battle.hpThem - s.battle.lastDmgThem);
      const ko = hpMe <= 0 || hpThem <= 0;
      const result = ko
        ? (hpMe <= 0 && hpThem <= 0 ? 'draw' : hpMe <= 0 ? 'lose' : 'win')
        : null;
      return {
        ...s,
        battle: {
          ...s.battle,
          hpMe, hpThem,
          phase: ko ? 'end' : 'choose',
          result,
          myMove: ko ? s.battle.myMove : null,
          theirMove: ko ? s.battle.theirMove : null,
          turn: ko ? s.battle.turn : s.battle.turn + 1,
        },
      };
    }
    case 'battleFlee': {
      if (!s.battle || s.battle.phase === 'end') return s;
      return {
        ...s,
        battle: { ...s.battle, phase: 'end', result: 'flee' },
      };
    }
    case 'battleEnd': {
      // Apply rewards/penalties, bump records, exit back to sonar.
      if (!s.battle) return s;
      const peer = s.peers.find((p) => p.id === s.battle.peerId);
      const result = s.battle.result;
      let happiness = s.happiness;
      let discipline = s.discipline;
      let evolveProgress = s.evolveProgress;
      let bondDelta = 0;
      let peers = s.peers;
      if (result === 'win') {
        evolveProgress = clamp(evolveProgress + 0.20);
        happiness = clamp(happiness + 0.05);
        bondDelta = 0.10;
        peers = s.peers.map((p) =>
          p.id === peer.id
            ? { ...p, battlesWon: (p.battlesWon || 0), battlesLost: (p.battlesLost || 0) + 1,
                bond: Math.min(1, p.bond + bondDelta) }
            : p,
        );
      } else if (result === 'lose') {
        happiness = clamp(happiness - 0.15);
        discipline = clamp(discipline + 0.05);
        bondDelta = 0.04;
        peers = s.peers.map((p) =>
          p.id === peer.id
            ? { ...p, battlesWon: (p.battlesWon || 0) + 1, battlesLost: (p.battlesLost || 0),
                bond: Math.min(1, p.bond + bondDelta) }
            : p,
        );
      } else if (result === 'draw') {
        happiness = clamp(happiness + 0.03);
        bondDelta = 0.06;
        peers = s.peers.map((p) =>
          p.id === peer.id ? { ...p, bond: Math.min(1, p.bond + bondDelta) } : p,
        );
      } else if (result === 'flee') {
        happiness = clamp(happiness - 0.05);
        bondDelta = -0.05;
        peers = s.peers.map((p) =>
          p.id === peer.id ? { ...p, bond: Math.max(0, p.bond + bondDelta) } : p,
        );
      }
      const toast =
        result === 'win'  ? 'VICTORY' :
        result === 'lose' ? 'DEFEATED' :
        result === 'draw' ? 'STALEMATE' :
        result === 'flee' ? 'DISENGAGED' : '';
      return {
        ...s,
        view: 'sonar',
        battle: null,
        happiness, discipline, evolveProgress, peers,
        toast,
      };
    }
    case 'reset': {
      const epitaph = {
        gen: s.gen,
        name: s.name,
        stage: s.stage,
        cycles: s.cycles,
        happiness: Math.round(s.happiness * 100),
        energy: Math.round(s.energy * 100),
        bond: Math.round(s.bond * 100),
        discipline: Math.round(s.discipline * 100),
        training: Math.round(s.training * 100),
        hatchedAt: s.hatchedAt,
        archivedAt: Date.now(),
      };
      const fresh = initialState();
      return {
        ...fresh,
        gen: s.gen + 1,
        lineage: [...s.lineage, epitaph],
        view: s.view,
        // Peers, bonds, and pending state are independent of your creature.
        peers: s.peers,
        pendingRequest: s.pendingRequest,
        peerEventNonce: s.peerEventNonce,
        peerEventLatest: s.peerEventLatest,
      };
    }
    case 'toggleSound':
      return { ...s, sound: !s.sound, toast: s.sound ? 'MUTED' : 'SOUND ON' };
    case 'setDnd':
      return { ...s, dnd: !!a.on, toast: a.on ? 'DND ON' : 'DND OFF' };
    case 'toast':
      return { ...s, toast: a.msg };
    default:
      return s;
  }
}

let __setToast = () => {};

// Short narration line for the battle log. Reads as "ME pings — BLOCKED" etc.
function battleNarration(myMove, theirMove, out, myName, theirName) {
  const verbs = {
    ping: 'pings', charge: 'charges', dodge: 'evades', screech: 'screeches',
  };
  const me = verbs[myMove] || myMove;
  const them = verbs[theirMove] || theirMove;
  if (out.tag === 'MISS') return `${myName} ${me}, ${theirName} ${them} — both whiff`;
  if (out.tag === 'INTERFERENCE') return `mutual ${myMove} — waves overlap`;
  if (out.tag === 'CLASH') return `both charge — shockwave between`;
  if (out.tag === 'FEEDBACK') return `dual screech — feedback loop`;
  if (out.tag === 'BLOCKED') {
    return out.me === 0 && out.them === 0
      ? (myMove === 'dodge' ? `${myName} evades the pulse` : `${theirName} evades the pulse`)
      : `pulse absorbed`;
  }
  if (out.tag === 'COUNTERED') {
    return myMove === 'screech'
      ? `${myName} reflects — ${theirName} takes ${out.them.toFixed(1)}`
      : `${theirName} reflects — ${myName} takes ${out.me.toFixed(1)}`;
  }
  if (out.tag === 'INTERRUPT') {
    return myMove === 'ping'
      ? `${myName}'s pulse interrupts the charge — ${out.them.toFixed(1)}`
      : `${theirName}'s pulse interrupts the charge — ${out.me.toFixed(1)}`;
  }
  if (out.tag === 'BROKEN') {
    if (myMove === 'charge') {
      return `${myName} smashes through — ${out.them.toFixed(1)} dmg`;
    }
    return `${theirName} smashes through — ${out.me.toFixed(1)} dmg`;
  }
  return '';
}

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

  // Keep the last peer-alert payload around after dismiss so the popup
  // can fade out smoothly while still showing the right info.
  const [lastAlert, setLastAlert] = React.useState(null);
  React.useEffect(() => {
    if (state.pendingRequest) {
      const peer = state.peers.find((p) => p.id === state.pendingRequest.from);
      if (peer) setLastAlert({ peer, type: state.pendingRequest.type });
    }
  }, [state.pendingRequest, state.peers]);

  React.useEffect(() => {
    // When a peer is hailing, override the entire phosphor hue to RED so
    // every component using oklch(L C var(--hue)) (text, LEDs, gauges,
    // sweep, blips via the canvas hue prop) shifts in lockstep.
    const alertActive = !!state.pendingRequest;
    const hue = alertActive ? 25 : HUE_BY_THEME[t.theme];
    document.documentElement.style.setProperty('--hue', hue);
    document.documentElement.style.setProperty('--scan-strength', t.scanlines ? 0.18 * t.crt : 0);
    document.documentElement.style.setProperty('--noise-strength', t.noise ? 0.12 * t.crt : 0);
    document.documentElement.style.setProperty('--glow-strength', t.crt);
    document.body.classList.toggle('alert-mode', alertActive);
  }, [t.theme, t.scanlines, t.noise, t.crt, state.pendingRequest]);

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

  // Peer tick — drift positions + roll NPC AI. 1s cadence keeps the
  // radar motion smooth while staying cheap.
  React.useEffect(() => {
    const id = setInterval(() => dispatch({ type: 'peerTick', dt: 1 }), 1000);
    return () => clearInterval(id);
  }, []);

  // Battle phase scheduler — drives reveal → damage → next-turn / end timing.
  // Each phase has a fixed duration so the visuals have time to land.
  React.useEffect(() => {
    if (!state.battle) return undefined;
    const phase = state.battle.phase;
    const result = state.battle.result;
    if (phase === 'reveal') {
      const id = setTimeout(() => dispatch({ type: 'battleResolve' }), 700);
      return () => clearTimeout(id);
    }
    if (phase === 'damage') {
      const id = setTimeout(() => dispatch({ type: 'battleApplyDamage' }), 500);
      return () => clearTimeout(id);
    }
    if (phase === 'end' && result) {
      const id = setTimeout(() => dispatch({ type: 'battleEnd' }), 1800);
      return () => clearTimeout(id);
    }
    return undefined;
  }, [state.battle?.phase, state.battle?.turn, state.battle?.result]);

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

          {/* 1:1 main bezel — SONAR / LINEAGE / RADAR / BATTLE depending on state.view */}
          <div style={{ padding: '8px 8px 0' }}>
            <Bezel
              variant={t.bezel}
              label={
                state.view === 'tree'
                  ? `LINEAGE-ARCHIVE · MK.III · G${String(state.gen).padStart(2, '0')}`
                  : state.view === 'radar'
                  ? `LRRS-RADAR · MK.III · ${state.peers.length} CONTACTS`
                  : state.view === 'battle'
                  ? `ENGAGEMENT · CH.07 · R.${String(state.battle?.turn || 1).padStart(2, '0')}`
                  : `SONAR-OBS · MK.III · ${state.stage.toUpperCase()}`
              }
            >
              <div style={{ position: 'relative', width: innerSquare, height: innerSquare }}>
                {state.view === 'tree' ? (
                  <TreeScreen
                    width={innerSquare}
                    height={innerSquare}
                    state={state}
                    dispatch={dispatch}
                    tweaks={t}
                  />
                ) : state.view === 'radar' ? (
                  <RadarScreen
                    width={innerSquare}
                    height={innerSquare}
                    state={state}
                    dispatch={dispatch}
                    tweaks={t}
                  />
                ) : state.view === 'battle' ? (
                  <BattleScreen
                    width={innerSquare}
                    height={innerSquare}
                    state={state}
                    dispatch={dispatch}
                    tweaks={t}
                  />
                ) : (
                  <SonarScreen
                    width={innerSquare}
                    height={innerSquare}
                    state={state}
                    dispatch={dispatch}
                    tweaks={t}
                  />
                )}
                {lastAlert && (
                  <PeerAlertOverlay
                    active={!!state.pendingRequest}
                    peer={lastAlert.peer}
                    type={lastAlert.type}
                  />
                )}
              </div>
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
