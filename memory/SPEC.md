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
- Export the local before/after comparison as part of the printable PDF report.
- Save up to 12 path/language/status filter combinations as browser-local favorites.
- Display a scannable QR code generated locally in the browser for the privacy-safe certificate URL.

## Data and processing
- Source code is held in browser memory only during the audit.
- The existing deterministic client-side engine calculates security pattern counts, performance heuristics, field metrics, and a content signature.
- Audit code and project history remain in browser localStorage and are never sent to the server.
- A small same-origin backend stores and serves public reviews only; review payloads never include source code.
- Production review storage uses MongoDB Atlas via server-only MONGO_URL/DB_NAME. Development falls back to an atomically written local reviews.json if local MongoDB is unavailable.
- A public system-status dashboard reports frontend/backend availability, active review storage mode, and boolean configuration readiness without exposing values, hosts, database names, credentials, or errors.
- MongoDB maintains indexes for review IDs and descending creation time; local JSON fallback updates use one atomic read-modify-write lock.
- HTML always revalidates, while static JS/images cache for one day and compressible assets are served with local gzip when supported.
- No authentication, account, or third-party integration is required.

## Supported inputs
JavaScript, TypeScript, JSX/TSX, HTML, CSS/SCSS, JSON, YAML, TOML, and ZIP projects containing these file types. ZIP archives are unpacked locally in the browser with file-size and archive-size limits.

## Audit output
- Security findings include exact source line numbers for every detected pattern.
- The visual certificate shows project identity, audited file and line totals, audit score, status, field metrics, timestamp, and signature.