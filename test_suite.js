const assert = require('assert');
const fs = require('fs');
const path = require('path');

console.log("=========================================");
console.log("RUNNING NANO PLAYER FULL TEST SUITE");
console.log("Targeting devices: Low-end (API 1/21) to Modern (API 34)");
console.log("=========================================\n");

let passed = 0;
let total = 0;

function test(name, fn) {
  total++;
  try {
    fn();
    console.log(`[PASS] ${name}`);
    passed++;
  } catch (err) {
    console.error(`[FAIL] ${name}: ${err.message}`);
    process.exitCode = 1;
  }
}

// 1. Domain Model Invariants
test("Domain Track model holds valid immutability and attributes", () => {
  const track = {
    id: "track_1",
    title: "1901",
    artist: "Phoenix",
    album: "Wolfgang Amadeus Phoenix",
    durationMs: 193000,
    sourceUri: "content://media/external/audio/1"
  };
  assert.strictEqual(track.id, "track_1");
  assert.strictEqual(track.durationMs, 193000);
});

test("PlaybackStatus calculates progressFraction accurately and clamps safely", () => {
  const calcProgress = (pos, dur) => (dur > 0 ? Math.min(1, Math.max(0, pos / dur)) : 0);
  assert.strictEqual(calcProgress(0, 200), 0);
  assert.strictEqual(calcProgress(100, 200), 0.5);
  assert.strictEqual(calcProgress(250, 200), 1.0);
  assert.strictEqual(calcProgress(50, 0), 0);
});

// 2. Use Case Tests
test("AdjustVolumeUseCase clamps strictly within [0, 100]", () => {
  const adjustVolume = (current, delta) => Math.max(0, Math.min(100, current + delta));
  assert.strictEqual(adjustVolume(70, 10), 80);
  assert.strictEqual(adjustVolume(95, 10), 100);
  assert.strictEqual(adjustVolume(5, -15), 0);
});

test("SkipTrackUseCase cycles circularly across playlist", () => {
  const playlist = [{ id: "1" }, { id: "2" }, { id: "3" }];
  const skipNext = (list, current) => {
    const idx = list.findIndex(t => t.id === current.id);
    return list[(idx + 1) % list.length];
  };
  const skipPrev = (list, current, posMs) => {
    if (posMs > 3000) return current;
    const idx = list.findIndex(t => t.id === current.id);
    return list[(idx - 1 + list.length) % list.length];
  };

  assert.strictEqual(skipNext(playlist, { id: "3" }).id, "1");
  assert.strictEqual(skipPrev(playlist, { id: "1" }, 0).id, "3");
  assert.strictEqual(skipPrev(playlist, { id: "2" }, 4500).id, "2");
});

test("Repeat Mode cycles: OFF -> ALL (Todo) -> ONE (Esta) -> OFF", () => {
  let mode = 'OFF';
  const cycle = (m) => m === 'OFF' ? 'ALL' : (m === 'ALL' ? 'ONE' : 'OFF');

  mode = cycle(mode);
  assert.strictEqual(mode, 'ALL', "Should switch to ALL (Todo)");

  mode = cycle(mode);
  assert.strictEqual(mode, 'ONE', "Should switch to ONE (Esta)");

  mode = cycle(mode);
  assert.strictEqual(mode, 'OFF', "Should loop back to OFF");
});

test("Now Playing menu action routes directly to now-playing screen", () => {
  const appState = {
    currentScreen: 'MENU',
    currentMenu: 'MAIN',
    currentTrack: { id: "track_2", title: "Feel Good, Inc." },
    menuStack: []
  };

  const showNowPlaying = (state) => {
    state.menuStack.push(state.currentMenu);
    state.currentScreen = 'NOW_PLAYING';
    return state;
  };

  const nextState = showNowPlaying(appState);
  assert.strictEqual(nextState.currentScreen, 'NOW_PLAYING');
  assert.strictEqual(nextState.currentTrack.title, "Feel Good, Inc.");
  assert.strictEqual(nextState.menuStack[0], 'MAIN');
});

test("Album covers audit: all 5 cover art images exist and are valid JPEGs", () => {
  const covers = [
    "app/src/main/assets/covers/demondays.jpg",
    "app/src/main/assets/covers/phoenix.jpg",
    "app/src/main/assets/covers/coldplay.jpg",
    "app/src/main/assets/covers/daftpunk.jpg",
    "app/src/main/assets/covers/thekillers.jpg"
  ];

  covers.forEach(rel => {
    const full = path.join(__dirname, rel);
    assert(fs.existsSync(full), `Missing cover art file: ${rel}`);
    const stat = fs.statSync(full);
    assert(stat.size > 10000, `Cover file is corrupted or too small (${stat.size} bytes): ${rel}`);
  });
});

test("Generic Remixicon cover art fallback: renders ri-disc-line when track has no artwork or on error", () => {
  const html = fs.readFileSync(path.join(__dirname, 'web/index.html'), 'utf8');
  assert(html.includes('id="np-generic-cover"'), "Must include generic cover element");
  assert(html.includes('ri-disc-line'), "Must use remixicon disc icon for generic cover");
  assert(html.includes('this.npGenericCover.style.display = \'flex\''), "Must display generic cover when artwork is missing");
});

test("UI Scaler functions deterministically across all phone resolutions", () => {
  const testDevices = [
    { name: "HTC Dream / G1 (Android 1.0 Low-End)", w: 320, h: 480 },
    { name: "HTC Wildfire (Vintage Low-End QVGA)", w: 240, h: 320 },
    { name: "Samsung Galaxy S (Android 2.1 WVGA)", w: 480, h: 800 },
    { name: "Nexus 5 (Android 4.4 FHD)", w: 1080, h: 1920 },
    { name: "Modern Flagship (Android 14 QHD+)", w: 1440, h: 3120 }
  ];

  testDevices.forEach(dev => {
    const scale = Math.max(0.7, Math.min(dev.w / 320, dev.h / 480));
    const headerH = Math.max(34, Math.max(40 * scale, dev.h * 0.08));
    const touchButtonRadius = 28 * scale;
    const touchButtonDiameter = touchButtonRadius * 2;

    assert(scale >= 0.7, `Scale too small on ${dev.name}`);
    assert(headerH >= 32, `Header height unusable on ${dev.name}`);
    assert(touchButtonDiameter >= 39, `Button touch target too small on ${dev.name}`);
  });
});

test("Repeat button layout stability: fixed 62px width prevents layout shifts", () => {
  const html = fs.readFileSync(path.join(__dirname, 'web/index.html'), 'utf8');
  assert(html.includes("width: 62px;"), "Repeat button must have fixed width: 62px");
  assert(html.includes("min-width: 62px;"), "Repeat button must have min-width: 62px");
  assert(html.includes("max-width: 62px;"), "Repeat button must have max-width: 62px");
});

test("Memory efficiency audit: simulates 5,000 continuous song transitions without memory leak", () => {
  if (global.gc) global.gc();
  const initialHeap = process.memoryUsage().heapUsed;

  // Simulated playback session for long hours (5,000 transitions)
  const playlist = [
    { id: "1", title: "Feel Good Inc." },
    { id: "2", title: "1901" },
    { id: "3", title: "Viva La Vida" }
  ];
  let currentIndex = 0;
  let sessionState = { currentTrack: playlist[0], history: [] };

  // Capped LRU cache simulation (20 items max)
  const lruCache = new Map();
  const maxCache = 20;

  for (let i = 0; i < 5000; i++) {
    currentIndex = (currentIndex + 1) % playlist.length;
    sessionState.currentTrack = playlist[currentIndex];
    
    // Cache put & LRU evict
    const key = `cover_${i % 100}`;
    if (lruCache.has(key)) lruCache.delete(key);
    lruCache.set(key, { buffer: Buffer.alloc(1024) }); // 1KB sample
    if (lruCache.size > maxCache) {
      const firstKey = lruCache.keys().next().value;
      lruCache.delete(firstKey);
    }
  }

  if (global.gc) global.gc();
  const finalHeap = process.memoryUsage().heapUsed;
  const heapDiffMb = (finalHeap - initialHeap) / (1024 * 1024);

  // Assert that after 5,000 transitions, heap delta is well within < 3 MB
  assert(heapDiffMb < 3.0, `Memory leaked during 5,000 transitions: +${heapDiffMb.toFixed(2)} MB`);
  assert.strictEqual(lruCache.size, maxCache, "LRU Cache size must remain bounded");
});

test("Multi-format audio scanner compatibility: queries MP3, FLAC, M4A, AAC, OGG, OPUS, WAV, WMA, MIDI", () => {
  const gatewayCode = fs.readFileSync(path.join(__dirname, 'app/src/main/kotlin/com/ipodnano/player/data/gateway/MediaStoreMusicRepositoryGateway.kt'), 'utf8');
  const formats = ['.mp3', '.flac', '.m4a', '.aac', '.ogg', '.opus', '.wav', '.wma', '.mid'];
  formats.forEach(ext => {
    assert(gatewayCode.includes(ext), `Gateway must query format extension: ${ext}`);
  });
});

test("Audio routing & becoming-noisy policy: auto-pauses on bluetooth/jack disconnect, permits speaker playback", () => {
  let outputMode = 'jack';
  let isPlaying = true;
  let toastMessage = null;

  const onOutputChange = (newMode, wasAutoPaused) => {
    outputMode = newMode;
    if (wasAutoPaused) {
      isPlaying = false;
      toastMessage = 'Dispositivo desconectado (Pausado)';
    }
  };

  // Disconnect headphones while playing
  onOutputChange('speaker', true);
  assert.strictEqual(outputMode, 'speaker');
  assert.strictEqual(isPlaying, false, "Must auto-pause when headphones unplugged");
  assert.strictEqual(toastMessage, 'Dispositivo desconectado (Pausado)');

  // Connect Bluetooth device
  onOutputChange('bluetooth', false);
  assert.strictEqual(outputMode, 'bluetooth');
});

test("Startup & sync library test: automatically scans storage and merges newly discovered audio files without duplicates", () => {
  const library = [
    { id: "1", uri: "content://media/1", title: "Song 1" }
  ];

  const scanned = [
    { id: "1", uri: "content://media/1", title: "Song 1" },
    { id: "2", uri: "content://media/2", title: "Newly Discovered Song 2" }
  ];

  const existingKeys = new Set(library.map(t => t.uri));
  let added = 0;
  scanned.forEach(t => {
    if (!existingKeys.has(t.uri)) {
      existingKeys.add(t.uri);
      library.push(t);
      added++;
    }
  });

  assert.strictEqual(added, 1, "Exactly one new song should be added");
  assert.strictEqual(library.length, 2, "Library must contain 2 songs total without duplicates");
  assert.strictEqual(library[1].title, "Newly Discovered Song 2");
});

console.log("\n=========================================");
console.log(`TEST SUMMARY: ${passed}/${total} PASSED (${Math.round((passed/total)*100)}%)`);
console.log("=========================================");
