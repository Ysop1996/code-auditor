// legal_compliance.js - DSGVO, ePrivacy, BGB, AGB compliance scanner
// Implements deterministic legal compliance checking for the Code Auditor

class LegalComplianceScanner {
    constructor() {
        this.PHI = 1.61803398875;
        this.OMEGA_CRITICAL = 5800.0;
        this.ZERO_DEFECT_THRESHOLD = 1.0;
        this.WARNING_THRESHOLD = 0.8;
    }

    // Scan for legal compliance issues
    scanLegalCompliance(files) {
        const findings = [];
        const metrics = {
            trackingPixels: [],
            missingImprint: false,
            missingPrivacyPolicy: false,
            agbIssues: [],
            totalViolations: 0
        };

        for (const file of files) {
            const content = file.content;
            const filename = file.filename;

            // Tracking-Vollbremse: Blockiert Analyse- und Werbepixel vor Opt-in
            const trackingPatterns = [
                { pattern: /gtag\s*\(/g, name: 'Google Analytics gtag' },
                { pattern: /ga\s*\(/g, name: 'Google Analytics ga' },
                { pattern: /fbq\s*\(/g, name: 'Facebook Pixel' },
                { pattern: /twttr\.com\/widgets/g, name: 'Twitter Widget' },
                { pattern: /TikTokAnalytics/g, name: 'TikTok Pixel' },
                { pattern: /_paq/g, name: 'Matomo/Piwik' },
                { pattern: /Hotjar/g, name: 'Hotjar' },
                { pattern: /clarity\.microsoft\.com/g, name: 'Microsoft Clarity' }
            ];

            for (const { pattern, name } of trackingPatterns) {
                const matches = content.match(pattern);
                if (matches) {
                    const hasOptIn = content.includes('consent') || 
                                   content.includes('opt-in') ||
                                   content.includes('cookie') ||
                                   content.includes('GDPR');
                    
                    if (!hasOptIn) {
                        metrics.trackingPixels.push({ name, file: filename, count: matches.length });
                        metrics.totalViolations++;
                        findings.push({
                            type: 'tracking_without_consent',
                            severity: 'critical',
                            message: `${name} detected without documented opt-in mechanism`,
                            file: filename,
                            penalty: 50.0,
                            recommendation: 'Implement explicit consent mechanism before loading tracking scripts'
                        });
                    }
                }
            }

            // Pflichtangaben-Check: Impressum (§ 5 DDG)
            if (filename.includes('imprint') || filename.includes('impressum') || 
                filename.includes('about') || filename.includes('contact')) {
                // Check if it contains required elements
                const requiredElements = ['Adresse', 'Telefon', 'E-Mail', 'Name', 'Vertreter'];
                const missing = requiredElements.filter(elem => !content.includes(elem));
                if (missing.length > 0) {
                    metrics.missingImprint = true;
                    metrics.totalViolations++;
                    findings.push({
                        type: 'missing_imprint_elements',
                        severity: 'high',
                        message: `Imprint missing required elements: ${missing.join(', ')}`,
                        file: filename,
                        penalty: 30.0,
                        recommendation: `Add missing imprint elements: ${missing.join(', ')}`
                    });
                }
            }

            // Datenschutzerklärung (Art. 13 DSGVO)
            if (filename.includes('privacy') || filename.includes('datenschutz')) {
                const requiredSections = ['Zwecke', 'Rechtsgrundlage', 'Speicherdauer', 'Betroffenenrechte'];
                const missing = requiredSections.filter(s => !content.includes(s));
                if (missing.length > 0) {
                    metrics.missingPrivacyPolicy = true;
                    metrics.totalViolations++;
                    findings.push({
                        type: 'incomplete_privacy_policy',
                        severity: 'high',
                        message: `Privacy policy missing required sections: ${missing.join(', ')}`,
                        file: filename,
                        penalty: 35.0,
                        recommendation: `Add missing GDPR sections: ${missing.join(', ')}`
                    });
                }
            }

            // AGB-Audit: Unwirksamkeit nach §§ 307–309 BGB
            const agbIssues = this._scanAGB(content, filename);
            if (agbIssues.length > 0) {
                metrics.agbIssues.push(...agbIssues);
                metrics.totalViolations += agbIssues.length;
                findings.push(...agbIssues);
            }
        }

        // Check if imprint/privacy policy files exist at all
        const hasImprint = files.some(f => 
            f.filename.includes('imprint') || 
            f.filename.includes('impressum') ||
            f.filename.toLowerCase().includes('imprint')
        );
        if (!hasImprint) {
            metrics.missingImprint = true;
            metrics.totalViolations++;
            findings.push({
                type: 'missing_imprint_file',
                severity: 'critical',
                message: 'No imprint file found - required by § 5 DDG',
                file: 'project root',
                penalty: 40.0,
                recommendation: 'Create an imprint page with address, phone, email, and legal representative'
            });
        }

        const hasPrivacy = files.some(f => 
            f.filename.includes('privacy') || 
            f.filename.includes('datenschutz')
        );
        if (!hasPrivacy) {
            metrics.missingPrivacyPolicy = true;
            metrics.totalViolations++;
            findings.push({
                type: 'missing_privacy_file',
                severity: 'critical',
                message: 'No privacy policy file found - required by Art. 13 DSGVO',
                file: 'project root',
                penalty: 45.0,
                recommendation: 'Create a privacy policy with data processing purposes, legal basis, and retention periods'
            });
        }

        return { metrics, findings };
    }

    _scanAGB(content, filename) {
        const findings = [];
        
        // § 307 BGB: Allgemeine Geschäftsbedingungen
        // Check for unfair terms
        const unfairTerms = [
            { pattern: /unlimited\s+right\s+to\s+terminate/i, message: 'Unilateral termination right without notice period' },
            { pattern: /we\s+reserve\s+the\s+right\s+to\s+change/i, message: 'Unilateral right to change terms without consent' },
            { pattern: /no\s+warranty/i, message: 'Complete exclusion of warranty - may be unfair under § 307 BGB' },
            { pattern: /no\s+liability/i, message: 'Complete exclusion of liability - may be unfair under § 307 BGB' },
            { pattern: /sole\s+discretion/i, message: 'Unilateral decision-making without user rights' }
        ];

        for (const { pattern, message } of unfairTerms) {
            if (content.match(pattern)) {
                findings.push({
                    type: 'agb_unfair_term',
                    severity: 'high',
                    message: `Potentially unfair term under § 307 BGB: ${message}`,
                    file: filename,
                    penalty: 25.0,
                    recommendation: 'Review and revise terms to comply with § 307 BGB fairness requirements'
                });
            }
        }

        return findings;
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
    module.exports = LegalComplianceScanner;
}
