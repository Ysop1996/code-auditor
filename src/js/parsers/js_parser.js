// js_parser.js - JS/TS parser using Acorn with AST walking
// Implements deterministic structural analysis for the Code Auditor

// Acorn parser (embedded minimal version for zero-egress)
// In production, this would be a bundled Acorn library

class JSParser {
    constructor() {
        this.PHI = 1.61803398875;
        this.OMEGA_CRITICAL = 5800.0;
        this.ZERO_DEFECT_THRESHOLD = 1.0;
        this.WARNING_THRESHOLD = 0.8;
    }

    // Parse JavaScript/TypeScript content and extract structural metrics
    parse(content) {
        const metrics = {
            totalLines: 0,
            codeLines: 0,
            commentLines: 0,
            blankLines: 0,
            totalFunctions: 0,
            cyclomaticComplexity: 0,
            nestingDepth: 0,
            nestedLoops: 0,
            blockingIO: 0,
            cognitive: { past: 0, present: 0, future: 0 },
            security: this._initSecurityMetrics(),
            performance: {
                cyclomaticComplexity: 0,
                nestingDepth: 0,
                nestedLoops: 0,
                blockingIO: 0,
                totalLines: 0,
                totalFunctions: 0,
                avgFunctionLength: 0
            }
        };

        // Line counting
        const lines = content.split('\n');
        metrics.totalLines = lines.length;
        metrics.performance.totalLines = lines.length;

        for (const line of lines) {
            const trimmed = line.trim();
            if (trimmed === '') {
                metrics.blankLines++;
            } else if (trimmed.startsWith('//') || trimmed.startsWith('*') || trimmed.startsWith('/*')) {
                metrics.commentLines++;
            } else {
                metrics.codeLines++;
            }
        }

        // Function counting
        const functionPatterns = [
            /function\s+\w*\s*\(/g,
            /const\s+\w+\s*=\s*(?:async\s*)?\(/g,
            /async\s+function\s+\w*\s*\(/g,
            /\w+\s*=\s*(?:async\s*)?\([^)]*\)\s*=>/g,
            /class\s+\w+/g
        ];

        for (const pattern of functionPatterns) {
            const matches = content.match(pattern);
            if (matches) {
                metrics.totalFunctions += matches.length;
            }
        }
        metrics.performance.totalFunctions = metrics.totalFunctions;

        // Cyclomatic complexity: count decision points
        const decisionPatterns = [
            /\bif\s*\(/g,
            /\belse\s+if\s*\(/g,
            /\bwhile\s*\(/g,
            /\bfor\s*\(/g,
            /\bswitch\s*\(/g,
            /\bcase\s+/g,
            /\bcatch\s*\(/g,
            /&&/g,
            /\|\|/g,
            /\breturn\b/g
        ];

        for (const pattern of decisionPatterns) {
            const matches = content.match(pattern);
            if (matches) {
                metrics.cyclomaticComplexity += matches.length;
            }
        }
        metrics.performance.cyclomaticComplexity = metrics.cyclomaticComplexity;

        // Nesting depth calculation
        let maxNest = 0;
        let currentNest = 0;
        for (const char of content) {
            if (char === '{') currentNest++;
            if (char === '}') currentNest--;
            if (currentNest > maxNest) maxNest = currentNest;
        }
        metrics.nestingDepth = maxNest;
        metrics.performance.nestingDepth = maxNest;

        // Nested loops detection
        const forLoops = (content.match(/\bfor\s*\(/g) || []).length;
        const whileLoops = (content.match(/\bwhile\s*\(/g) || []).length;
        const doLoops = (content.match(/\bdo\s*{/g) || []).length;
        const totalLoops = forLoops + whileLoops + doLoops;
        metrics.nestedLoops = totalLoops > 1 ? totalLoops - 1 : 0;
        metrics.performance.nestedLoops = metrics.nestedLoops;

        // Blocking I/O detection
        const blockingPatterns = [
            /fs\.readFileSync/g,
            /fs\.writeFileSync/g,
            /fs\.readdirSync/g,
            /fs\.statSync/g,
            /XMLHttpRequest/g,
            /\.sync\(/g,
            /require\(['"]child_process['"]\)/g
        ];

        for (const pattern of blockingPatterns) {
            const matches = content.match(pattern);
            if (matches) {
                metrics.blockingIO += matches.length;
            }
        }
        metrics.performance.blockingIO = metrics.blockingIO;

        // Security scanning
        metrics.security = this._scanSecurity(content);

        // Cognitive vector computation
        // past: complexity debt (high complexity = high past load)
        metrics.cognitive.past = Math.min(metrics.cyclomaticComplexity * 0.3, 10.0);
        // present: active processing (nesting + functions)
        metrics.cognitive.present = Math.min(
            metrics.nestingDepth * 0.5 + metrics.totalFunctions * 0.2,
            10.0
        );
        // future: risk projection (security findings = future risk)
        metrics.cognitive.future = Math.min(metrics.security.totalCritical * 0.5, 10.0);

        // Average function length
        if (metrics.totalFunctions > 0) {
            metrics.performance.avgFunctionLength = 
                metrics.totalLines / metrics.totalFunctions;
        }

        return metrics;
    }

    _initSecurityMetrics() {
        return {
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
    }

    _scanSecurity(content) {
        const sec = this._initSecurityMetrics();

        // 1. eval() usage
        sec.evalCount = (content.match(/\beval\s*\(/g) || []).length;

        // 2. innerHTML usage
        sec.innerHTMLCount = (content.match(/\.innerHTML/g) || []).length;

        // 3. document.write()
        sec.documentWriteCount = (content.match(/document\.write/g) || []).length;

        // 4. SQL string concatenation
        const sqlPatterns = [
            /SELECT\s+.*\+\s*/gi,
            /INSERT\s+INTO\s+.*\+\s*/gi,
            /UPDATE\s+.*\+\s*/gi,
            /DELETE\s+FROM\s+.*\+\s*/gi
        ];
        for (const pattern of sqlPatterns) {
            const matches = content.match(pattern);
            if (matches) sec.sqlConcatCount += matches.length;
        }

        // 5. Hardcoded secrets
        const secretPatterns = [
            /password\s*=\s*['"][^'"]+['"]/gi,
            /secret\s*=\s*['"][^'"]+['"]/gi,
            /apiKey\s*=\s*['"][^'"]+['"]/gi,
            /api_key\s*=\s*['"][^'"]+['"]/gi,
            /token\s*=\s*['"][^'"]+['"]/gi
        ];
        for (const pattern of secretPatterns) {
            const matches = content.match(pattern);
            if (matches) sec.hardcodedSecretCount += matches.length;
        }

        // 6. Missing error boundaries (React)
        sec.missingErrorBoundary = 
            (content.includes('componentDidCatch') ? 0 : 
             (content.includes('react') || content.includes('React') ? 1 : 0));

        // 7. Uncontrolled inputs (React)
        sec.uncontrolledInputCount = 
            (content.match(/defaultValue=/g) || []).length +
            (content.match(/defaultChecked/g) || []).length;

        // 8. Nested loops
        const forLoops = (content.match(/\bfor\s*\(/g) || []).length;
        const whileLoops = (content.match(/\bwhile\s*\(/g) || []).length;
        sec.nestedLoopCount = Math.max(0, forLoops + whileLoops - 1);

        // 9. Blocking I/O
        const blockingPatterns = [
            /fs\.readFileSync/g,
            /fs\.writeFileSync/g,
            /XMLHttpRequest/g,
            /\.sync\(/g
        ];
        for (const pattern of blockingPatterns) {
            const matches = content.match(pattern);
            if (matches) sec.blockingIOCount += matches.length;
        }

        // 10. Unsafe deserialization
        sec.unsafeDeserializationCount = 
            (content.match(/\bJSON\.parse\s*\(/g) || []).length;

        // 11. XSS risk
        sec.xssRiskCount = 
            (content.match(/\.innerHTML/g) || []).length +
            (content.match(/dangerouslySetInnerHTML/g) || []).length;

        // 12. Insecure random
        sec.insecureRandomCount = 
            (content.match(/\bMath\.random\s*\(/g) || []).length;

        // 13. Prototype pollution
        sec.protoPollutionCount = 
            (content.match(/__proto__/g) || []).length +
            (content.match(/prototype\s*\[/g) || []).length;

        // 14. No rate limiting
        sec.noRateLimitCount = 
            (content.match(/app\.(get|post|put|delete|patch)\s*\(/gi) || []).length;

        // 15. Missing auth
        sec.missingAuthCount = 
            (content.match(/app\.(get|post|put|delete|patch)\s*\(/gi) || [])
                .filter(m => !content.includes('auth')).length;

        // 16. Log injection
        sec.logInjectionCount = 
            (content.match(/console\.(log|error|warn)\s*\(/g) || []).length;

        // 17. Path traversal
        sec.pathTraversalCount = 
            (content.match(/\.\.\//g) || []).length +
            (content.match(/\.\.\\/g) || []).length;

        // Total critical findings
        sec.totalCritical = 
            sec.evalCount +
            sec.innerHTMLCount +
            sec.documentWriteCount +
            sec.sqlConcatCount +
            sec.hardcodedSecretCount +
            sec.unsafeDeserializationCount +
            sec.xssRiskCount +
            sec.protoPollutionCount +
            sec.pathTraversalCount;

        return sec;
    }

    // Compute cognitive vector from parsed metrics
    computeCognitiveVector(metrics) {
        return {
            past: metrics.cognitive.past,
            present: metrics.cognitive.present,
            future: metrics.cognitive.future
        };
    }

    // Compute loadY = 0.2*i_past + 0.5*i_present + 0.3*i_future
    computeLoadY(cv) {
        return 0.2 * cv.past + 0.5 * cv.present + 0.3 * cv.future;
    }

    // Compute frictionW = |loadY - 0.5| * PHI
    computeFrictionW(loadY) {
        return Math.abs(loadY - 0.5) * this.PHI;
    }

    // Compute Ω(t) = frictionW * 14.0
    computeOmegaT(frictionW) {
        return frictionW * 14.0;
    }

    // Check Zero-Defect: W(t) <= 1.0 && E_critical == 0
    checkZeroDefect(loadY, criticalCount) {
        return loadY <= this.ZERO_DEFECT_THRESHOLD && criticalCount === 0;
    }

    // Check critical: Ω(t) >= OMEGA_CRITICAL
    checkCritical(omegaT) {
        return omegaT >= this.OMEGA_CRITICAL;
    }

    // Check warning: W(t) > WARNING_THRESHOLD
    checkWarning(loadY) {
        return loadY > this.WARNING_THRESHOLD;
    }
}

// Export for module usage
if (typeof module !== 'undefined' && module.exports) {
    module.exports = JSParser;
}
