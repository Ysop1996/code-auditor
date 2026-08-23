# MMSI V3.8 Code Auditor

**Deterministic field theory-based code quality and compliance analysis** for Emergent Builder's Contest submissions.

## Overview

The MMSI V3.8 Code Auditor is a zero-egress, fully deterministic code analysis system that evaluates contest submissions through the lens of MMSI V3.8 field theory. It operates as a standalone web application with no external dependencies, no network calls, and no server-side processing.

### Key Architecture

- **C++ Blackbox WASM Engine**: Core evaluation engine compiled to WebAssembly, source not exposed to contestants
- **Field Theory Computation**: I(t) = [i_past, i_present, i_future]^T ∈ R³≥0 with deterministic load Y, friction W, and Ω(t)
- **17 Hard-Gate Security Patterns**: eval, innerHTML, document.write, SQL concat, hardcoded secrets, and 12 more
- **Compliance Scanners**: Apple App Store, Google Play, DSGVO/BGB, Copyright/CVE
- **Monetization Analysis**: Platform fees, hybrid payment, paywall funnel optimization
- **Null-Tolerance Certification**: Deterministic SHA-256 certificate with 30-day validity

## Quick Start

### Running Tests

```bash
cd /Users/monterey/AndroidStudioProjects/code-auditor
npm test
```

This runs all 7 test suites:
- `test_field_theory.js` — Field theory computation (I(t), W(t), Ω(t), φ, Seinsmodus)
- `test_security_scanner.js` — 17 hard-gate security pattern detection
- `test_report_generator.js` — Report structure, findings, recommendations
- `test_hash_determinism.js` — SHA-256 determinism across all modules
- `test_certificate.js` — Certificate generation, validation, formatting
- `test_compliance.js` — Apple/Play, Legal, Copyright compliance scanners
- `test_monetization.js` — Revenue model, platform fees, optimization analysis

### Individual Test Suites

```bash
npm run test:field-theory
npm run test:security
npm run test:report
npm run test:hash
npm run test:certificate
npm run test:compliance
npm run test:monetization
```

### Building the WASM Engine

```bash
npm run build:wasm
```

### Serving the Web App

```bash
npm run serve
```

Then open `http://localhost:8080` in any browser.

## Field Theory

### Cognitive Vector I(t)

I(t) = [i_past, i_present, i_future]^T ∈ R³≥0

- **past**: Complexity debt (cyclomatic complexity × 0.3)
- **present**: Active processing (nesting depth × 0.5 + function count × 0.2)
- **future**: Risk projection (security findings × 0.5)

### Load Function W(t)

W(t) = 0.2·i_past + 0.5·i_present + 0.3·i_future

### Friction W

W = |W(t) - 0.5| · φ

Where φ = 1.61803398875 (golden ratio)

### Omega T Ω(t)

Ω(t) = W × 14.0

### Seinsmodus Certification

Achieved when:
- W(t) ≤ 1.0 (SEINSMODUS_THRESHOLD)
- Total critical findings = 0

### Critical Threshold

Ω(t) ≥ 5800.0 (OMEGA_CRITICAL)

### Warning Threshold

W(t) > 0.8 (WARNING_THRESHOLD)

## Security Patterns (17 Hard-Gate)

| Pattern | Severity | Penalty |
|---------|----------|---------|
| eval() | critical | 50.0 |
| innerHTML | high | 40.0 |
| document.write() | high | 35.0 |
| SQL concatenation | critical | 45.0 |
| Hardcoded secrets | critical | 60.0 |
| Missing error boundary | medium | 25.0 |
| Uncontrolled inputs | medium | 20.0 |
| Nested loops | low | 15.0 |
| Blocking I/O | medium | 30.0 |
| Unsafe deserialization | high | 50.0 |
| XSS risk | high | 35.0 |
| Insecure random | low | 15.0 |
| Prototype pollution | high | 40.0 |
| No rate limiting | low | 10.0 |
| Missing auth | medium | 30.0 |
| Log injection | medium | 20.0 |
| Path traversal | high | 45.0 |

## Compliance Scanners

### Apple App Store Compliance
- Guideline 4.8: Sign in with Apple required
- Guideline 3.1.1: External payment providers blocked for digital goods
- Guideline 4.2: Webview-only apps without native functionality
- App icon and launch screen configuration

### Google Play Compliance
- Target SDK Level 34+ required
- Background permission declarations
- Closed testing track (20-tester requirement)

### Legal Compliance (DSGVO/BGB)
- Tracking pixel opt-in enforcement
- Imprint requirements (§ 5 DDG)
- Privacy policy sections (Art. 13 DSGVO)
- AGB fairness (§§ 307–309 BGB)

### Copyright Scanner
- Copyleft license detection (GPL, AGPL, LGPL, MPL, CDDL)
- CVE vulnerable package database (lodash, axios, moment, etc.)
- Unlicensed font/image asset detection
- Missing license header detection

## Monetization Analysis

### Revenue Models
- Subscription
- One-time purchase
- Pay-per-use
- In-app purchase
- Freemium
- Advertising
- Marketplace commission

### Platform Fees
- Apple/Google IAP: 30% (15% after first year)
- Web Stripe/PayPal: 2.9% + $0.30
- Physical goods/B2B: 0%

### Optimization Opportunities
- Hybrid payment model (native IAP for digital, web billing for physical)
- Paywall funnel friction reduction
- Cognitive barrier elimination

## Project Structure

```
code-auditor/
├── src/
│   ├── cpp/                    # C++ WASM engine (blackbox)
│   │   ├── audit_engine.h      # Engine header
│   │   ├── audit_engine.cpp    # Engine implementation
│   │   └── CMakeLists.txt      # WASM build config
│   ├── js/                     # JavaScript modules
│   │   ├── parsers/            # Language parsers
│   │   │   ├── js_parser.js    # JS/TS parser with 17 security patterns
│   │   │   ├── html_parser.js  # HTML DOM analysis
│   │   │   ├── css_parser.js   # CSS selector analysis
│   │   └── config_parser.js    # JSON/YAML/TOML config analysis
│   │   ├── analyzers/          # Analysis engines
│   │   │   ├── security_scanner.js    # 17 hard-gate patterns
│   │   │   ├── dependency_graph.js    # Tarjan's SCC cycle detection
│   │   │   └── monetization_analyzer.js
│   │   ├── optimizers/         # Optimization modules
│   │   │   ├── latency_optimizer.js   # N+1, blocking I/O, memory leaks
│   │   │   └── patch_generator.js     # Code patches with before/after diffs
│   │   ├── scanners/           # Compliance scanners
│   │   │   ├── apple_play_compliance.js
│   │   │   ├── legal_compliance.js
│   │   │   └── copyright_scanner.js
│   │   ├── report/             # Report generation
│   │   │   ├── finding.js
│   │   │   ├── recommendation.js
│   │   │   ├── report_generator.js
│   │   │   └── certificate_generator.js
│   │   └── test/               # Test suite (7 test files)
│   │       ├── test_field_theory.js
│   │       ├── test_security_scanner.js
│   │       ├── test_report_generator.js
│   │       ├── test_hash_determinism.js
│   │       ├── test_certificate.js
│   │       ├── test_compliance.js
│   │       └── test_monetization.js
│   └── assets/
│       └── code_auditor.html   # Single-file SPA with 3D phase visualization
├── package.json
├── README.md
└── dist/                       # Build output
```

## Emergent Contest Submission

This project is designed for submission to the **Emergent Builder's Contest** ($100K prize pool, Aug 17-31 2026).

### Submission Requirements Met

1. **Standalone web app**: Single HTML file with all logic local
2. **Zero network egress**: All parsing and evaluation in-browser
3. **Deterministic output**: SHA-256 certificate hash for verification
4. **Field theory foundation**: MMSI V3.8 compliant computation
5. **Professional audit reports**: Structured findings with optimization recommendations
6. **Compliance coverage**: Apple/Google Play, DSGVO/BGB, Copyright/CVE

### Running the Auditor

1. Open `src/assets/code_auditor.html` in any modern browser
2. Drag and drop contest submission files onto the upload area
3. View real-time metrics in the Live-Audit HUD
4. Review findings in the Findings tab
5. Generate optimization recommendations
6. Export the Seinsmodus certification badge

## Constants

| Constant | Value | Description |
|----------|-------|-------------|
| φ (PHI) | 1.61803398875 | Golden ratio |
| OMEGA_CRITICAL | 5800.0 | Critical threshold for Ω(t) |
| SEINSMODUS_THRESHOLD | 1.0 | W(t) threshold for Seinsmodus |
| WARNING_THRESHOLD | 0.8 | W(t) threshold for warning |
| W_BASE | 0.7557 | Base weight constant |
| DW | 0.6690 | Delta weight |
| E_PENALTY | 1.201301 | Error penalty |
| M_MASK | 0.643047 | Mask constant |

## License

MIT
