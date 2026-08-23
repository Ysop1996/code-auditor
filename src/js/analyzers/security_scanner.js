// security_scanner.js - Comprehensive security scanner with 17 hard-gate patterns
// Implements deterministic security analysis for the Code Auditor

class SecurityScanner {
    constructor() {
        this.PHI = 1.61803398875;
        this.OMEGA_CRITICAL = 5800.0;
        this.ZERO_DEFECT_THRESHOLD = 1.0;
        this.WARNING_THRESHOLD = 0.8;
        
        // Penalty weights for each security finding type
        this.PENALTIES = {
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
            pathTraversal: 45.0,
            postMessage: 40.0,
            locationHash: 20.0,
            dateNow: 15.0
        };
    }

    // Scan content for all security patterns
    scan(content, language = 'javascript') {
        const findings = [];
        const metrics = this._initSecurityMetrics();

        // 1. eval() usage
        const evalMatches = this._findPattern(content, /\beval\s*\(/g);
        metrics.evalCount = evalMatches.length;
        for (const match of evalMatches) {
            findings.push({
                type: 'eval',
                severity: 'critical',
                message: 'Use of eval() detected - allows arbitrary code execution',
                line: match.line,
                column: match.column,
                penalty: this.PENALTIES.eval,
                recommendation: 'Replace eval() with Function constructor or structured data parsing'
            });
        }

        // 2. innerHTML usage
        const innerHTMLMatches = this._findPattern(content, /\.innerHTML/g);
        metrics.innerHTMLCount = innerHTMLMatches.length;
        for (const match of innerHTMLMatches) {
            findings.push({
                type: 'innerHTML',
                severity: 'high',
                message: 'Use of innerHTML detected - potential XSS vulnerability',
                line: match.line,
                column: match.column,
                penalty: this.PENALTIES.innerHTML,
                recommendation: 'Use textContent or safe DOM manipulation methods'
            });
        }

        // 3. document.write()
        const docWriteMatches = this._findPattern(content, /document\.write/g);
        metrics.documentWriteCount = docWriteMatches.length;
        for (const match of docWriteMatches) {
            findings.push({
                type: 'documentWrite',
                severity: 'high',
                message: 'Use of document.write() detected - can overwrite entire document',
                line: match.line,
                column: match.column,
                penalty: this.PENALTIES.documentWrite,
                recommendation: 'Use DOM manipulation methods like appendChild or insertAdjacentHTML'
            });
        }

        // 4. SQL string concatenation
        const sqlPatterns = [
            { pattern: /SELECT\s+.*\+\s*/gi, message: 'SQL query with string concatenation - SQL injection risk' },
            { pattern: /INSERT\s+INTO\s+.*\+\s*/gi, message: 'SQL INSERT with string concatenation - SQL injection risk' },
            { pattern: /UPDATE\s+.*\+\s*/gi, message: 'SQL UPDATE with string concatenation - SQL injection risk' },
            { pattern: /DELETE\s+FROM\s+.*\+\s*/gi, message: 'SQL DELETE with string concatenation - SQL injection risk' }
        ];
        for (const { pattern, message } of sqlPatterns) {
            const matches = this._findPattern(content, pattern);
            metrics.sqlConcatCount += matches.length;
            for (const match of matches) {
                findings.push({
                    type: 'sqlConcat',
                    severity: 'critical',
                    message,
                    line: match.line,
                    column: match.column,
                    penalty: this.PENALTIES.sqlConcat,
                    recommendation: 'Use parameterized queries or prepared statements'
                });
            }
        }

        // 5. Hardcoded secrets
        const secretPatterns = [
            { pattern: /password\s*[:=]\s*['"][^'"]+['"]/gi, message: 'Hardcoded password detected' },
            { pattern: /secret\s*[:=]\s*['"][^'"]+['"]/gi, message: 'Hardcoded secret detected' },
            { pattern: /api[_-]?key\s*[:=]\s*['"][^'"]+['"]/gi, message: 'Hardcoded API key detected' },
            { pattern: /token\s*[:=]\s*['"][^'"]+['"]/gi, message: 'Hardcoded token detected' },
            { pattern: /private[_-]?key\s*[:=]\s*['"][^'"]+['"]/gi, message: 'Hardcoded private key detected' }
        ];
        for (const { pattern, message } of secretPatterns) {
            const matches = this._findPattern(content, pattern);
            metrics.hardcodedSecretCount += matches.length;
            for (const match of matches) {
                findings.push({
                    type: 'hardcodedSecret',
                    severity: 'critical',
                    message,
                    line: match.line,
                    column: match.column,
                    penalty: this.PENALTIES.hardcodedSecret,
                    recommendation: 'Use environment variables or secure secret management'
                });
            }
        }

        // 6. Missing error boundaries (React)
        if (language === 'javascript' || language === 'typescript') {
            const hasReact = content.includes('react') || content.includes('React');
            const hasErrorBoundary = content.includes('componentDidCatch') || 
                                    content.includes('getDerivedStateFromError');
            if (hasReact && !hasErrorBoundary) {
                metrics.missingErrorBoundary = 1;
                findings.push({
                    type: 'missingErrorBoundary',
                    severity: 'medium',
                    message: 'React application missing error boundary - uncaught errors can crash the app',
                    line: 1,
                    column: 0,
                    penalty: this.PENALTIES.missingErrorBoundary,
                    recommendation: 'Add error boundaries using componentDidCatch or getDerivedStateFromError'
                });
            }
        }

        // 7. Uncontrolled inputs (React)
        const uncontrolledMatches = this._findPattern(content, /defaultValue=/g);
        metrics.uncontrolledInputCount = uncontrolledMatches.length;
        const defaultCheckedMatches = this._findPattern(content, /defaultChecked/g);
        metrics.uncontrolledInputCount += defaultCheckedMatches.length;
        for (const match of [...uncontrolledMatches, ...defaultCheckedMatches]) {
            findings.push({
                type: 'uncontrolledInput',
                severity: 'medium',
                message: 'Uncontrolled input detected - potential state inconsistency',
                line: match.line,
                column: match.column,
                penalty: this.PENALTIES.uncontrolledInput,
                recommendation: 'Use controlled components with value/onChange props'
            });
        }

        // 8. Nested loops
        const forLoops = (content.match(/\bfor\s*\(/g) || []).length;
        const whileLoops = (content.match(/\bwhile\s*\(/g) || []).length;
        const nestedLoopCount = Math.max(0, forLoops + whileLoops - 1);
        metrics.nestedLoopCount = nestedLoopCount;
        if (nestedLoopCount > 0) {
            findings.push({
                type: 'nestedLoop',
                severity: 'medium',
                message: `Nested loops detected (${forLoops} for, ${whileLoops} while) - O(n²) complexity risk`,
                line: 1,
                column: 0,
                penalty: this.PENALTIES.nestedLoop * nestedLoopCount,
                recommendation: 'Consider flattening loops or using more efficient algorithms'
            });
        }

        // 9. Blocking I/O
        const blockingPatterns = [
            { pattern: /fs\.readFileSync/g, message: 'Synchronous file read - blocks event loop' },
            { pattern: /fs\.writeFileSync/g, message: 'Synchronous file write - blocks event loop' },
            { pattern: /fs\.readdirSync/g, message: 'Synchronous directory read - blocks event loop' },
            { pattern: /fs\.statSync/g, message: 'Synchronous stat - blocks event loop' },
            { pattern: /XMLHttpRequest/g, message: 'Synchronous XMLHttpRequest - blocks event loop' },
            { pattern: /\.sync\(/g, message: 'Synchronous operation detected - blocks event loop' }
        ];
        for (const { pattern, message } of blockingPatterns) {
            const matches = this._findPattern(content, pattern);
            metrics.blockingIOCount += matches.length;
            for (const match of matches) {
                findings.push({
                    type: 'blockingIO',
                    severity: 'medium',
                    message,
                    line: match.line,
                    column: match.column,
                    penalty: this.PENALTIES.blockingIO,
                    recommendation: 'Use asynchronous alternatives (promises, async/await)'
                });
            }
        }

        // 10. Unsafe deserialization
        const jsonParseMatches = this._findPattern(content, /\bJSON\.parse\s*\(/g);
        metrics.unsafeDeserializationCount = jsonParseMatches.length;
        for (const match of jsonParseMatches) {
            findings.push({
                type: 'unsafeDeserialization',
                severity: 'high',
                message: 'JSON.parse() detected - potential prototype pollution if parsing untrusted data',
                line: match.line,
                column: match.column,
                penalty: this.PENALTIES.unsafeDeserialization,
                recommendation: 'Validate input before parsing and use safe parsing libraries'
            });
        }

        // 11. XSS risk
        const xssMatches = [...innerHTMLMatches, ...this._findPattern(content, /dangerouslySetInnerHTML/g)];
        metrics.xssRiskCount = xssMatches.length;
        for (const match of xssMatches) {
            findings.push({
                type: 'xssRisk',
                severity: 'high',
                message: 'XSS risk detected - unsanitized HTML content',
                line: match.line,
                column: match.column,
                penalty: this.PENALTIES.xssRisk,
                recommendation: 'Sanitize HTML content using DOMPurify or similar library'
            });
        }

        // 12. Insecure random
        const randomMatches = this._findPattern(content, /\bMath\.random\s*\(/g);
        metrics.insecureRandomCount = randomMatches.length;
        for (const match of randomMatches) {
            findings.push({
                type: 'insecureRandom',
                severity: 'low',
                message: 'Math.random() detected - not cryptographically secure',
                line: match.line,
                column: match.column,
                penalty: this.PENALTIES.insecureRandom,
                recommendation: 'Use crypto.getRandomValues() for security-sensitive operations'
            });
        }

        // 13. Prototype pollution
        const protoMatches = this._findPattern(content, /__proto__/g);
        const prototypeMatches = this._findPattern(content, /prototype\s*\[/g);
        metrics.protoPollutionCount = protoMatches.length + prototypeMatches.length;
        for (const match of [...protoMatches, ...prototypeMatches]) {
            findings.push({
                type: 'protoPollution',
                severity: 'high',
                message: 'Prototype manipulation detected - potential prototype pollution',
                line: match.line,
                column: match.column,
                penalty: this.PENALTIES.protoPollution,
                recommendation: 'Avoid direct prototype manipulation; use Object.freeze or Map'
            });
        }

        // 14. No rate limiting
        const expressRoutes = (content.match(/app\.(get|post|put|delete|patch)\s*\(/gi) || []).length;
        metrics.noRateLimitCount = expressRoutes;
        if (expressRoutes > 0 && !content.includes('rateLimit') && !content.includes('express-rate-limit')) {
            findings.push({
                type: 'noRateLimit',
                severity: 'medium',
                message: `Express application has ${expressRoutes} routes without rate limiting`,
                line: 1,
                column: 0,
                penalty: this.PENALTIES.noRateLimit * expressRoutes,
                recommendation: 'Add express-rate-limit middleware to all routes'
            });
        }

        // 15. Missing auth
        const routeMatches = this._findPattern(content, /app\.(get|post|put|delete|patch)\s*\(/gi);
        const authMatches = this._findPattern(content, /\bauth\b/gi);
        metrics.missingAuthCount = Math.max(0, routeMatches.length - authMatches.length);
        if (metrics.missingAuthCount > 0) {
            findings.push({
                type: 'missingAuth',
                severity: 'high',
                message: `${metrics.missingAuthCount} routes without authentication`,
                line: 1,
                column: 0,
                penalty: this.PENALTIES.missingAuth * metrics.missingAuthCount,
                recommendation: 'Add authentication middleware to all sensitive routes'
            });
        }

        // 16. Log injection
        const logMatches = this._findPattern(content, /console\.(log|error|warn)\s*\(/g);
        metrics.logInjectionCount = logMatches.length;
        for (const match of logMatches) {
            findings.push({
                type: 'logInjection',
                severity: 'low',
                message: 'Console logging detected - potential log injection',
                line: match.line,
                column: match.column,
                penalty: this.PENALTIES.logInjection,
                recommendation: 'Sanitize log inputs and use structured logging'
            });
        }

        // 17. Path traversal
        const pathMatches = [...this._findPattern(content, /\.\.\//g), ...this._findPattern(content, /\.\.\\/g)];
        metrics.pathTraversalCount = pathMatches.length;
        for (const match of pathMatches) {
            findings.push({
                type: 'pathTraversal',
                severity: 'high',
                message: 'Path traversal pattern detected - potential directory traversal attack',
                line: match.line,
                column: match.column,
                penalty: this.PENALTIES.pathTraversal,
                recommendation: 'Use path.resolve and validate/sanitize file paths'
            });
        }

        // 18. Insecure postMessage
        const postMessageMatches = this._findPattern(content, /postMessage\s*\([^,]*\)/g);
        metrics.postMessageCount = postMessageMatches.length;
        for (const match of postMessageMatches) {
            findings.push({
                type: 'insecurePostMessage',
                severity: 'high',
                message: 'postMessage without targetOrigin specified - potential XSS',
                line: match.line,
                column: match.column,
                penalty: 40.0,
                recommendation: 'Always specify targetOrigin as "*" or a specific origin'
            });
        }

        // 19. Location hash manipulation
        const locationHashMatches = this._findPattern(content, /location\.hash/g);
        metrics.locationHashCount = locationHashMatches.length;
        for (const match of locationHashMatches) {
            findings.push({
                type: 'locationHash',
                severity: 'medium',
                message: 'location.hash manipulation - potential XSS via hash fragment',
                line: match.line,
                column: match.column,
                penalty: 20.0,
                recommendation: 'Sanitize and validate hash fragment before use'
            });
        }

        // 20. Timing attack via Date.now()
        const dateNowMatches = this._findPattern(content, /Date\.now\s*\(\s*\)/g);
        metrics.dateNowCount = dateNowMatches.length;
        for (const match of dateNowMatches) {
            findings.push({
                type: 'timingAttack',
                severity: 'low',
                message: 'Date.now() used in security-sensitive context - timing attack risk',
                line: match.line,
                column: match.column,
                penalty: 15.0,
                recommendation: 'Use crypto.timingSafeEqual for security-sensitive comparisons'
            });
        }

        // Calculate total critical findings
        metrics.totalCritical = 
            metrics.evalCount +
            metrics.innerHTMLCount +
            metrics.documentWriteCount +
            metrics.sqlConcatCount +
            metrics.hardcodedSecretCount +
            metrics.unsafeDeserializationCount +
            metrics.xssRiskCount +
            metrics.protoPollutionCount +
            metrics.pathTraversalCount;

        return { metrics, findings };
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
            postMessageCount: 0,
            locationHashCount: 0,
            dateNowCount: 0,
            totalCritical: 0
        };
    }

    // Find pattern with line/column information
    _findPattern(content, pattern) {
        const matches = [];
        const lines = content.split('\n');
        let lineNumber = 1;
        
        for (const line of lines) {
            const lineMatches = line.matchAll(pattern);
            for (const match of lineMatches) {
                matches.push({
                    match: match[0],
                    line: lineNumber,
                    column: match.index || 0
                });
            }
            lineNumber++;
        }
        
        return matches;
    }

    // Compute cognitive vector from security findings
    computeCognitiveVector(metrics) {
        return {
            past: 0,
            present: metrics.totalCritical * 0.3,
            future: metrics.totalCritical * 0.5
        };
    }

    // Compute loadY
    computeLoadY(cv) {
        return 0.2 * cv.past + 0.5 * cv.present + 0.3 * cv.future;
    }

    // Compute frictionW
    computeFrictionW(loadY) {
        return Math.abs(loadY - 0.5) * this.PHI;
    }

    // Compute Ω(t)
    computeOmegaT(frictionW) {
        return frictionW * 14.0;
    }

    // Check Zero-Defect
    checkZeroDefect(loadY, criticalCount) {
        return loadY <= this.ZERO_DEFECT_THRESHOLD && criticalCount === 0;
    }

    // Check critical
    checkCritical(omegaT) {
        return omegaT >= this.OMEGA_CRITICAL;
    }

    // Check warning
    checkWarning(loadY) {
        return loadY > this.WARNING_THRESHOLD;
    }
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = SecurityScanner;
}
