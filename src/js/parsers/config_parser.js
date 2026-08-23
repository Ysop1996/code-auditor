// config_parser.js - Config file parser (JSON/YAML/TOML)
// Implements deterministic structural analysis for the Code Auditor

class ConfigParser {
    constructor() {
        this.PHI = 1.61803398875;
        this.OMEGA_CRITICAL = 5800.0;
        this.ZERO_DEFECT_THRESHOLD = 1.0;
        this.WARNING_THRESHOLD = 0.8;
    }

    // Parse config content and extract structural metrics
    parse(content, filename = '') {
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
            } else if (trimmed.startsWith('#') || trimmed.startsWith('//') || trimmed.startsWith('/*')) {
                metrics.commentLines++;
            } else {
                metrics.codeLines++;
            }
        }

        // Count configuration keys (as "functions" for metric purposes)
        const keyMatches = content.match(/^[a-zA-Z_][a-zA-Z0-9_]*\s*:/g) || [];
        metrics.totalFunctions = keyMatches.length;
        metrics.performance.totalFunctions = keyMatches.length;

        // Cyclomatic complexity = number of config entries
        metrics.cyclomaticComplexity = keyMatches.length;
        metrics.performance.cyclomaticComplexity = keyMatches.length;

        // Nesting depth (JSON/YAML structure depth)
        let maxNest = 0;
        let currentNest = 0;
        for (const char of content) {
            if (char === '{' || char === '[') currentNest++;
            if (char === '}' || char === ']') currentNest--;
            if (currentNest > maxNest) maxNest = currentNest;
        }
        metrics.nestingDepth = maxNest;
        metrics.performance.nestingDepth = maxNest;

        // Nested loops (N/A for config)
        metrics.nestedLoops = 0;
        metrics.performance.nestedLoops = 0;

        // Blocking I/O (N/A for config)
        metrics.blockingIO = 0;
        metrics.performance.blockingIO = 0;

        // Security scanning
        metrics.security = this._scanSecurity(content);

        // Cognitive vector computation
        metrics.cognitive.past = 0.0;
        metrics.cognitive.present = metrics.totalLines * 0.01;
        metrics.cognitive.future = metrics.security.totalCritical * 0.5;

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

        // 5. Hardcoded secrets in config files
        const secretPatterns = [
            /password\s*[:=]\s*['"]?[^'"\s,}]+/gi,
            /secret\s*[:=]\s*['"]?[^'"\s,}]+/gi,
            /token\s*[:=]\s*['"]?[^'"\s,}]+/gi,
            /api_?key\s*[:=]\s*['"]?[^'"\s,}]+/gi,
            /private_?key\s*[:=]\s*['"]?[^'"\s,}]+/gi,
            /access_?key\s*[:=]\s*['"]?[^'"\s,}]+/gi
        ];
        for (const pattern of secretPatterns) {
            const matches = content.match(pattern);
            if (matches) sec.hardcodedSecretCount += matches.length;
        }

        // 17. Path traversal
        sec.pathTraversalCount = 
            (content.match(/\.\.\//g) || []).length +
            (content.match(/\.\.\\/g) || []).length;

        // Total critical findings
        sec.totalCritical = 
            sec.hardcodedSecretCount +
            sec.pathTraversalCount;

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
    module.exports = ConfigParser;
}
