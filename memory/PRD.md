# MMSI V3.8 Code Auditor — PRD

## Problem statement (user)
"repo anschauen" → "app starten" → "frontend viel professioneller gestalten und auditor testen"
(Look at the repo, start the app, make the frontend much more professional, and test the auditor.)

## What this project is
A standalone, zero-network in-browser code quality & compliance auditor for the Emergent
Builder's Contest. Users drag & drop source files; a deterministic "field-theory" engine
scores them (cognitive vector I(t), load W(t), friction, Ω(t), golden ratio φ), flags
20 hard-gate security patterns, runs compliance scanners (Apple/Play, DSGVO/BGB, Copyright/CVE),
and issues a SHA-256 "Zero-Defect" certificate.

## Tech stack (NOT standard React/FastAPI/Mongo)
- Single self-contained HTML SPA: `src/assets/code_auditor.html` (+ `index.html` copy served as root)
- Vanilla JS + inline CSS, zero external CDNs/fonts (zero-egress requirement)
- C++ WASM engine source in `src/cpp/` (JS fallback active in browser)
- 30 JS modules + 7 Node test suites (`npm test`)
- Android/Gradle wrapper at repo root (separate, untouched)

## Serving in this environment
- No `/app/frontend` (React) exists, so supervisor frontend/backend are FATAL by design.
- App is served via a background static server: `python3 -m http.server 3000 --directory /app/src/assets`
  (port 3000 maps to the preview URL). NOTE: background process — does NOT survive a container restart.

## Work done (2026-06 / this session)
1. **Critical bug fix**: A global find-replace of "Seinsmodus"→"Zero-Defect" had corrupted JS
   identifiers (`ZERO-DEFECT_THRESHOLD`, `isZero-Defect`, `statZero-Defect`) — hyphens are illegal
   in JS identifiers, so the ENTIRE inline script failed to parse and every button (login, guest,
   scan) was dead. Fixed to `ZERO_DEFECT_THRESHOLD` / `isZeroDefect` / `statZeroDefect`. JS now parses.
2. **Professional redesign** ("Swiss / enterprise security console"): new solid dark palette,
   sharp flat surfaces, 1px wireframe grid, system + mono typography, left-aligned hero,
   2×3 feature grid, terminal-style login card, restyled HUD/panels/tabs/findings/certificate.
   Replaced ALL emojis with inline stroke SVGs. Added 14 `data-testid`s. Applied to both HTML files.
3. **Testing**:
   - Engine (Node stub harness): detects eval/innerHTML/document.write/secret/proto-pollution/
     path-traversal (10 critical on the vulnerable fixture), computes W(t)/Ω(t), marks clean file
     Zero-Defect with valid cert, deterministic hash within a run. ALL PASS.
   - Original `npm test` (7 suites) — ALL PASS.
   - Landing page renders crisply (screenshot verified). No console JS errors on load.

## Known notes / backlog
- P2: `analyzeFiles` mixes a wall-clock `timestamp` into the certificate hash input, so the hash
  is only deterministic within the same millisecond — README claims full determinism. Consider
  excluding timestamp from the hashed payload for reproducible certificates.
- P2: `audit_worker.js` is referenced by `new Worker(...)` but not present in `src/assets`; the app
  correctly falls back to the main-thread engine.
- P2: Static server is not supervisor-managed (won't survive restart). Could add a supervisor entry.
- The HUD/live-audit screen wasn't screenshot-verified (the screenshot tool only auto-captures the
  initial landing and didn't execute interaction scripts), but the engine + no-error load were verified.
