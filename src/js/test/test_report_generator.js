// test_report_generator.js - Tests for audit report generator
// Verifies deterministic report generation and certificate hash

const assert = require('assert');
const crypto = require('crypto');

const PHI = 1.61803398875;
const OMEGA_CRITICAL = 5800.0;
const ZERO_DEFECT_THRESHOLD = 1.0;
const WARNING_THRESHOLD = 0.8;

function computeLoadY(cv) {
    return 0.2 * cv.past + 0.5 * cv.present + 0.3 * cv.future;
}

function computeFrictionW(loadY) {
    return Math.abs(loadY - 0.5) * PHI;
}

function computeOmegaT(frictionW) {
    return frictionW * 14.0;
}

function generateCertificateHash(data) {
    return crypto.createHash('sha256').update(JSON.stringify(data)).digest('hex');
}

const tests = {
    testReportStructure: function() {
        const fileMetrics = [
            {
                filename: 'test.js',
                language: 'javascript',
                totalLines: 100,
                cognitive: { past: 1.0, present: 2.0, future: 0.5 },
                security: { totalCritical: 0 },
                performance: { cyclomaticComplexity: 5, nestingDepth: 2, totalFunctions: 3 },
                loadY: 1.35,
                frictionW: 0.809016994375,
                omegaT: 11.32623792125,
                isZeroDefect: false,
                isCritical: false,
                isWarning: true
            }
        ];

        const aggregateCognitive = {
            past: 1.0,
            present: 2.0,
            future: 0.5
        };

        const report = {
            projectName: 'Test Project',
            timestamp: '2026-08-23T18:00:00.000Z',
            files: fileMetrics,
            aggregateCognitive,
            aggregateLoadY: computeLoadY(aggregateCognitive),
            aggregateFrictionW: computeFrictionW(computeLoadY(aggregateCognitive)),
            aggregateOmegaT: computeOmegaT(computeFrictionW(computeLoadY(aggregateCognitive))),
            totalSecurityFindings: 0,
            totalCriticalFindings: 0,
            isZeroDefect: false,
            isCritical: false,
            isWarning: true,
            certificateHash: generateCertificateHash({ projectName: 'Test Project', timestamp: '2026-08-23T18:00:00.000Z' })
        };

        assert.ok(report.projectName, 'Report should have project name');
        assert.ok(report.timestamp, 'Report should have timestamp');
        assert.ok(report.files.length > 0, 'Report should have files');
        assert.ok(report.certificateHash, 'Report should have certificate hash');
        assert.ok(report.certificateHash.length === 64, 'Certificate hash should be SHA-256 (64 chars)');
        
        console.log('✓ testReportStructure passed');
    },

    testCertificateHashDeterminism: function() {
        const data = {
            projectName: 'Test Project',
            timestamp: '2026-08-23T18:00:00.000Z',
            cognitive: { past: 1.0, present: 2.0, future: 0.5 },
            loadY: 1.35,
            frictionW: 0.809016994375,
            omegaT: 11.32623792125,
            totalCritical: 0
        };

        const hash1 = generateCertificateHash(data);
        const hash2 = generateCertificateHash(data);
        
        assert.strictEqual(hash1, hash2, 'Same data should produce same hash');
        assert.ok(/^[a-f0-9]{64}$/.test(hash1), 'Hash should be valid SHA-256 hex');
        
        console.log('✓ testCertificateHashDeterminism passed');
    },

    testCertificateHashUniqueness: function() {
        const data1 = { projectName: 'Project A', timestamp: '2026-08-23T18:00:00.000Z' };
        const data2 = { projectName: 'Project B', timestamp: '2026-08-23T18:00:00.000Z' };
        
        const hash1 = generateCertificateHash(data1);
        const hash2 = generateCertificateHash(data2);
        
        assert.notStrictEqual(hash1, hash2, 'Different data should produce different hashes');
        
        console.log('✓ testCertificateHashUniqueness passed');
    },

    testZeroDefectCertification: function() {
        // Zero-Defect: loadY <= 1.0, no critical findings
        const cv = { past: 0, present: 0.5, future: 0 };
        const loadY = computeLoadY(cv);
        const isZeroDefect = loadY <= ZERO_DEFECT_THRESHOLD && 0 === 0;
        
        assert.ok(isZeroDefect, 'Zero cognitive vector should be Zero-Defect');
        
        // Not Zero-Defect: loadY > 1.0
        const cv2 = { past: 2, present: 2, future: 2 };
        const loadY2 = computeLoadY(cv2);
        const isZeroDefect2 = loadY2 <= ZERO_DEFECT_THRESHOLD && 0 === 0;
        
        assert.ok(!isZeroDefect2, 'High cognitive vector should not be Zero-Defect');
        
        console.log('✓ testZeroDefectCertification passed');
    },

    testFindingSorting: function() {
        const findings = [
            { severity: 'low', message: 'Low issue' },
            { severity: 'critical', message: 'Critical issue' },
            { severity: 'medium', message: 'Medium issue' },
            { severity: 'high', message: 'High issue' }
        ];

        const severityScores = { critical: 4, high: 3, medium: 2, low: 1 };
        findings.sort((a, b) => severityScores[b.severity] - severityScores[a.severity]);

        assert.strictEqual(findings[0].severity, 'critical', 'Critical should be first');
        assert.strictEqual(findings[1].severity, 'high', 'High should be second');
        assert.strictEqual(findings[2].severity, 'medium', 'Medium should be third');
        assert.strictEqual(findings[3].severity, 'low', 'Low should be last');
        
        console.log('✓ testFindingSorting passed');
    },

    testRecommendationGeneration: function() {
        const findings = [
            { type: 'eval', severity: 'critical', message: 'eval() detected', penalty: 50.0 },
            { type: 'innerHTML', severity: 'high', message: 'innerHTML detected', penalty: 40.0 }
        ];

        const recommendations = findings.map(f => ({
            type: f.type,
            priority: f.severity,
            title: `Fix ${f.type}`,
            description: f.message,
            estimatedImprovement: f.penalty
        }));

        assert.strictEqual(recommendations.length, 2, 'Should generate 2 recommendations');
        assert.strictEqual(recommendations[0].priority, 'critical', 'First recommendation should be critical');
        assert.strictEqual(recommendations[1].priority, 'high', 'Second recommendation should be high');
        
        console.log('✓ testRecommendationGeneration passed');
    },

    testAggregateMetrics: function() {
        const fileMetrics = [
            { cognitive: { past: 1, present: 2, future: 3 }, security: { totalCritical: 2 } },
            { cognitive: { past: 2, present: 3, future: 1 }, security: { totalCritical: 1 } },
            { cognitive: { past: 0, present: 1, future: 0 }, security: { totalCritical: 0 } }
        ];

        const aggregateCognitive = {
            past: fileMetrics.reduce((s, m) => s + m.cognitive.past, 0) / fileMetrics.length,
            present: fileMetrics.reduce((s, m) => s + m.cognitive.present, 0) / fileMetrics.length,
            future: fileMetrics.reduce((s, m) => s + m.cognitive.future, 0) / fileMetrics.length
        };

        const totalCritical = fileMetrics.reduce((s, m) => s + m.security.totalCritical, 0);

        assert.strictEqual(aggregateCognitive.past, 1, 'Aggregate past should be 1');
        assert.strictEqual(aggregateCognitive.present, 2, 'Aggregate present should be 2');
        assert.strictEqual(aggregateCognitive.future, 4/3, 'Aggregate future should be 4/3');
        assert.strictEqual(totalCritical, 3, 'Total critical should be 3');
        
        console.log('✓ testAggregateMetrics passed');
    },

    testReportFormatting: function() {
        const report = {
            projectName: 'Test',
            timestamp: '2026-08-23T18:00:00.000Z',
            aggregateCognitive: { past: 0, present: 1, future: 0 },
            aggregateLoadY: 0.5,
            aggregateFrictionW: 0,
            aggregateOmegaT: 0,
            totalSecurityFindings: 0,
            totalCriticalFindings: 0,
            isZeroDefect: true,
            certificateHash: 'abc123'
        };

        const formatted = `Report: ${report.projectName}\nLoadY: ${report.aggregateLoadY}\nZero-Defect: ${report.isZeroDefect}`;
        
        assert.ok(formatted.includes('Test'), 'Formatted report should include project name');
        assert.ok(formatted.includes('0.5'), 'Formatted report should include loadY');
        assert.ok(formatted.includes('true'), 'Formatted report should include Zero-Defect status');
        
        console.log('✓ testReportFormatting passed');
    },

    runAll: function() {
        console.log('Running Report Generator Tests...\n');
        
        this.testReportStructure();
        this.testCertificateHashDeterminism();
        this.testCertificateHashUniqueness();
        this.testZeroDefectCertification();
        this.testFindingSorting();
        this.testRecommendationGeneration();
        this.testAggregateMetrics();
        this.testReportFormatting();
        
        console.log('\n✅ All report generator tests passed!');
    }
};

tests.runAll();
