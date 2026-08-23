// css_parser.js - CSS parser for structural analysis
// Implements deterministic structural analysis for the Code Auditor

class CSSParser {
    constructor() {
        this.PHI = 1.61803398875;
        this.OMEGA_CRITICAL = 5800.0;
        this.ZERO_DEFECT_THRESHOLD = 1.0;
        this.WARNING_THRESHOLD = 0.8;
    }

    // Parse CSS content and extract structural metrics
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
            } else if (trimmed.startsWith('/*') || trimmed.startsWith('*')) {
                metrics.commentLines++;
            } else {
                metrics.codeLines++;
            }
        }

        // Count rule blocks (selectors)
        const ruleBlocks = (content.match(/{/g) || []).length;
        metrics.totalFunctions = ruleBlocks;
        metrics.performance.totalFunctions = ruleBlocks;

        // Cyclomatic complexity = number of selectors + combinators
        const selectors = (content.match(/,/g) || []).length + ruleBlocks;
        metrics.cyclomaticComplexity = selectors;
        metrics.performance.cyclomaticComplexity = selectors;

        // Nesting depth (CSS nesting)
        let maxNest = 0;
        let currentNest = 0;
        for (const char of content) {
            if (char === '{') currentNest++;
            if (char === '}') currentNest--;
            if (currentNest > maxNest) maxNest = currentNest;
        }
        metrics.nestingDepth = maxNest;
        metrics.performance.nestingDepth = maxNest;

        // Nested loops (nested selectors)
        metrics.nestedLoops = Math.max(0, ruleBlocks - 1);
        metrics.performance.nestedLoops = metrics.nestedLoops;

        // Blocking I/O (N/A for CSS)
        metrics.blockingIO = 0;
        metrics.performance.blockingIO = 0;

        // Security scanning (CSS has minimal security concerns)
        metrics.security = this._scanSecurity(content);

        // Cognitive vector computation
        metrics.cognitive.past = metrics.nestingDepth * 0.15;
        metrics.cognitive.present = metrics.totalFunctions * 0.1;
        metrics.cognitive.future = 0.0;

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

        // CSS has minimal security concerns, but check for:
        // - expression() (IE CSS injection)
        sec.evalCount = (content.match(/\bexpression\s*\(/gi) || []).length;

        // - url() with javascript: scheme
        sec.xssRiskCount = (content.match(/url\s*\(\s*['"]?\s*javascript:/gi) || []).length;

        // - @import with external URLs (potential tracking)
        sec.missingAuthCount = (content.match(/@import\s+['"]https?:\/\//gi) || []).length;

        // Total critical findings
        sec.totalCritical = 
            sec.evalCount +
            sec.xssRiskCount +
            sec.missingAuthCount;

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
    module.exports = CSSParser;
}
