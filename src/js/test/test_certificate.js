// test_certificate.js - Tests for certificate generation, validation, and formatting
// Verifies MMSI V3.8 certificate with null-tolerance validation and deterministic signatures

const assert = require('assert');
const crypto = require('crypto');

const CertificateGenerator = require('../report/certificate_generator');
const ReportGenerator = require('../report/report_generator');

const PHI = 1.61803398875;
const OMEGA_CRITICAL = 5800.0;
const ZERO_DEFECT_THRESHOLD = 1.0;

// Helper: Create mock file metrics
function createMockFileMetrics(overrides = {}) {
    return {
        filename: overrides.filename || 'test.js',
        language: overrides.language || 'javascript',
        totalLines: overrides.totalLines || 100,
        totalFunctions: overrides.totalFunctions || 5,
        cognitive: overrides.cognitive || { past: 1.0, present: 2.0, future: 0.5 },
        security: overrides.security || { totalCritical: 0 },
        performance: overrides.performance || {
            cyclomaticComplexity: 5,
            nestingDepth: 2,
            nestedLoops: 0,
            blockingIO: 0,
            totalLines: 100,
            totalFunctions: 5,
            avgFunctionLength: 20
        },
        load_y: overrides.load_y || 1.35,
        friction_w: overrides.friction_w || 0.809016994375,
        omega_t: overrides.omega_t || 11.32623792125,
        is_zero_defect: overrides.is_zero_defect !== undefined ? overrides.is_zero_defect : false,
        ...overrides
    };
}

// Helper: Create mock audit result
function createMockAuditResult(overrides = {}) {
    return {
        projectName: overrides.projectName || 'Test Project',
        timestamp: overrides.timestamp || '2026-08-23T18:00:00.000Z',
        files: overrides.files || [createMockFileMetrics()],
        aggregateCognitive: overrides.aggregateCognitive || { past: 1.0, present: 2.0, future: 0.5 },
        aggregateLoadY: overrides.aggregateLoadY || 1.35,
        aggregateFrictionW: overrides.aggregateFrictionW || 0.809016994375,
        aggregateOmegaT: overrides.aggregateOmegaT || 11.32623792125,
        maxOmegaT: overrides.maxOmegaT || 11.32623792125,
        totalSecurityFindings: overrides.totalSecurityFindings || 0,
        totalCriticalFindings: overrides.totalCriticalFindings || 0,
        isZeroDefect: overrides.isZeroDefect || false,
        isCritical: overrides.isCritical || false,
        isWarning: overrides.isWarning || true,
        zeroDefectBadge: overrides.zeroDefectBadge || 'WARNING',
        certificateHash: overrides.certificateHash || 'a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2',
        platformCompliance: overrides.platformCompliance || { metrics: { totalViolations: 0 } },
        legalCompliance: overrides.legalCompliance || { metrics: { totalViolations: 0 } },
        copyrightCompliance: overrides.copyrightCompliance || { metrics: { totalViolations: 0 } },
        monetization: overrides.monetization || {},
        ...overrides
    };
}

const tests = {
    testCertificateGeneration: function() {
        const cg = new CertificateGenerator();
        const auditResult = createMockAuditResult();
        
        const cert = cg.generateCertificate(auditResult);
        
        assert.ok(cert.id, 'Certificate should have an ID');
        assert.ok(cert.id.startsWith('CERT-'), 'Certificate ID should start with CERT-');
        assert.strictEqual(cert.id.length, 21, 'Certificate ID should be CERT- + 16 hex chars (21 total)');
        assert.strictEqual(cert.projectName, 'Test Project', 'Certificate should have project name');
        assert.ok(cert.issuedAt, 'Certificate should have issue date');
        assert.ok(cert.expiresAt, 'Certificate should have expiry date');
        assert.ok(cert.signature, 'Certificate should have signature');
        assert.strictEqual(cert.signature.length, 64, 'Signature should be SHA-256 (64 chars)');
        assert.ok(/^[a-f0-9]{64}$/.test(cert.signature), 'Signature should be valid hex');
        console.log('✓ testCertificateGeneration passed');
    },

    testCertificateExpiry: function() {
        const cg = new CertificateGenerator();
        const auditResult = createMockAuditResult({
            timestamp: '2026-08-23T18:00:00.000Z'
        });
        
        const cert = cg.generateCertificate(auditResult);
        
        const issued = new Date(cert.issuedAt);
        const expires = new Date(cert.expiresAt);
        const diffDays = (expires - issued) / (1000 * 60 * 60 * 24);
        
        assert.strictEqual(diffDays, 30, 'Certificate should expire in exactly 30 days');
        console.log('✓ testCertificateExpiry passed');
    },

    testCertificateScope: function() {
        const cg = new CertificateGenerator();
        const auditResult = createMockAuditResult({
            files: [
                createMockFileMetrics({ filename: 'a.js', language: 'javascript', totalLines: 100 }),
                createMockFileMetrics({ filename: 'b.ts', language: 'typescript', totalLines: 200 }),
                createMockFileMetrics({ filename: 'c.py', language: 'python', totalLines: 50 })
            ]
        });
        
        const cert = cg.generateCertificate(auditResult);
        
        assert.strictEqual(cert.scope.filesAudited, 3, 'Should audit 3 files');
        assert.strictEqual(cert.scope.totalLines, 350, 'Should total 350 lines');
        assert.deepStrictEqual(cert.scope.languages.sort(), ['javascript', 'python', 'typescript'].sort(), 
            'Should detect all languages');
        console.log('✓ testCertificateScope passed');
    },

    testCertificateFieldTheory: function() {
        const cg = new CertificateGenerator();
        const auditResult = createMockAuditResult({
            aggregateCognitive: { past: 1.0, present: 2.0, future: 0.5 },
            aggregateLoadY: 1.35,
            aggregateFrictionW: 0.809016994375,
            aggregateOmegaT: 11.32623792125,
            maxOmegaT: 11.32623792125
        });
        
        const cert = cg.generateCertificate(auditResult);
        
        assert.strictEqual(cert.fieldTheory.phi, PHI, 'Certificate should include PHI constant');
        assert.strictEqual(cert.fieldTheory.omegaCritical, OMEGA_CRITICAL, 'Certificate should include OMEGA_CRITICAL');
        assert.deepStrictEqual(cert.fieldTheory.cognitiveVector, { past: 1.0, present: 2.0, future: 0.5 },
            'Certificate should include cognitive vector');
        assert.strictEqual(cert.fieldTheory.loadY, 1.35, 'Certificate should include loadY');
        assert.strictEqual(cert.fieldTheory.frictionW, 0.809016994375, 'Certificate should include frictionW');
        assert.strictEqual(cert.fieldTheory.omegaT, 11.32623792125, 'Certificate should include omegaT');
        console.log('✓ testCertificateFieldTheory passed');
    },

    testCertificateCompliance: function() {
        const cg = new CertificateGenerator();
        const auditResult = createMockAuditResult({
            totalSecurityFindings: 5,
            totalCriticalFindings: 2,
            isZeroDefect: false,
            isCritical: false,
            isWarning: true,
            zeroDefectBadge: 'WARNING',
            platformCompliance: { metrics: { totalViolations: 1 } },
            legalCompliance: { metrics: { totalViolations: 0 } },
            copyrightCompliance: { metrics: { totalViolations: 2 } }
        });
        
        const cert = cg.generateCertificate(auditResult);
        
        assert.strictEqual(cert.compliance.securityFindings, 5, 'Should report security findings count');
        assert.strictEqual(cert.compliance.criticalFindings, 2, 'Should report critical findings count');
        assert.strictEqual(cert.compliance.isZeroDefect, false, 'Should report Zero-Defect status');
        assert.strictEqual(cert.compliance.badge, 'WARNING', 'Should report badge');
        assert.ok(cert.platformCompliance, 'Should include platform compliance');
        assert.ok(cert.legalCompliance, 'Should include legal compliance');
        assert.ok(cert.copyrightCompliance, 'Should include copyright compliance');
        console.log('✓ testCertificateCompliance passed');
    },

    testCertificateValidationZeroDefect: function() {
        const cg = new CertificateGenerator();
        
        // Zero-Defect certificate (valid)
        const zeroDefectResult = createMockAuditResult({
            isZeroDefect: true,
            totalCriticalFindings: 0,
            isCritical: false,
            isWarning: false,
            zeroDefectBadge: 'ZERO_DEFECT_CERTIFIED',
            platformCompliance: { metrics: { totalViolations: 0 } },
            legalCompliance: { metrics: { totalViolations: 0 } },
            copyrightCompliance: { metrics: { totalViolations: 0 } }
        });
        
        const cert = cg.generateCertificate(zeroDefectResult);
        assert.strictEqual(cert.isValid, true, 'Zero-Defect certificate should be valid');
        console.log('✓ testCertificateValidationZeroDefect passed');
    },

    testCertificateValidationInvalid: function() {
        const cg = new CertificateGenerator();
        
        // Invalid certificate (has critical findings)
        const invalidResult = createMockAuditResult({
            isZeroDefect: false,
            totalCriticalFindings: 5,
            isCritical: false,
            isWarning: true,
            zeroDefectBadge: 'WARNING',
            platformCompliance: { metrics: { totalViolations: 0 } },
            legalCompliance: { metrics: { totalViolations: 0 } },
            copyrightCompliance: { metrics: { totalViolations: 0 } }
        });
        
        const cert = cg.generateCertificate(invalidResult);
        assert.strictEqual(cert.isValid, false, 'Certificate with critical findings should be invalid');
        console.log('✓ testCertificateValidationInvalid passed');
    },

    testCertificateValidationWithComplianceViolations: function() {
        const cg = new CertificateGenerator();
        
        // Zero-Defect but has platform compliance violations
        const result = createMockAuditResult({
            isZeroDefect: true,
            totalCriticalFindings: 0,
            isCritical: false,
            isWarning: false,
            zeroDefectBadge: 'ZERO_DEFECT_CERTIFIED',
            platformCompliance: { metrics: { totalViolations: 2 } },
            legalCompliance: { metrics: { totalViolations: 0 } },
            copyrightCompliance: { metrics: { totalViolations: 0 } }
        });
        
        const cert = cg.generateCertificate(result);
        assert.strictEqual(cert.isValid, false, 'Certificate with compliance violations should be invalid');
        console.log('✓ testCertificateValidationWithComplianceViolations passed');
    },

    testCertificateNullTolerance: function() {
        const cg = new CertificateGenerator();
        
        // All compliance fields null
        const result = createMockAuditResult({
            isZeroDefect: true,
            totalCriticalFindings: 0,
            isCritical: false,
            isWarning: false,
            zeroDefectBadge: 'ZERO_DEFECT_CERTIFIED',
            platformCompliance: null,
            legalCompliance: null,
            copyrightCompliance: null,
            monetization: null
        });
        
        const cert = cg.generateCertificate(result);
        assert.strictEqual(cert.isValid, true, 'Certificate should be valid with null compliance fields');
        assert.ok(cert.id, 'Certificate should still have ID');
        assert.ok(cert.signature, 'Certificate should still have signature');
        console.log('✓ testCertificateNullTolerance passed');
    },

    testCertificateDeterminism: function() {
        const cg = new CertificateGenerator();
        const auditResult = createMockAuditResult();
        
        const cert1 = cg.generateCertificate(auditResult);
        const cert2 = cg.generateCertificate(auditResult);
        
        assert.strictEqual(cert1.id, cert2.id, 'Certificate ID must be deterministic');
        assert.strictEqual(cert1.signature, cert2.signature, 'Certificate signature must be deterministic');
        assert.strictEqual(cert1.certificateHash, cert2.certificateHash, 'Certificate hash must be deterministic');
        console.log('✓ testCertificateDeterminism passed');
    },

    testCertificateFormatText: function() {
        const cg = new CertificateGenerator();
        const auditResult = createMockAuditResult({
            isZeroDefect: true,
            totalCriticalFindings: 0,
            isCritical: false,
            isWarning: false,
            zeroDefectBadge: 'ZERO_DEFECT_CERTIFIED',
            platformCompliance: { metrics: { totalViolations: 0 } },
            legalCompliance: { metrics: { totalViolations: 0 } },
            copyrightCompliance: { metrics: { totalViolations: 0 } }
        });
        
        const cert = cg.generateCertificate(auditResult);
        const formatted = cg.formatCertificate(cert);
        
        assert.ok(formatted.includes('MMSI V3.8'), 'Text format should include MMSI V3.8');
        assert.ok(formatted.includes('Zertifikat ID'), 'Text format should include certificate ID');
        assert.ok(formatted.includes('ZERO_DEFECT_CERTIFIED'), 'Text format should include badge');
        assert.ok(formatted.includes('SHA-256'), 'Text format should mention SHA-256');
        assert.ok(formatted.includes('Signatur'), 'Text format should include signature');
        console.log('✓ testCertificateFormatText passed');
    },

    testCertificateFormatJSON: function() {
        const cg = new CertificateGenerator();
        const auditResult = createMockAuditResult();
        
        const cert = cg.generateCertificate(auditResult);
        const json = cg.toJSON(cert);
        
        const parsed = JSON.parse(json);
        assert.strictEqual(parsed.id, cert.id, 'JSON should preserve certificate ID');
        assert.strictEqual(parsed.signature, cert.signature, 'JSON should preserve signature');
        assert.strictEqual(parsed.fieldTheory.phi, PHI, 'JSON should preserve PHI');
        console.log('✓ testCertificateFormatJSON passed');
    },

    testCertificateFormatMarkdown: function() {
        const cg = new CertificateGenerator();
        const auditResult = createMockAuditResult();
        
        const cert = cg.generateCertificate(auditResult);
        const md = cg.toMarkdown(cert);
        
        assert.ok(md.includes('# MMSI V3.8'), 'Markdown should include title');
        assert.ok(md.includes('**Zertifikat ID:**'), 'Markdown should include certificate ID');
        assert.ok(md.includes('| Metrik | Wert |'), 'Markdown should include field theory table');
        assert.ok(md.includes('`'), 'Markdown should use code formatting for hashes');
        console.log('✓ testCertificateFormatMarkdown passed');
    },

    testCertificateWithReportGenerator: function() {
        const rg = new ReportGenerator();
        const cg = new CertificateGenerator();
        
        // Use clean metrics that achieve Zero-Defect (loadY <= 1.0, no critical findings)
        const fileMetrics = [createMockFileMetrics({
            cognitive: { past: 0, present: 0.5, future: 0 },
            security: { totalCritical: 0 }
        })];
        const report = rg.generateReport('Integration Test', fileMetrics, null);
        
        // Build audit result from report
        const auditResult = {
            projectName: report.projectName,
            timestamp: report.timestamp,
            files: report.files,
            aggregateCognitive: report.aggregateCognitive,
            aggregateLoadY: report.aggregateLoadY,
            aggregateFrictionW: report.aggregateFrictionW,
            aggregateOmegaT: report.aggregateOmegaT,
            maxOmegaT: report.maxOmegaT,
            totalSecurityFindings: report.totalSecurityFindings,
            totalCriticalFindings: report.totalCriticalFindings,
            isZeroDefect: report.isZeroDefect,
            isCritical: report.isCritical,
            isWarning: report.isWarning,
            zeroDefectBadge: report.zeroDefectBadge,
            certificateHash: report.certificateHash,
            platformCompliance: null,
            legalCompliance: null,
            copyrightCompliance: null,
            monetization: null
        };
        
        const cert = cg.generateCertificate(auditResult);
        assert.ok(cert.id, 'Certificate should be generated from report');
        assert.strictEqual(cert.certificateHash, report.certificateHash, 'Certificate hash should match report hash');
        assert.ok(cert.isValid, 'Certificate should be valid for clean code');
        console.log('✓ testCertificateWithReportGenerator passed');
    },

    testCertificateZeroDefectBadge: function() {
        const cg = new CertificateGenerator();
        
        // Test ZERO_DEFECT_CERTIFIED badge
        const zeroDefectResult = createMockAuditResult({
            isZeroDefect: true,
            zeroDefectBadge: 'ZERO_DEFECT_CERTIFIED'
        });
        const cert1 = cg.generateCertificate(zeroDefectResult);
        assert.strictEqual(cert1.compliance.badge, 'ZERO_DEFECT_CERTIFIED', 'Should have Zero-Defect badge');
        
        // Test WARNING badge
        const warningResult = createMockAuditResult({
            isZeroDefect: false,
            zeroDefectBadge: 'WARNING'
        });
        const cert2 = cg.generateCertificate(warningResult);
        assert.strictEqual(cert2.compliance.badge, 'WARNING', 'Should have WARNING badge');
        
        // Test PROCESSING_MODE badge
        const processingResult = createMockAuditResult({
            isZeroDefect: false,
            zeroDefectBadge: 'PROCESSING_MODE'
        });
        const cert3 = cg.generateCertificate(processingResult);
        assert.strictEqual(cert3.compliance.badge, 'PROCESSING_MODE', 'Should have PROCESSING_MODE badge');
        
        console.log('✓ testCertificateZeroDefectBadge passed');
    },

    runAll: function() {
        console.log('Running Certificate Generation Tests...\n');
        
        this.testCertificateGeneration();
        this.testCertificateExpiry();
        this.testCertificateScope();
        this.testCertificateFieldTheory();
        this.testCertificateCompliance();
        this.testCertificateValidationZeroDefect();
        this.testCertificateValidationInvalid();
        this.testCertificateValidationWithComplianceViolations();
        this.testCertificateNullTolerance();
        this.testCertificateDeterminism();
        this.testCertificateFormatText();
        this.testCertificateFormatJSON();
        this.testCertificateFormatMarkdown();
        this.testCertificateWithReportGenerator();
        this.testCertificateZeroDefectBadge();
        
        console.log('\n✅ All certificate tests passed!');
    }
};

// Run tests
tests.runAll();
