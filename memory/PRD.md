# MMSI V3.8 Code Auditor — PRD

## Problem statement (user, German)
"repo anschauen" → "app starten" → "frontend viel professioneller gestalten und auditor testen"
→ "zu dunkel, überladen, Überlappungen, der Speicher/Datei-Dialog öffnet nicht; mach eine
einfache Import-Seite, einen Ladebalken für den Auslese-Fortschritt, dann klare Audit-
Ausarbeitung und Empfehlung."

## What it is
Standalone, zero-network, in-browser code quality & security auditor. All analysis runs
client-side (deterministic "field-theory" engine + 13 security-pattern scanners) and issues
a SHA-signed Zero-Defect / Seinsmodus certificate. No server, no uploads, no cloud.

## Tech stack (NOT React/FastAPI/Mongo)
- Single self-contained HTML SPA: `src/assets/code_auditor.html` (+ identical `index.html` served as root)
- Vanilla JS + inline CSS, zero external CDNs/fonts (zero-egress)
- C++ WASM engine source in `src/cpp/` — browser uses the JS fallback (fully functional)
- Served in this env by a background `python3 -m http.server 3000` (maps to preview URL).
  NOTE: background process, NOT supervisor-managed (won't survive a container restart).

## "Blackbox after deployment?" — answer
Yes. The audit engine is pure client-side JS (WASM has a JS fallback), so on any static host
(Emergent/Vercel/Netlify/GitHub Pages) it behaves identically — nothing server-side is needed
and the zero-egress guarantee is preserved.

## Work log
### Session 1 (2026-06)
- Fixed a FATAL bug: a global "Seinsmodus"→"Zero-Defect" replace had corrupted JS identifiers
  (`ZERO-DEFECT_THRESHOLD`, `isZero-Defect`), breaking the entire script. Fixed → script parses.
- First professional redesign of the (then dark 3-panel HUD) landing.

### Session 2 (2026-06) — full UX rebuild (current)
- Rebuilt the whole app into a clean, LIGHT, linear 3-step flow: **Import → Progress → Results**.
  Removed the dark 3-panel HUD and the 3D canvas (source of the "overlaps"/darkness).
- Fixed the file picker: replaced `webkitdirectory` directory-picker with a normal multi-file
  `<input>` + "Dateien auswählen" button + drag & drop → dialog now opens.
- Added a file-reading PROGRESS BAR (per-file status + animated %).
- Clear results page: verdict banner, 4 metric cards (kritische Befunde / Dateien / W(t) / Ω(t)),
  detailed per-pattern findings (severity-tagged), aggregated German recommendations, per-file
  table, certificate card, and actions (Neues Audit / Bericht exportieren / Zertifikat laden).
- Made the certificate hash DETERMINISTIC (removed wall-clock timestamp from the hashed payload).
- Reused the original engine verbatim: `getLanguage`, `analyzeFile`, `analyzeFiles`, `generateHash`.

## Verification
- Engine (Node stub harness): detects eval/innerHTML/document.write/secret/proto-pollution/
  path-traversal; clean file = Zero-Defect; deterministic hash across runs. PASS.
- testing_agent iteration_1.json: **frontend 100%** — import loads (light, no overlap), file
  picker works, progress bar animates, vulnerable.js → 8 critical findings + NICHT-BESTANDEN cert,
  clean.js → Zero-Defect cert + empty state, exports fire, "Neues Audit" resets. No console errors.

## Backlog / notes
- P2: `code_auditor.html` and `index.html` are duplicated — keep both in sync on every edit
  (currently done via `cp`). Consider a symlink/build step.
- P2: Static server not supervisor-managed (won't survive restart). Could add a supervisor entry.
- P2: WASM `.wasm`/`audit_worker.js` not bundled; JS fallback active (by design).
