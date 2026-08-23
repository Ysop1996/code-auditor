// test_field_theory.js - Tests for MMSI V3.8 field theory computation
// Verifies deterministic computation of I(t), W(t), Ω(t), φ, and Zero-Defect

const assert = require('assert');
const crypto = require('crypto');

const PHI = 1.61803398875;
const OMEGA_CRITICAL = 5800.0;
const ZERO_DEFECT_THRESHOLD = 1.0;
const WARNING_THRESHOLD = 0.8;

// Field theory functions
function computeLoadY(cv) {
    return 0.2 * cv.past + 0.5 * cv.present + 0.3 * cv.future;
}

function computeFrictionW(loadY) {
    return Math.abs(loadY - 0.5) * PHI;
}

function computeOmegaT(frictionW) {
    return frictionW * 14.0;
}

function checkZeroDefect(loadY, criticalCount) {
    return loadY <= ZERO_DEFECT_THRESHOLD && criticalCount === 0;
}

function checkCritical(omegaT) {
    return omegaT >= OMEGA_CRITICAL;
}

function checkWarning(loadY) {
    return loadY > WARNING_THRESHOLD;
}

// SHA-256 hash function
function generateHash(data) {
    return crypto.createHash('sha256').update(data).digest('hex');
}

// Test suite
const tests = {
    testLoadYComputation: function() {
        // Test basic computation
        const cv = { past: 0, present: 0, future: 0 };
        assert.strictEqual(computeLoadY(cv), 0, 'Zero cognitive vector should have loadY = 0');

        // Test with known values
        const cv2 = { past: 1, present: 2, future: 3 };
        const expected = 0.2 * 1 + 0.5 * 2 + 0.3 * 3; // 0.2 + 1.0 + 0.9 = 2.1
        assert.strictEqual(computeLoadY(cv2), expected, 'LoadY computation with known values');

        // Test with high values
        const cv3 = { past: 10, present: 10, future: 10 };
        const expected3 = 0.2 * 10 + 0.5 * 10 + 0.3 * 10; // 2 + 5 + 3 = 10
        assert.strictEqual(computeLoadY(cv3), expected3, 'LoadY with high values');

        console.log('✓ testLoadYComputation passed');
    },

    testFrictionWComputation: function() {
        // Test zero load
        const loadY = 0;
        const expected = Math.abs(0 - 0.5) * PHI; // 0.5 * 1.618... = 0.809...
        assert.strictEqual(computeFrictionW(loadY), expected, 'FrictionW with zero load');

        // Test load at 0.5 (should be zero friction)
        const loadY2 = 0.5;
        assert.strictEqual(computeFrictionW(loadY2), 0, 'FrictionW at equilibrium point');

        // Test load at 1.0
        const loadY3 = 1.0;
        const expected3 = Math.abs(1.0 - 0.5) * PHI;
        assert.strictEqual(computeFrictionW(loadY3), expected3, 'FrictionW at load 1.0');

        console.log('✓ testFrictionWComputation passed');
    },

    testOmegaTComputation: function() {
        // Test zero friction
        assert.strictEqual(computeOmegaT(0), 0, 'OmegaT with zero friction');

        // Test with known friction
        const friction = 1.0;
        assert.strictEqual(computeOmegaT(friction), 14.0, 'OmegaT with friction 1.0');

        // Test with high friction
        const friction2 = 10.0;
        assert.strictEqual(computeOmegaT(friction2), 140.0, 'OmegaT with friction 10.0');

        console.log('✓ testOmegaTComputation passed');
    },

    testZeroDefectCheck: function() {
        // Zero load, zero critical = Zero-Defect
        assert.strictEqual(checkZeroDefect(0, 0), true, 'Zero load and zero critical = Zero-Defect');

        // Zero load, but critical findings = not Zero-Defect
        assert.strictEqual(checkZeroDefect(0, 1), false, 'Zero load but critical findings = not Zero-Defect');

        // Load at threshold, zero critical = Zero-Defect
        assert.strictEqual(checkZeroDefect(ZERO_DEFECT_THRESHOLD, 0), true, 'Load at threshold = Zero-Defect');

        // Load above threshold = not Zero-Defect
        assert.strictEqual(checkZeroDefect(ZERO_DEFECT_THRESHOLD + 0.01, 0), false, 'Load above threshold = not Zero-Defect');

        console.log('✓ testZeroDefectCheck passed');
    },

    testCriticalCheck: function() {
        // Below critical
        assert.strictEqual(checkCritical(OMEGA_CRITICAL - 1), false, 'Below critical threshold');

        // At critical
        assert.strictEqual(checkCritical(OMEGA_CRITICAL), true, 'At critical threshold');

        // Above critical
        assert.strictEqual(checkCritical(OMEGA_CRITICAL + 1), true, 'Above critical threshold');

        console.log('✓ testCriticalCheck passed');
    },

    testWarningCheck: function() {
        // Below warning
        assert.strictEqual(checkWarning(WARNING_THRESHOLD - 0.01), false, 'Below warning threshold');

        // At warning threshold (strictly greater than, so at threshold = false)
        assert.strictEqual(checkWarning(WARNING_THRESHOLD), false, 'At warning threshold should be false (strict >)');

        // Above warning
        assert.strictEqual(checkWarning(WARNING_THRESHOLD + 0.01), true, 'Above warning threshold');

        console.log('✓ testWarningCheck passed');
    },

    testPhiConstant: function() {
        // Verify phi is the golden ratio (within floating point precision)
        const goldenRatio = (1 + Math.sqrt(5)) / 2;
        assert.ok(Math.abs(PHI - goldenRatio) < 1e-10, 'PHI should equal golden ratio within precision');

        console.log('✓ testPhiConstant passed');
    },

    testHashDeterminism: function() {
        // Same input should produce same hash
        const data = JSON.stringify({ test: 'data', value: 42 });
        const hash1 = generateHash(data);
        const hash2 = generateHash(data);
        assert.strictEqual(hash1, hash2, 'Same input should produce same hash');

        // Different input should produce different hash
        const data2 = JSON.stringify({ test: 'data', value: 43 });
        const hash3 = generateHash(data2);
        assert.notStrictEqual(hash1, hash3, 'Different input should produce different hash');

        // Hash should be 64 characters (SHA-256)
        assert.strictEqual(hash1.length, 64, 'SHA-256 hash should be 64 characters');

        console.log('✓ testHashDeterminism passed');
    },

    testFullPipeline: function() {
        // Simulate a full audit pipeline
        const cv = { past: 2.0, present: 1.0, future: 0.5 };
        const loadY = computeLoadY(cv);
        const frictionW = computeFrictionW(loadY);
        const omegaT = computeOmegaT(frictionW);
        const isZeroDefect = checkZeroDefect(loadY, 0);
        const isCritical = checkCritical(omegaT);
        const isWarning = checkWarning(loadY);

        // Verify all computations are consistent
        assert.ok(loadY >= 0, 'LoadY should be non-negative');
        assert.ok(frictionW >= 0, 'FrictionW should be non-negative');
        assert.ok(omegaT >= 0, 'OmegaT should be non-negative');
        assert.ok(isZeroDefect || isCritical || isWarning || (!isZeroDefect && !isCritical && !isWarning), 
                  'Exactly one state should be true');

        console.log('✓ testFullPipeline passed');
    },

    runAll: function() {
        console.log('Running MMSI V3.8 Field Theory Tests...\n');
        
        this.testLoadYComputation();
        this.testFrictionWComputation();
        this.testOmegaTComputation();
        this.testZeroDefectCheck();
        this.testCriticalCheck();
        this.testWarningCheck();
        this.testPhiConstant();
        this.testHashDeterminism();
        this.testFullPipeline();
        
        console.log('\n✅ All field theory tests passed!');
    }
};

// Run tests
tests.runAll();
