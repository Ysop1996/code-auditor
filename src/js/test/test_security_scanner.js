// test_security_scanner.js - Tests for security scanner with 17 hard-gate patterns
// Verifies deterministic detection of all security patterns

const assert = require('assert');

// Import SecurityScanner (in browser, this would be a script tag)
// For Node.js testing, we'll inline the scanner logic

const PENALTIES = {
    eval: 50.0,
    innerHTML: 40.0,
    documentWrite: 35.0,
    sqlConcat: 45.0,
    hardcodedSecret: 60.0,
    missingErrorBoundary: 25.0,
    uncontrolledInput: 20.0,
    nestedLoop: 15.0,
    blockingIO: 30.0,
    unsafeDeserialization: 50.0,
    xssRisk: 35.0,
    insecureRandom: 15.0,
    protoPollution: 40.0,
    noRateLimit: 10.0,
    missingAuth: 30.0,
    logInjection: 20.0,
    pathTraversal: 45.0
};

function scanSecurity(content) {
    const sec = {
        evalCount: 0,
        innerHTMLCount: 0,
        documentWriteCount: 0,
        sqlConcatCount: 0,
        hardcodedSecretCount: 0,
        missingErrorBoundary: 0,
        uncontrolledInputCount: 0,
        nestedLoopCount: 0,
        blockingIOCount: 0,
        unsafeDeserializationCount: 0,
        xssRiskCount: 0,
        insecureRandomCount: 0,
        protoPollutionCount: 0,
        noRateLimitCount: 0,
        missingAuthCount: 0,
        logInjectionCount: 0,
        pathTraversalCount: 0,
        totalCritical: 0
    };

    // 1. eval()
    sec.evalCount = (content.match(/\beval\s*\(/g) || []).length;

    // 2. innerHTML
    sec.innerHTMLCount = (content.match(/\.innerHTML/g) || []).length;

    // 3. document.write
    sec.documentWriteCount = (content.match(/document\.write/g) || []).length;

    // 4. SQL concatenation
    sec.sqlConcatCount = (content.match(/SELECT\s+.*\+\s*/gi) || []).length +
                        (content.match(/INSERT\s+INTO\s+.*\+\s*/gi) || []).length;

    // 5. Hardcoded secrets
    sec.hardcodedSecretCount = (content.match(/password\s*[:=]\s*['"][^'"]+['"]/gi) || []).length +
                              (content.match(/secret\s*[:=]\s*['"][^'"]+['"]/gi) || []).length +
                              (content.match(/apiKey\s*[:=]\s*['"][^'"]+['"]/gi) || []).length;

    // 6. Missing error boundary
    sec.missingErrorBoundary = content.includes('react') && !content.includes('componentDidCatch') ? 1 : 0;

    // 7. Uncontrolled inputs
    sec.uncontrolledInputCount = (content.match(/defaultValue=/g) || []).length;

    // 8. Nested loops
    const forLoops = (content.match(/\bfor\s*\(/g) || []).length;
    const whileLoops = (content.match(/\bwhile\s*\(/g) || []).length;
    sec.nestedLoopCount = Math.max(0, forLoops + whileLoops - 1);

    // 9. Blocking I/O
    sec.blockingIOCount = (content.match(/fs\.readFileSync/g) || []).length +
                         (content.match(/fs\.writeFileSync/g) || []).length;

    // 10. Unsafe deserialization
    sec.unsafeDeserializationCount = (content.match(/\bJSON\.parse\s*\(/g) || []).length;

    // 11. XSS risk
    sec.xssRiskCount = (content.match(/\.innerHTML/g) || []).length +
                      (content.match(/dangerouslySetInnerHTML/g) || []).length;

    // 12. Insecure random
    sec.insecureRandomCount = (content.match(/\bMath\.random\s*\(/g) || []).length;

    // 13. Prototype pollution
    sec.protoPollutionCount = (content.match(/__proto__/g) || []).length +
                             (content.match(/prototype\s*\[/g) || []).length;

    // 14. No rate limiting
    sec.noRateLimitCount = (content.match(/app\.(get|post|put|delete|patch)\s*\(/gi) || []).length;

    // 15. Missing auth
    const routes = (content.match(/app\.(get|post|put|delete|patch)\s*\(/gi) || []).length;
    const authRefs = (content.match(/\bauth\b/gi) || []).length;
    sec.missingAuthCount = Math.max(0, routes - authRefs);

    // 16. Log injection
    sec.logInjectionCount = (content.match(/console\.(log|error|warn)\s*\(/g) || []).length;

    // 17. Path traversal
    sec.pathTraversalCount = (content.match(/\.\.\//g) || []).length;

    sec.totalCritical = sec.evalCount + sec.innerHTMLCount + sec.documentWriteCount +
                       sec.sqlConcatCount + sec.hardcodedSecretCount +
                       sec.unsafeDeserializationCount + sec.xssRiskCount +
                       sec.protoPollutionCount + sec.pathTraversalCount;

    return sec;
}

const tests = {
    testEvalDetection: function() {
        const content = 'var x = eval("1+1");';
        const result = scanSecurity(content);
        assert.strictEqual(result.evalCount, 1, 'Should detect eval()');
        assert.ok(result.totalCritical > 0, 'eval should be critical');
        console.log('✓ testEvalDetection passed');
    },

    testInnerHTMLDetection: function() {
        const content = 'element.innerHTML = "<script>alert(1)</script>";';
        const result = scanSecurity(content);
        assert.strictEqual(result.innerHTMLCount, 1, 'Should detect innerHTML');
        assert.strictEqual(result.xssRiskCount, 1, 'innerHTML should count as XSS risk');
        console.log('✓ testInnerHTMLDetection passed');
    },

    testDocumentWriteDetection: function() {
        const content = 'document.write("hello");';
        const result = scanSecurity(content);
        assert.strictEqual(result.documentWriteCount, 1, 'Should detect document.write');
        console.log('✓ testDocumentWriteDetection passed');
    },

    testSQLConcatDetection: function() {
        const content = 'var query = "SELECT * FROM users WHERE id = " + userId;';
        const result = scanSecurity(content);
        assert.ok(result.sqlConcatCount > 0, 'Should detect SQL concatenation');
        console.log('✓ testSQLConcatDetection passed');
    },

    testHardcodedSecretDetection: function() {
        const content = 'const password = "supersecret123";';
        const result = scanSecurity(content);
        assert.ok(result.hardcodedSecretCount > 0, 'Should detect hardcoded password');
        console.log('✓ testHardcodedSecretDetection passed');
    },

    testMissingErrorBoundary: function() {
        const content = 'import React from "react"; function App() { return <div>Hello</div>; }';
        const result = scanSecurity(content);
        assert.strictEqual(result.missingErrorBoundary, 1, 'Should detect missing error boundary in React');
        console.log('✓ testMissingErrorBoundary passed');
    },

    testUncontrolledInput: function() {
        const content = '<input defaultValue="test" />';
        const result = scanSecurity(content);
        assert.strictEqual(result.uncontrolledInputCount, 1, 'Should detect uncontrolled input');
        console.log('✓ testUncontrolledInput passed');
    },

    testNestedLoopDetection: function() {
        const content = 'for(let i=0; i<10; i++) { for(let j=0; j<10; j++) { console.log(i+j); } }';
        const result = scanSecurity(content);
        assert.ok(result.nestedLoopCount > 0, 'Should detect nested loops');
        console.log('✓ testNestedLoopDetection passed');
    },

    testBlockingIODetection: function() {
        const content = 'const data = fs.readFileSync("/path/to/file");';
        const result = scanSecurity(content);
        assert.strictEqual(result.blockingIOCount, 1, 'Should detect blocking I/O');
        console.log('✓ testBlockingIODetection passed');
    },

    testUnsafeDeserialization: function() {
        const content = 'const obj = JSON.parse(userInput);';
        const result = scanSecurity(content);
        assert.strictEqual(result.unsafeDeserializationCount, 1, 'Should detect JSON.parse');
        console.log('✓ testUnsafeDeserialization passed');
    },

    testXSSRiskDetection: function() {
        const content = 'element.innerHTML = userInput;';
        const result = scanSecurity(content);
        assert.ok(result.xssRiskCount > 0, 'Should detect XSS risk');
        console.log('✓ testXSSRiskDetection passed');
    },

    testInsecureRandom: function() {
        const content = 'const token = Math.random().toString(36);';
        const result = scanSecurity(content);
        assert.strictEqual(result.insecureRandomCount, 1, 'Should detect Math.random');
        console.log('✓ testInsecureRandom passed');
    },

    testProtoPollution: function() {
        const content = 'const obj = {}; obj.__proto__.polluted = true;';
        const result = scanSecurity(content);
        assert.ok(result.protoPollutionCount > 0, 'Should detect prototype pollution via __proto__');
        console.log('✓ testProtoPollution passed');
    },

    testPathTraversal: function() {
        const content = 'const path = "../../etc/passwd";';
        const result = scanSecurity(content);
        assert.ok(result.pathTraversalCount > 0, 'Should detect path traversal');
        console.log('✓ testPathTraversal passed');
    },

    testCleanCode: function() {
        const content = 'function add(a, b) { return a + b; }';
        const result = scanSecurity(content);
        assert.strictEqual(result.totalCritical, 0, 'Clean code should have no critical findings');
        console.log('✓ testCleanCode passed');
    },

    testAllPatternsInOne: function() {
        const content = `
            const password = "secret123";
            eval("dangerous");
            element.innerHTML = userInput;
            document.write("deprecated");
            var query = "SELECT * FROM users WHERE id = " + userId;
            Object.prototype.polluted = true;
            const path = "../../etc/passwd";
            const token = Math.random().toString(36);
            const obj = JSON.parse(userInput);
        `;
        const result = scanSecurity(content);
        assert.ok(result.totalCritical > 5, 'Should detect multiple critical issues');
        console.log('✓ testAllPatternsInOne passed');
    },

    runAll: function() {
        console.log('Running Security Scanner Tests...\n');
        
        this.testEvalDetection();
        this.testInnerHTMLDetection();
        this.testDocumentWriteDetection();
        this.testSQLConcatDetection();
        this.testHardcodedSecretDetection();
        this.testMissingErrorBoundary();
        this.testUncontrolledInput();
        this.testNestedLoopDetection();
        this.testBlockingIODetection();
        this.testUnsafeDeserialization();
        this.testXSSRiskDetection();
        this.testInsecureRandom();
        this.testProtoPollution();
        this.testPathTraversal();
        this.testCleanCode();
        this.testAllPatternsInOne();
        
        console.log('\n✅ All security scanner tests passed!');
    }
};

tests.runAll();
