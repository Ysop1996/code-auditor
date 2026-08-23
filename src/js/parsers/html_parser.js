// html_parser.js - HTML parser using DOMParser
// Implements deterministic structural analysis for the Code Auditor

class HTMLParser {
    constructor() {
        this.PHI = 1.61803398875;
        this.OMEGA_CRITICAL = 5800.0;
        this.ZERO_DEFECT_THRESHOLD = 1.0;
        this.WARNING_THRESHOLD = 0.8;
    }

    // Parse HTML content and extract structural metrics
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
            } else if (trimmed.startsWith('<!--') || trimmed.startsWith('--')) {
                metrics.commentLines++;
            } else {
                metrics.codeLines++;
            }
        }

        // DOM node counting (simplified: count opening tags)
        const tagMatches = content.match(/<[a-zA-Z][a-zA-Z0-9]*\b/g) || [];
        metrics.totalFunctions = tagMatches.length; // Using totalFunctions as DOM node count
        metrics.performance.totalFunctions = tagMatches.length;

        // Count specific element types for complexity
        const interactiveElements = (content.match(/<(button|input|select|textarea|form)\b/gi) || []).length;
        const semanticElements = (content.match(/<(article|section|nav|header|footer|main|aside)\b/gi) || []).length;
        const mediaElements = (content.match(/<(img|video|audio|canvas|svg)\b/gi) || []).length;

        // Cyclomatic complexity = number of selectors/elements
        metrics.cyclomaticComplexity = tagMatches.length + interactiveElements;
        metrics.performance.cyclomaticComplexity = metrics.cyclomaticComplexity;

        // Nesting depth calculation
        let maxNest = 0;
        let currentNest = 0;
        const openTagPattern = /<[a-zA-Z][a-zA-Z0-9]*\b(?![^>]*\/>)/g;
        const closeTagPattern = /<\/[a-zA-Z][a-zA-Z0-9]*\s*>/g;
        
        const allTags = content.match(/<\/?[a-zA-Z][a-zA-Z0-9]*\b[^>]*>/g) || [];
        for (const tag of allTags) {
            if (tag.startsWith('</')) {
                currentNest--;
            } else if (!tag.endsWith('/>')) {
                currentNest++;
                if (currentNest > maxNest) maxNest = currentNest;
            }
        }
        metrics.nestingDepth = maxNest;
        metrics.performance.nestingDepth = maxNest;

        // Nested loops (tables within tables, etc.)
        const tableCount = (content.match(/<table\b/gi) || []).length;
        const divCount = (content.match(/<div\b/gi) || []).length;
        metrics.nestedLoops = Math.max(0, tableCount + divCount - 1);
        metrics.performance.nestedLoops = metrics.nestedLoops;

        // Blocking I/O (inline scripts)
        metrics.blockingIO = (content.match(/<script[^>]*>[^<]+<\/script>/gi) || []).length;
        metrics.performance.blockingIO = metrics.blockingIO;

        // Security scanning
        metrics.security = this._scanSecurity(content);

        // Cognitive vector computation
        metrics.cognitive.past = metrics.nestingDepth * 0.2;
        metrics.cognitive.present = metrics.totalFunctions * 0.1;
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

        // 1. eval() in inline scripts
        sec.evalCount = (content.match(/\beval\s*\(/g) || []).length;

        // 2. innerHTML
        sec.innerHTMLCount = (content.match(/\.innerHTML/g) || []).length;

        // 3. document.write
        sec.documentWriteCount = (content.match(/document\.write/g) || []).length;

        // 4. SQL concatenation (unlikely in HTML but check for completeness)
        sec.sqlConcatCount = 0;

        // 5. Hardcoded secrets in attributes
        const secretPatterns = [
            /password\s*=\s*['"][^'"]+['"]/gi,
            /secret\s*=\s*['"][^'"]+['"]/gi,
            /token\s*=\s*['"][^'"]+['"]/gi,
            /apikey\s*=\s*['"][^'"]+['"]/gi
        ];
        for (const pattern of secretPatterns) {
            const matches = content.match(pattern);
            if (matches) sec.hardcodedSecretCount += matches.length;
        }

        // 6. Missing error boundaries (N/A for HTML)
        sec.missingErrorBoundary = 0;

        // 7. Uncontrolled inputs
        sec.uncontrolledInputCount = 
            (content.match(/<input[^>]*type=['"]text['"]/gi) || []).length;

        // 8. Nested loops (deeply nested tables/divs)
        const tableCount = (content.match(/<table\b/gi) || []).length;
        sec.nestedLoopCount = Math.max(0, tableCount - 1);

        // 9. Blocking I/O (inline scripts)
        sec.blockingIOCount = (content.match(/<script[^>]*>[^<]+<\/script>/gi) || []).length;

        // 10. Unsafe deserialization (N/A for HTML)
        sec.unsafeDeserializationCount = 0;

        // 11. XSS risk
        sec.xssRiskCount = 
            (content.match(/\.innerHTML/g) || []).length +
            (content.match(/dangerouslySetInnerHTML/g) || []).length;

        // 12. Insecure random (N/A for HTML)
        sec.insecureRandomCount = 0;

        // 13. Prototype pollution (N/A for HTML)
        sec.protoPollutionCount = 0;

        // 14. No rate limiting (N/A for HTML)
        sec.noRateLimitCount = 0;

        // 15. Missing auth (N/A for HTML)
        sec.missingAuthCount = 0;

        // 16. Log injection (N/A for HTML)
        sec.logInjectionCount = 0;

        // 17. Path traversal
        sec.pathTraversalCount = 
            (content.match(/\.\.\//g) || []).length;

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
    module.exports = HTMLParser;
}
