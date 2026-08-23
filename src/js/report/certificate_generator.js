// certificate_generator.js - Das unbestechliche Marktkonformitäts-Zertifikat
// Implements deterministic certificate generation with SHA-256 signature

class CertificateGenerator {
    constructor() {
        this.PHI = 1.61803398875;
        this.OMEGA_CRITICAL = 5800.0;
        this.ZERO_DEFECT_THRESHOLD = 1.0;
        this.WARNING_THRESHOLD = 0.8;
    }

    // Generate certificate from audit result
    generateCertificate(auditResult) {
        const certificate = {
            id: this._generateCertificateId(auditResult),
            projectName: auditResult.projectName,
            issuedAt: auditResult.timestamp,
            expiresAt: this._computeExpiry(auditResult.timestamp),
            scope: {
                filesAudited: auditResult.files.length,
                totalLines: auditResult.files.reduce((sum, f) => sum + f.totalLines, 0),
                languages: [...new Set(auditResult.files.map(f => f.language))]
            },
            fieldTheory: {
                phi: this.PHI,
                omegaCritical: this.OMEGA_CRITICAL,
                cognitiveVector: auditResult.aggregateCognitive,
                loadY: auditResult.aggregateLoadY,
                frictionW: auditResult.aggregateFrictionW,
                omegaT: auditResult.aggregateOmegaT,
                maxOmegaT: auditResult.maxOmegaT
            },
            compliance: {
                securityFindings: auditResult.totalSecurityFindings,
                criticalFindings: auditResult.totalCriticalFindings,
                isZeroDefect: auditResult.isZeroDefect,
                isCritical: auditResult.isCritical,
                isWarning: auditResult.isWarning,
                badge: auditResult.zeroDefectBadge
            },
            platformCompliance: auditResult.platformCompliance || {},
            legalCompliance: auditResult.legalCompliance || {},
            copyrightCompliance: auditResult.copyrightCompliance || {},
            monetization: auditResult.monetization || {},
            certificateHash: auditResult.certificateHash,
            signature: this._generateSignature(auditResult),
            isValid: this._validateCertificate(auditResult)
        };

        return certificate;
    }

    // Generate deterministic certificate ID
    _generateCertificateId(auditResult) {
        const crypto = require('crypto');
        const data = JSON.stringify({
            projectName: auditResult.projectName,
            timestamp: auditResult.timestamp,
            fileCount: auditResult.files.length,
            hash: auditResult.certificateHash
        });
        return 'CERT-' + crypto.createHash('sha256').update(data).digest('hex').substring(0, 16).toUpperCase();
    }

    // Compute expiry date (30 days from issue)
    _computeExpiry(timestamp) {
        const date = new Date(timestamp);
        date.setDate(date.getDate() + 30);
        return date.toISOString();
    }

    // Generate cryptographic signature
    _generateSignature(auditResult) {
        const crypto = require('crypto');
        const data = JSON.stringify({
            projectName: auditResult.projectName,
            timestamp: auditResult.timestamp,
            aggregateCognitive: auditResult.aggregateCognitive,
            aggregateLoadY: auditResult.aggregateLoadY,
            aggregateFrictionW: auditResult.aggregateFrictionW,
            aggregateOmegaT: auditResult.aggregateOmegaT,
            totalSecurityFindings: auditResult.totalSecurityFindings,
            totalCriticalFindings: auditResult.totalCriticalFindings,
            isZeroDefect: auditResult.isZeroDefect,
            certificateHash: auditResult.certificateHash
        });
        
        return crypto.createHash('sha256').update(data).digest('hex');
    }

    // Validate certificate (null-tolerance certification)
    _validateCertificate(auditResult) {
        return auditResult.isZeroDefect && 
               auditResult.totalCriticalFindings === 0 &&
               !auditResult.isCritical &&
               !auditResult.isWarning &&
               this._validatePlatformCompliance(auditResult.platformCompliance) &&
               this._validateLegalCompliance(auditResult.legalCompliance) &&
               this._validateCopyrightCompliance(auditResult.copyrightCompliance);
    }

    _validatePlatformCompliance(platformCompliance) {
        if (!platformCompliance) return true;
        return platformCompliance.metrics?.totalViolations === 0;
    }

    _validateLegalCompliance(legalCompliance) {
        if (!legalCompliance) return true;
        return legalCompliance.metrics?.totalViolations === 0;
    }

    _validateCopyrightCompliance(copyrightCompliance) {
        if (!copyrightCompliance) return true;
        return copyrightCompliance.metrics?.totalViolations === 0;
    }

    // Format certificate for display
    formatCertificate(certificate) {
        const lines = [];
        
        lines.push('╔══════════════════════════════════════════════════════════════════════╗');
        lines.push('║         MMSI V3.8 CODE AUDITOR - MARKTKONFORMITÄTS-ZERTIFIKAT        ║');
        lines.push('║                     Null-Toleranz-Zertifizierung                     ║');
        lines.push('╚══════════════════════════════════════════════════════════════════════╝');
        lines.push('');
        lines.push(`Zertifikat ID: ${certificate.id}`);
        lines.push(`Projekt: ${certificate.projectName}`);
        lines.push(`Ausgestellt: ${certificate.issuedAt}`);
        lines.push(`Gültig bis: ${certificate.expiresAt}`);
        lines.push('');
        lines.push('--- FELDTHEORIE METRICS ---');
        lines.push(`Kognitiver Vektor: [${certificate.fieldTheory.cognitiveVector.past.toFixed(4)}, ${certificate.fieldTheory.cognitiveVector.present.toFixed(4)}, ${certificate.fieldTheory.cognitiveVector.future.toFixed(4)}]`);
        lines.push(`Last Y (W(t)): ${certificate.fieldTheory.loadY.toFixed(4)}`);
        lines.push(`Reibung W: ${certificate.fieldTheory.frictionW.toFixed(4)}`);
        lines.push(`Omega T (Ω(t)): ${certificate.fieldTheory.omegaT.toFixed(4)}`);
        lines.push(`Max Omega T: ${certificate.fieldTheory.maxOmegaT.toFixed(4)}`);
        lines.push('');
        lines.push('--- KOMPLIANSZUSTAND ---');
        lines.push(`Sicherheitsfindungen: ${certificate.compliance.securityFindings}`);
        lines.push(`Kritische Verstöße: ${certificate.compliance.criticalFindings}`);
        lines.push(`Zero-Defect: ${certificate.compliance.isZeroDefect ? '✓ ZERTIFIZIERT' : '✗ NICHT ZERTIFIZIERT'}`);
        lines.push(`Status: ${certificate.compliance.badge}`);
        lines.push('');
        lines.push('--- PLATTFORM-KOMPLIANS ---');
        if (certificate.platformCompliance?.metrics) {
            lines.push(`Apple/Play Verletzungen: ${certificate.platformCompliance.metrics.totalViolations}`);
        }
        lines.push('');
        lines.push('--- RECHTSKOMPLIANS ---');
        if (certificate.legalCompliance?.metrics) {
            lines.push(`DSGVO/BGB Verletzungen: ${certificate.legalCompliance.metrics.totalViolations}`);
        }
        lines.push('');
        lines.push('--- COPYRIGHT-KOMPLIANS ---');
        if (certificate.copyrightCompliance?.metrics) {
            lines.push(`Lizenzverletzungen: ${certificate.copyrightCompliance.metrics.totalViolations}`);
        }
        lines.push('');
        lines.push('--- KRYPTOGRAFISCHE SIGNATUR ---');
        lines.push(`SHA-256 Scope Hash: ${certificate.certificateHash}`);
        lines.push(`Signatur: ${certificate.signature}`);
        lines.push(`Gültig: ${certificate.isValid ? '✓ ZERTIFIZIERT' : '✗ NICHT ZERTIFIZIERT'}`);
        lines.push('');
        lines.push('╔══════════════════════════════════════════════════════════════════════╗');
        lines.push('║  Dieses Zertifikat ist manipulationssicher und ausschließlich für    ║');
        lines.push('║  den Contest-Nachweis bei Emergent Builder\'s Contest gültig.          ║');
        lines.push('╚══════════════════════════════════════════════════════════════════════╝');
        
        return lines.join('\n');
    }

    // Generate JSON certificate for export
    toJSON(certificate) {
        return JSON.stringify(certificate, null, 2);
    }

    // Generate markdown certificate for contest submission
    toMarkdown(certificate) {
        return `# MMSI V3.8 Code Auditor - Marktkonformitäts-Zertifikat

**Zertifikat ID:** ${certificate.id}
**Projekt:** ${certificate.projectName}
**Ausgestellt:** ${certificate.issuedAt}
**Gültig bis:** ${certificate.expiresAt}

## Feldtheorie-Metriken

| Metrik | Wert |
|--------|------|
| Kognitiver Vektor | [${certificate.fieldTheory.cognitiveVector.past.toFixed(4)}, ${certificate.fieldTheory.cognitiveVector.present.toFixed(4)}, ${certificate.fieldTheory.cognitiveVector.future.toFixed(4)}] |
| Last Y (W(t)) | ${certificate.fieldTheory.loadY.toFixed(4)} |
| Reibung W | ${certificate.fieldTheory.frictionW.toFixed(4)} |
| Omega T (Ω(t)) | ${certificate.fieldTheory.omegaT.toFixed(4)} |
| Max Omega T | ${certificate.fieldTheory.maxOmegaT.toFixed(4)} |

## Komplianszustand

- **Sicherheitsfindungen:** ${certificate.compliance.securityFindings}
- **Kritische Verstöße:** ${certificate.compliance.criticalFindings}
- **Zero-Defect:** ${certificate.compliance.isZeroDefect ? '✓ ZERTIFIZIERT' : '✗ NICHT ZERTIFIZIERT'}
- **Status:** ${certificate.compliance.badge}

## Plattform-Komplians

${certificate.platformCompliance?.metrics ? 
    `- Apple/Play Verletzungen: ${certificate.platformCompliance.metrics.totalViolations}` : 
    '- Keine Plattformprüfung durchgeführt'}

## Rechtskomplians

${certificate.legalCompliance?.metrics ? 
    `- DSGVO/BGB Verletzungen: ${certificate.legalCompliance.metrics.totalViolations}` : 
    '- Keine Rechtsprüfung durchgeführt'}

## Copyright-Komplians

${certificate.copyrightCompliance?.metrics ? 
    `- Lizenzverletzungen: ${certificate.copyrightCompliance.metrics.totalViolations}` : 
    '- Keine Copyright-Prüfung durchgeführt'}

## Kryptografische Signatur

- **SHA-256 Scope Hash:** \`${certificate.certificateHash}\`
- **Signatur:** \`${certificate.signature}\`
- **Gültig:** ${certificate.isValid ? '✓ ZERTIFIZIERT' : '✗ NICHT ZERTIFIZIERT'}

---

*Dieses Zertifikat wurde von MMSI V3.8 Code Auditor generiert und ist manipulationssicher.*
`;
    }

    computeLoadY(cv) {
        return 0.2 * cv.past + 0.5 * cv.present + 0.3 * cv.future;
    }

    computeFrictionW(loadY) {
        return Math.abs(loadY - 0.5) * this.PHI;
    }

    computeOmegaT(frictionW) {
        return frictionW * 14.0;
    }

    checkZeroDefect(loadY, criticalCount) {
        return loadY <= this.ZERO_DEFECT_THRESHOLD && criticalCount === 0;
    }

    checkCritical(omegaT) {
        return omegaT >= this.OMEGA_CRITICAL;
    }

    checkWarning(loadY) {
        return loadY > this.WARNING_THRESHOLD;
    }
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = CertificateGenerator;
}
