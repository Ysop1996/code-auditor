// python_parser.js - Python parser for backend/data code
// Implements deterministic structural analysis for the Code Auditor

class PythonParser {
    constructor() {
        this.PHI = 1.61803398875;
        this.OMEGA_CRITICAL = 5800.0;
        this.ZERO_DEFECT_THRESHOLD = 1.0;
        this.WARNING_THRESHOLD = 0.8;
    }

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
            } else if (trimmed.startsWith('#')) {
                metrics.commentLines++;
            } else {
                metrics.codeLines++;
            }
        }

        // Function/method counting
        const funcPatterns = [
            /\bdef\s+\w+/g,
            /\bclass\s+\w+/g
        ];

        for (const pattern of funcPatterns) {
            const matches = content.match(pattern);
            if (matches) {
                metrics.totalFunctions += matches.length;
            }
        }
        metrics.performance.totalFunctions = metrics.totalFunctions;

        // Cyclomatic complexity
        const decisionPatterns = [
            /\bif\s+/g,
            /\belif\s+/g,
            /\bwhile\s+/g,
            /\bfor\s+/g,
            /\bexcept\s+/g,
            /&&/g,
            /\|\|/g,
            /\band\b/g,
            /\bor\b/g
        ];

        for (const pattern of decisionPatterns) {
            const matches = content.match(pattern);
            if (matches) {
                metrics.cyclomaticComplexity += matches.length;
            }
        }
        metrics.performance.cyclomaticComplexity = metrics.cyclomaticComplexity;

        // Nesting depth (Python uses indentation)
        let maxNest = 0;
        let currentNest = 0;
        for (const line of lines) {
            const stripped = line.replace(/\n$/, '');
            const indent = stripped.match(/^\s*/)[0].length;
            const newNest = Math.floor(indent / 4);
            if (newNest > currentNest) {
                currentNest = newNest;
                if (currentNest > maxNest) maxNest = currentNest;
            } else if (newNest < currentNest) {
                currentNest = newNest;
            }
        }
        metrics.nestingDepth = maxNest;
        metrics.performance.nestingDepth = maxNest;

        // Nested loops
        const forLoops = (content.match(/\bfor\s+/g) || []).length;
        const whileLoops = (content.match(/\bwhile\s+/g) || []).length;
        metrics.nestedLoops = Math.max(0, forLoops + whileLoops - 1);
        metrics.performance.nestedLoops = metrics.nestedLoops;

        // Blocking I/O
        const blockingPatterns = [
            /requests\.get\s*\(/g,
            /requests\.post\s*\(/g,
            /open\s*\([^)]*\)\.read/g,
            /subprocess\.call/g,
            /os\.system\s*\(/g
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

        // Cognitive vector
        metrics.cognitive.past = Math.min(metrics.cyclomaticComplexity * 0.3, 10.0);
        metrics.cognitive.present = Math.min(
            metrics.nestingDepth * 0.5 + metrics.totalFunctions * 0.2,
            10.0
        );
        metrics.cognitive.future = Math.min(metrics.security.totalCritical * 0.5, 10.0);

        if (metrics.totalFunctions > 0) {
            metrics.performance.avgFunctionLength = metrics.totalLines / metrics.totalFunctions;
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

        // eval() usage
        sec.evalCount = (content.match(/\beval\s*\(/g) || []).length;

        // Hardcoded secrets
        const secretPatterns = [
            /password\s*=\s*["'][^"']+["']/gi,
            /secret\s*=\s*["'][^"']+["']/gi,
            /api[_-]?key\s*=\s*["'][^"']+["']/gi,
            /token\s*=\s*["'][^"']+["']/gi
        ];
        for (const pattern of secretPatterns) {
            const matches = content.match(pattern);
            if (matches) sec.hardcodedSecretCount += matches.length;
        }

        // SQL injection
        const sqlPatterns = [
            /execute\s*\(\s*["'].*\+/gi,
            /cursor\.execute\s*\(\s*["'].*\+/gi
        ];
        for (const pattern of sqlPatterns) {
            const matches = content.match(pattern);
            if (matches) sec.sqlConcatCount += matches.length;
        }

        // Path traversal
        sec.pathTraversalCount = 
            (content.match(/\.\.\//g) || []).length +
            (content.match(/\.\.\\/g) || []).length;

        // Insecure random
        sec.insecureRandomCount = 
            (content.match(/\brandom\b/gi) || []).length;

        // Unsafe deserialization
        sec.unsafeDeserializationCount = 
            (content.match(/\bpickle\.loads?\b/g) || []).length +
            (content.match(/\byaml\.load\s*\(/g) || []).length;

        // Log injection
        sec.logInjectionCount = 
            (content.match(/\blogging\.(debug|info|warning|error|critical)\s*\(/g) || []).length;

        // Total critical
        sec.totalCritical = 
            sec.evalCount +
            sec.hardcodedSecretCount +
            sec.sqlConcatCount +
            sec.pathTraversalCount +
            sec.unsafeDeserializationCount;

        return sec;
    }

    computeCognitiveVector(metrics) {
        return {
            past: metrics.cognitive.past,
            present: metrics.cognitive.present,
            future: metrics.cognitive.future
        };
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
    module.exports = PythonParser;
}
