// java_kotlin_parser.js - Java/Kotlin parser for Android/mobile code
// Implements deterministic structural analysis for the Code Auditor

class JavaKotlinParser {
    constructor() {
        this.PHI = 1.61803398875;
        this.OMEGA_CRITICAL = 5800.0;
        this.ZERO_DEFECT_THRESHOLD = 1.0;
        this.WARNING_THRESHOLD = 0.8;
    }

    parse(content, language = 'java') {
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
            } else if (trimmed.startsWith('//') || trimmed.startsWith('/*') || trimmed.startsWith('*')) {
                metrics.commentLines++;
            } else {
                metrics.codeLines++;
            }
        }

        // Method counting (Java/Kotlin)
        const methodPatterns = [
            /\b(public|private|protected|internal)\s+(?:static\s+)?[\w<>,\s]+\s+\w+\s*\(/g,
            /\bfun\s+\w+\s*\(/g,  // Kotlin
            /\b(override\s+)?fun\s+\w+\s*\(/g  // Kotlin override
        ];

        for (const pattern of methodPatterns) {
            const matches = content.match(pattern);
            if (matches) {
                metrics.totalFunctions += matches.length;
            }
        }
        metrics.performance.totalFunctions = metrics.totalFunctions;

        // Cyclomatic complexity: decision points
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
            /\?:|\?\s/g  // ternary
        ];

        for (const pattern of decisionPatterns) {
            const matches = content.match(pattern);
            if (matches) {
                metrics.cyclomaticComplexity += matches.length;
            }
        }
        metrics.performance.cyclomaticComplexity = metrics.cyclomaticComplexity;

        // Nesting depth
        let maxNest = 0;
        let currentNest = 0;
        for (const char of content) {
            if (char === '{') currentNest++;
            if (char === '}') currentNest--;
            if (currentNest > maxNest) maxNest = currentNest;
        }
        metrics.nestingDepth = maxNest;
        metrics.performance.nestingDepth = maxNest;

        // Nested loops
        const forLoops = (content.match(/\bfor\s*\(/g) || []).length;
        const whileLoops = (content.match(/\bwhile\s*\(/g) || []).length;
        metrics.nestedLoops = Math.max(0, forLoops + whileLoops - 1);
        metrics.performance.nestedLoops = metrics.nestedLoops;

        // Blocking I/O
        const blockingPatterns = [
            /Thread\.sleep/g,
            /URLConnection\.connect/g,
            /HttpURLConnection/g,
            /\.execute/g  // AsyncTask execute
        ];

        for (const pattern of blockingPatterns) {
            const matches = content.match(pattern);
            if (matches) {
                metrics.blockingIO += matches.length;
            }
        }
        metrics.performance.blockingIO = metrics.blockingIO;

        // Security scanning
        metrics.security = this._scanSecurity(content, language);

        // Cognitive vector
        metrics.cognitive.past = Math.min(metrics.cyclomaticComplexity * 0.3, 10.0);
        metrics.cognitive.present = Math.min(
            metrics.nestingDepth * 0.5 + metrics.totalFunctions * 0.2,
            10.0
        );
        metrics.cognitive.future = Math.min(metrics.security.totalCritical * 0.5, 10.0);

        // Average function length
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

    _scanSecurity(content, language) {
        const sec = this._initSecurityMetrics();

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
            /Statement\.executeQuery\s*\(\s*["'].*\+/gi,
            /prepareStatement\s*\(\s*["'].*\+/gi,
            /createQuery\s*\(\s*["'].*\+/gi
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
            (content.match(/\bRandom\b/g) || []).length;

        // Log injection
        sec.logInjectionCount = 
            (content.match(/Log\.(d|i|e|w|v)\s*\(/g) || []).length;

        // Unsafe deserialization
        sec.unsafeDeserializationCount = 
            (content.match(/\bObjectInputStream\b/g) || []).length +
            (content.match(/\breadObject\s*\(/g) || []).length;

        // Total critical
        sec.totalCritical = 
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
    module.exports = JavaKotlinParser;
}
