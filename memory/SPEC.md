# AuditIQ Living Spec

## Purpose
AuditIQ is a zero-egress code audit tool. Users can paste source code directly into the browser or import supported local files, then receive a deterministic audit report without a backend, upload, or network dependency.

## Key flows
- Open a starting page that explains zero-egress auditing and shows locally stored successful projects, files, and audited line counts.
- Paste code on the import screen, optionally change the filename and language, choose **Code auditieren**, and review the local progress state and results.
- Clear the paste editor with **Leeren**.
- Import supported local source files or use the bundled demo flow.
- Review verdict, metrics, findings, recommendations, per-file summary, and certificate.
- Export the report or certificate through the browser print dialog.
- Read public reviews loaded from the shared backend.
- Submit one public review after the browser detects a failed audit followed by a successful audit of the same project.
- Compare a repeated audit of the same project against its previous local snapshot, showing fixed, new, and remaining findings.
- Filter the per-file project tree by path, language, and audit status.
- Copy a privacy-safe certificate URL containing only verdict metadata and signatures, never source code or filenames.

## Data and processing
- Source code is held in browser memory only during the audit.
- The existing deterministic client-side engine calculates security pattern counts, performance heuristics, field metrics, and a content signature.
- Audit code and project history remain in browser localStorage and are never sent to the server.
- A small same-origin backend stores and serves public reviews only; review payloads never include source code.
- No authentication, account, or third-party integration is required.

## Supported inputs
JavaScript, TypeScript, JSX/TSX, HTML, CSS/SCSS, JSON, YAML, TOML, and ZIP projects containing these file types. ZIP archives are unpacked locally in the browser with file-size and archive-size limits.

## Audit output
- Security findings include exact source line numbers for every detected pattern.
- The visual certificate shows project identity, audited file and line totals, audit score, status, field metrics, timestamp, and signature.