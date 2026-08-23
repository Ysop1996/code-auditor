// runtime_analyzer.js - Dynamic/runtime analysis capabilities
// Implements deterministic runtime behavior analysis for the Code Auditor
// Zero-egress: all analysis happens locally in browser

class RuntimeAnalyzer {
    constructor() {
        this.PHI = 1.61803398875;
        this.OMEGA_CRITICAL = 5800.0;
        this.ZERO_DEFECT_THRESHOLD = 1.0;
        this.WARNING_THRESHOLD = 0.8;
        
        // Runtime behavior patterns
        this.RUNTIME_PATTERNS = {
            memoryLeak: [
                { pattern: /setInterval\s*\(/g, message: 'setInterval without clearInterval - potential memory leak' },
                { pattern: /setTimeout\s*\(/g, message: 'setTimeout without clearTimeout - potential memory leak' },
                { pattern: /addEventListener\s*\(/g, message: 'addEventListener without removeEventListener - potential memory leak' },
                { pattern: /new\s+EventSource/g, message: 'EventSource without close() - potential memory leak' },
                { pattern: /WebSocket\(/g, message: 'WebSocket without close() - potential memory leak' }
            ],
            asyncIssues: [
                { pattern: /Promise\.all\s*\(/g, message: 'Promise.all - potential unhandled rejection' },
                { pattern: /\.then\s*\(\s*\)/g, message: 'Promise chain without catch - unhandled rejection risk' },
                { pattern: /async\s+function/g, message: 'Async function - ensure proper error handling' }
            ],
            raceConditions: [
                { pattern: /Promise\.race\s*\(/g, message: 'Promise.race - potential race condition' },
                { pattern: /await\s+.*\n.*await/g, message: 'Multiple awaits in sequence - potential race condition' }
            ],
            resourceExhaustion: [
                { pattern: /while\s*\(\s*true\s*\)/g, message: 'Infinite loop - potential resource exhaustion' },
                { pattern: /Array\.from\s*\(\s*\{.*length:\s*Infinity/g, message: 'Infinite array creation - potential resource exhaustion' }
            ],
            timingAttacks: [
                { pattern: /===.*password|password.*===/gi, message: 'Direct string comparison of passwords - timing attack risk' },
                { pattern: /==.*token|token.*==/gi, message: 'Direct string comparison of tokens - timing attack risk' }
            ]
        };
    }

    // Analyze runtime behavior patterns
    analyzeRuntime(content, language = 'javascript') {
        const findings = [];
        const metrics = {
            memoryLeakRisk: 0,
            asyncIssues: 0,
            raceConditions: 0,
            resourceExhaustion: 0,
            timingAttackRisk: 0,
            totalRuntimeIssues: 0
        };

        // Memory leak detection
        for (const { pattern, message } of this.RUNTIME_PATTERNS.memoryLeak) {
            const matches = content.match(pattern) || [];
            if (matches.length > 0) {
                metrics.memoryLeakRisk += matches.length;
                findings.push({
                    type: 'memoryLeak',
                    severity: 'medium',
                    message: `${message} (${matches.length} occurrences)`,
                    file: '',
                    line: 0,
                    penalty: 20.0 * matches.length,
                    recommendation: 'Ensure proper cleanup of intervals, timeouts, and event listeners'
                });
            }
        }

        // Async issues detection
        for (const { pattern, message } of this.RUNTIME_PATTERNS.asyncIssues) {
            const matches = content.match(pattern) || [];
            if (matches.length > 0) {
                metrics.asyncIssues += matches.length;
                findings.push({
                    type: 'asyncIssue',
                    severity: 'low',
                    message: `${message} (${matches.length} occurrences)`,
                    file: '',
                    line: 0,
                    penalty: 10.0 * matches.length,
                    recommendation: 'Add proper error handling with .catch() or try/catch'
                });
            }
        }

        // Race condition detection
        for (const { pattern, message } of this.RUNTIME_PATTERNS.raceConditions) {
            const matches = content.match(pattern) || [];
            if (matches.length > 0) {
                metrics.raceConditions += matches.length;
                findings.push({
                    type: 'raceCondition',
                    severity: 'high',
                    message: `${message} (${matches.length} occurrences)`,
                    file: '',
                    line: 0,
                    penalty: 30.0 * matches.length,
                    recommendation: 'Use proper synchronization or state management'
                });
            }
        }

        // Resource exhaustion detection
        for (const { pattern, message } of this.RUNTIME_PATTERNS.resourceExhaustion) {
            const matches = content.match(pattern) || [];
            if (matches.length > 0) {
                metrics.resourceExhaustion += matches.length;
                findings.push({
                    type: 'resourceExhaustion',
                    severity: 'critical',
                    message: `${message} (${matches.length} occurrences)`,
                    file: '',
                    line: 0,
                    penalty: 50.0 * matches.length,
                    recommendation: 'Add proper termination conditions and resource limits'
                });
            }
        }

        // Timing attack detection
        for (const { pattern, message } of this.RUNTIME_PATTERNS.timingAttacks) {
            const matches = content.match(pattern) || [];
            if (matches.length > 0) {
                metrics.timingAttackRisk += matches.length;
                findings.push({
                    type: 'timingAttack',
                    severity: 'high',
                    message: `${message} (${matches.length} occurrences)`,
                    file: '',
                    line: 0,
                    penalty: 40.0 * matches.length,
                    recommendation: 'Use crypto.timingSafeEqual for security-sensitive comparisons'
                });
            }
        }

        metrics.totalRuntimeIssues = metrics.memoryLeakRisk + metrics.asyncIssues + 
                                     metrics.raceConditions + metrics.resourceExhaustion + 
                                     metrics.timingAttackRisk;

        return { metrics, findings };
    }

    // Analyze event loop blocking
    analyzeEventLoopBlocking(content) {
        const findings = [];
        const blockingPatterns = [
            { pattern: /while\s*\(\s*true\s*\)/g, message: 'Infinite loop blocks event loop', penalty: 50.0 },
            { pattern: /for\s*\(\s*let\s+i\s*=\s*0\s*;\s*i\s*<.*;\s*i\+\+\s*\)/g, message: 'Heavy synchronous loop blocks event loop', penalty: 30.0 },
            { pattern: /Array\.from\s*\(\s*\{.*length:\s*\d{6,}/g, message: 'Large array creation blocks event loop', penalty: 25.0 },
            { pattern: /JSON\.parse\s*\(\s*["'].{10000,}["']\s*\)/g, message: 'Large JSON parse blocks event loop', penalty: 35.0 }
        ];

        for (const { pattern, message, penalty } of blockingPatterns) {
            const matches = content.match(pattern) || [];
            if (matches.length > 0) {
                findings.push({
                    type: 'eventLoopBlocking',
                    severity: 'high',
                    message: `${message} (${matches.length} occurrences)`,
                    file: '',
                    line: 0,
                    penalty: penalty * matches.length,
                    recommendation: 'Use setImmediate, process.nextTick, or break work into chunks'
                });
            }
        }

        return findings;
    }

    // Analyze garbage collection pressure
    analyzeGCPressure(content) {
        const findings = [];
        const gcPatterns = [
            { pattern: /new\s+Array\s*\(\s*\d{6,}/g, message: 'Large array allocation causes GC pressure', penalty: 20.0 },
            { pattern: /new\s+String\s*\(/g, message: 'String object creation causes GC pressure', penalty: 10.0 },
            { pattern: /new\s+Number\s*\(/g, message: 'Number object creation causes GC pressure', penalty: 10.0 },
            { pattern: /\[\]\.concat\s*\(/g, message: 'Array concatenation creates new arrays - GC pressure', penalty: 15.0 }
        ];

        for (const { pattern, message, penalty } of gcPatterns) {
            const matches = content.match(pattern) || [];
            if (matches.length > 0) {
                findings.push({
                    type: 'gcPressure',
                    severity: 'low',
                    message: `${message} (${matches.length} occurrences)`,
                    file: '',
                    line: 0,
                    penalty: penalty * matches.length,
                    recommendation: 'Reuse objects and arrays where possible'
                });
            }
        }

        return findings;
    }

    // Full runtime analysis
    analyze(content, language = 'javascript') {
        const runtimeFindings = this.analyzeRuntime(content, language);
        const eventLoopFindings = this.analyzeEventLoopBlocking(content);
        const gcFindings = this.analyzeGCPressure(content);
        
        const allFindings = [...runtimeFindings.findings, ...eventLoopFindings, ...gcFindings];
        const allMetrics = {
            ...runtimeFindings.metrics,
            eventLoopBlocking: eventLoopFindings.length,
            gcPressure: gcFindings.length,
            totalRuntimeIssues: allFindings.length
        };

        return {
            metrics: allMetrics,
            findings: allFindings
        };
    }
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = RuntimeAnalyzer;
}
