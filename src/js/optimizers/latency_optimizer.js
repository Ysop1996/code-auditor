// latency_optimizer.js - Latenz- & Datenbank-Audit
// Implements deterministic performance optimization analysis for the Code Auditor

class LatencyOptimizer {
    constructor() {
        this.PHI = 1.61803398875;
        this.OMEGA_CRITICAL = 5800.0;
        this.ZERO_DEFECT_THRESHOLD = 1.0;
        this.WARNING_THRESHOLD = 0.8;
    }

    // Analyze latency and database issues
    analyzeLatency(files) {
        const findings = [];
        const metrics = {
            nPlusOneQueries: [],
            missingIndexes: [],
            blockingOperations: [],
            memoryLeaks: [],
            totalViolations: 0
        };

        for (const file of files) {
            const content = file.content;
            const filename = file.filename;

            // N+1 query detection
            const nPlusOnePatterns = [
                { pattern: /\.findMany\s*\(\s*\)/g, message: 'findMany() without limit/take - potential N+1 query' },
                { pattern: /\.find\s*\(\s*\)/g, message: 'find() without limit - potential N+1 query' },
                { pattern: /\.findAll\s*\(\s*\)/g, message: 'findAll() without limit - potential N+1 query' },
                { pattern: /\.findMany\s*\(\s*\)/g, message: 'findMany() without limit/take - potential N+1 query' },
                { pattern: /for\s*\(.*\)\s*{[^}]*\.find/g, message: 'Database query inside loop - N+1 pattern' }
            ];

            for (const { pattern, message } of nPlusOnePatterns) {
                const matches = content.match(pattern);
                if (matches) {
                    metrics.nPlusOneQueries.push({ file: filename, count: matches.length, message });
                    metrics.totalViolations++;
                    findings.push({
                        type: 'n_plus_one',
                        severity: 'high',
                        message: `${message} (${matches.length} occurrences)`,
                        file: filename,
                        penalty: 35.0 * matches.length,
                        recommendation: 'Add pagination with limit/take or use batch queries'
                    });
                }
            }

            // Missing indexes on foreign keys
            const fkPatterns = [
                /foreign\s+key\s*\([^)]+\)/gi,
                /references\s*\([^)]+\)/gi
            ];

            for (const pattern of fkPatterns) {
                const matches = content.match(pattern);
                if (matches) {
                    // Check if index is defined nearby
                    const hasIndex = content.includes('index') || content.includes('INDEX');
                    if (!hasIndex) {
                        metrics.missingIndexes.push({ file: filename, count: matches.length });
                        metrics.totalViolations++;
                        findings.push({
                            type: 'missing_index',
                            severity: 'medium',
                            message: `Foreign key defined without index (${matches.length} occurrences)`,
                            file: filename,
                            penalty: 20.0 * matches.length,
                            recommendation: 'Add index on foreign key columns for query performance'
                        });
                    }
                }
            }

            // Blocking operations
            const blockingPatterns = [
                { pattern: /fs\.readFileSync/g, message: 'Synchronous file read' },
                { pattern: /fs\.writeFileSync/g, message: 'Synchronous file write' },
                { pattern: /XMLHttpRequest.*false/g, message: 'Synchronous XMLHttpRequest' },
                { pattern: /while\s*\([^)]*\)\s*\{\s*\}/g, message: 'Busy wait loop' }
            ];

            for (const { pattern, message } of blockingPatterns) {
                const matches = content.match(pattern);
                if (matches) {
                    metrics.blockingOperations.push({ file: filename, count: matches.length, message });
                    metrics.totalViolations++;
                    findings.push({
                        type: 'blocking_operation',
                        severity: 'medium',
                        message: `${message} (${matches.length} occurrences)`,
                        file: filename,
                        penalty: 25.0 * matches.length,
                        recommendation: 'Replace with asynchronous alternatives'
                    });
                }
            }

            // Memory leak detection
            const leakPatterns = [
                { pattern: /setInterval\s*\(/g, message: 'setInterval without clearInterval' },
                { pattern: /setTimeout\s*\(/g, message: 'setTimeout without clearTimeout' },
                { pattern: /addEventListener\s*\(/g, message: 'addEventListener without removeEventListener' }
            ];

            for (const { pattern, message } of leakPatterns) {
                const addMatches = content.match(pattern) || [];
                const removePattern = pattern.source.replace('add', 'remove').replace('set', 'clear');
                const removeMatches = content.match(new RegExp(removePattern, 'g')) || [];
                
                if (addMatches.length > removeMatches.length) {
                    metrics.memoryLeaks.push({ 
                        file: filename, 
                        count: addMatches.length - removeMatches.length, 
                        message 
                    });
                    metrics.totalViolations++;
                    findings.push({
                        type: 'memory_leak',
                        severity: 'medium',
                        message: `${message} - ${addMatches.length - removeMatches.length} unclosed references`,
                        file: filename,
                        penalty: 20.0 * (addMatches.length - removeMatches.length),
                        recommendation: 'Ensure all intervals, timeouts, and event listeners are properly cleaned up'
                    });
                }
            }
        }

        return { metrics, findings };
    }

    // Analyze frontend performance
    analyzeFrontend(files) {
        const findings = [];
        const metrics = {
            domNodes: 0,
            blockingScripts: [],
            renderBlocking: [],
            totalViolations: 0
        };

        for (const file of files) {
            const content = file.content;
            const filename = file.filename;

            // DOM node count
            if (filename.endsWith('.html')) {
                const domNodes = (content.match(/<[a-zA-Z][a-zA-Z0-9]*\b/g) || []).length;
                metrics.domNodes += domNodes;

                if (domNodes > 1200) {
                    metrics.totalViolations++;
                    findings.push({
                        type: 'dom_explosion',
                        severity: 'high',
                        message: `DOM node count: ${domNodes} (limit: 1200 for 60FPS)`,
                        file: filename,
                        penalty: 30.0,
                        recommendation: 'Reduce DOM nodes through virtualization or pagination'
                    });
                }

                // Blocking scripts in head
                const headMatch = content.match(/<head[^>]*>([\s\S]*?)<\/head>/i);
                if (headMatch) {
                    const headContent = headMatch[1];
                    const blockingScripts = headContent.match(/<script[^>]*>(?!.*defer)(?!.*async)/gi);
                    if (blockingScripts && blockingScripts.length > 0) {
                        metrics.blockingScripts.push({ file: filename, count: blockingScripts.length });
                        metrics.totalViolations++;
                        findings.push({
                            type: 'blocking_script',
                            severity: 'medium',
                            message: `Blocking scripts in head (${blockingScripts.length} found)`,
                            file: filename,
                            penalty: 15.0 * blockingScripts.length,
                            recommendation: 'Add defer or async attributes to scripts in head'
                        });
                    }
                }
            }

            // CSS optimization
            if (filename.endsWith('.css') || filename.endsWith('.scss')) {
                // Check for excessive selectors
                const selectors = (content.match(/[,{]+/g) || []).length;
                if (selectors > 100) {
                    findings.push({
                        type: 'css_complexity',
                        severity: 'low',
                        message: `High CSS selector count: ${selectors}`,
                        file: filename,
                        penalty: 5.0,
                        recommendation: 'Simplify CSS selectors and use CSS modules or styled-components'
                    });
                }
            }
        }

        return { metrics, findings };
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
    module.exports = LatencyOptimizer;
}
