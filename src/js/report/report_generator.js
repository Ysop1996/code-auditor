// report_generator.js - Audit report generator
// Implements deterministic report generation with MMSI V3.8 field theory integration

const Finding = require('./finding');
const Recommendation = require('./recommendation');

class ReportGenerator {
    constructor() {
        this.PHI = 1.61803398875;
        this.OMEGA_CRITICAL = 5800.0;
        this.ZERO_DEFECT_THRESHOLD = 1.0;
        this.WARNING_THRESHOLD = 0.8;
        this.W_BASE = 0.7557;
        this.DW = 0.6690;
        this.E_PENALTY = 1.201301;
        this.M_MASK = 0.643047;
    }

    // Generate full audit report from file metrics
    generateReport(projectName, fileMetricsList, dependencyAnalysis) {
        const timestamp = new Date().toISOString();
        
        // Aggregate cognitive vectors
        const aggregateCognitive = {
            past: fileMetricsList.reduce((sum, fm) => sum + fm.cognitive.past, 0) / fileMetricsList.length,
            present: fileMetricsList.reduce((sum, fm) => sum + fm.cognitive.present, 0) / fileMetricsList.length,
            future: fileMetricsList.reduce((sum, fm) => sum + fm.cognitive.future, 0) / fileMetricsList.length
        };

        // Compute aggregate field theory metrics
        const aggregateLoadY = this.computeLoadY(aggregateCognitive);
        const aggregateFrictionW = this.computeFrictionW(aggregateLoadY);
        const aggregateOmegaT = this.computeOmegaT(aggregateFrictionW);

        // Find max omega across files
        const maxOmegaT = Math.max(...fileMetricsList.map(fm => fm.omega_t));

        // Total security findings
        const totalSecurityFindings = fileMetricsList.reduce(
            (sum, fm) => sum + fm.security.totalCritical, 0
        );
        const totalCriticalFindings = totalSecurityFindings;

        // Check states
        const isZeroDefect = this.checkZeroDefect(aggregateLoadY, totalCriticalFindings);
        const isCritical = this.checkCritical(maxOmegaT);
        const isWarning = this.checkWarning(aggregateLoadY);

        // Generate certificate hash
        const certificateHash = this.generateCertificateHash({
            projectName,
            timestamp,
            aggregateCognitive,
            aggregateLoadY,
            aggregateFrictionW,
            aggregateOmegaT,
            totalSecurityFindings,
            totalCriticalFindings,
            isZeroDefect,
            fileMetricsList
        });

        // Generate badge
        let badge = 'PROCESSING_MODE';
        if (isZeroDefect) badge = 'ZERO_DEFECT_CERTIFIED';
        else if (isWarning) badge = 'WARNING';

        // Collect all findings
        const findings = this._collectFindings(fileMetricsList);

        // Generate recommendations
        const recommendations = this._generateRecommendations(findings, fileMetricsList, dependencyAnalysis);

        // Build report
        const report = {
            projectName,
            timestamp,
            files: fileMetricsList,
            aggregateCognitive,
            aggregateLoadY,
            aggregateFrictionW,
            aggregateOmegaT,
            maxOmegaT,
            totalSecurityFindings,
            totalCriticalFindings,
            isZeroDefect,
            isCritical,
            isWarning,
            certificateHash,
            zeroDefectBadge: badge,
            findings,
            recommendations,
            dependencyAnalysis: dependencyAnalysis ? {
                cycles: dependencyAnalysis.cycles,
                instability: Array.from(dependencyAnalysis.instability.entries()),
                cohesion: Array.from(dependencyAnalysis.cohesion.entries())
            } : null,
            fieldTheory: {
                phi: this.PHI,
                omegaCritical: this.OMEGA_CRITICAL,
                zeroDefectThreshold: this.ZERO_DEFECT_THRESHOLD,
                warningThreshold: this.WARNING_THRESHOLD,
                wBase: this.W_BASE,
                dw: this.DW,
                ePenalty: this.E_PENALTY,
                mMask: this.M_MASK
            }
        };

        return report;
    }

    // Collect all findings from file metrics
    _collectFindings(fileMetricsList) {
        const findings = [];
        let findingId = 0;

        for (const fm of fileMetricsList) {
            // Security findings
            const sec = fm.security;
            
            if (sec.evalCount > 0) {
                findings.push(new Finding({
                    id: `finding_${findingId++}`,
                    type: 'eval',
                    severity: 'critical',
                    message: `eval() usage detected (${sec.evalCount} occurrences)`,
                    file: fm.filename,
                    language: fm.language,
                    penalty: 50.0 * sec.evalCount,
                    recommendation: 'Replace eval() with Function constructor or structured data parsing'
                }));
            }

            if (sec.innerHTMLCount > 0) {
                findings.push(new Finding({
                    id: `finding_${findingId++}`,
                    type: 'innerHTML',
                    severity: 'high',
                    message: `innerHTML usage detected (${sec.innerHTMLCount} occurrences)`,
                    file: fm.filename,
                    language: fm.language,
                    penalty: 40.0 * sec.innerHTMLCount,
                    recommendation: 'Use textContent or safe DOM manipulation methods'
                }));
            }

            if (sec.documentWriteCount > 0) {
                findings.push(new Finding({
                    id: `finding_${findingId++}`,
                    type: 'documentWrite',
                    severity: 'high',
                    message: `document.write() usage detected (${sec.documentWriteCount} occurrences)`,
                    file: fm.filename,
                    language: fm.language,
                    penalty: 35.0 * sec.documentWriteCount,
                    recommendation: 'Use DOM manipulation methods like appendChild or insertAdjacentHTML'
                }));
            }

            if (sec.sqlConcatCount > 0) {
                findings.push(new Finding({
                    id: `finding_${findingId++}`,
                    type: 'sqlConcat',
                    severity: 'critical',
                    message: `SQL string concatenation detected (${sec.sqlConcatCount} occurrences)`,
                    file: fm.filename,
                    language: fm.language,
                    penalty: 45.0 * sec.sqlConcatCount,
                    recommendation: 'Use parameterized queries or prepared statements'
                }));
            }

            if (sec.hardcodedSecretCount > 0) {
                findings.push(new Finding({
                    id: `finding_${findingId++}`,
                    type: 'hardcodedSecret',
                    severity: 'critical',
                    message: `Hardcoded secrets detected (${sec.hardcodedSecretCount} occurrences)`,
                    file: fm.filename,
                    language: fm.language,
                    penalty: 60.0 * sec.hardcodedSecretCount,
                    recommendation: 'Use environment variables or secure secret management'
                }));
            }

            if (sec.unsafeDeserializationCount > 0) {
                findings.push(new Finding({
                    id: `finding_${findingId++}`,
                    type: 'unsafeDeserialization',
                    severity: 'high',
                    message: `Unsafe deserialization detected (${sec.unsafeDeserializationCount} occurrences)`,
                    file: fm.filename,
                    language: fm.language,
                    penalty: 50.0 * sec.unsafeDeserializationCount,
                    recommendation: 'Validate input before parsing and use safe parsing libraries'
                }));
            }

            if (sec.xssRiskCount > 0) {
                findings.push(new Finding({
                    id: `finding_${findingId++}`,
                    type: 'xssRisk',
                    severity: 'high',
                    message: `XSS risk detected (${sec.xssRiskCount} occurrences)`,
                    file: fm.filename,
                    language: fm.language,
                    penalty: 35.0 * sec.xssRiskCount,
                    recommendation: 'Sanitize HTML content using DOMPurify or similar library'
                }));
            }

            if (sec.protoPollutionCount > 0) {
                findings.push(new Finding({
                    id: `finding_${findingId++}`,
                    type: 'protoPollution',
                    severity: 'high',
                    message: `Prototype pollution risk detected (${sec.protoPollutionCount} occurrences)`,
                    file: fm.filename,
                    language: fm.language,
                    penalty: 40.0 * sec.protoPollutionCount,
                    recommendation: 'Avoid direct prototype manipulation; use Object.freeze or Map'
                }));
            }

            if (sec.pathTraversalCount > 0) {
                findings.push(new Finding({
                    id: `finding_${findingId++}`,
                    type: 'pathTraversal',
                    severity: 'high',
                    message: `Path traversal pattern detected (${sec.pathTraversalCount} occurrences)`,
                    file: fm.filename,
                    language: fm.language,
                    penalty: 45.0 * sec.pathTraversalCount,
                    recommendation: 'Use path.resolve and validate/sanitize file paths'
                }));
            }

            // Performance findings
            if (fm.performance.cyclomaticComplexity > 15) {
                findings.push(new Finding({
                    id: `finding_${findingId++}`,
                    type: 'highComplexity',
                    severity: 'medium',
                    message: `High cyclomatic complexity: ${fm.performance.cyclomaticComplexity}`,
                    file: fm.filename,
                    language: fm.language,
                    penalty: 10.0 * (fm.performance.cyclomaticComplexity - 15),
                    recommendation: 'Refactor to reduce cyclomatic complexity below 15'
                }));
            }

            if (fm.performance.nestingDepth > 5) {
                findings.push(new Finding({
                    id: `finding_${findingId++}`,
                    type: 'deepNesting',
                    severity: 'medium',
                    message: `Deep nesting detected: ${fm.performance.nestingDepth} levels`,
                    file: fm.filename,
                    language: fm.language,
                    penalty: 5.0 * (fm.performance.nestingDepth - 5),
                    recommendation: 'Extract nested logic into separate functions'
                }));
            }

            if (fm.performance.blockingIO > 0) {
                findings.push(new Finding({
                    id: `finding_${findingId++}`,
                    type: 'blockingIO',
                    severity: 'medium',
                    message: `Blocking I/O detected (${fm.performance.blockingIO} occurrences)`,
                    file: fm.filename,
                    language: fm.language,
                    penalty: 30.0 * fm.performance.blockingIO,
                    recommendation: 'Use asynchronous alternatives (promises, async/await)'
                }));
            }
        }

        // Sort by severity (critical first)
        findings.sort((a, b) => b.getSeverityScore() - a.getSeverityScore());

        return findings;
    }

    // Generate recommendations from findings
    _generateRecommendations(findings, fileMetricsList, dependencyAnalysis) {
        const recommendations = [];
        let recId = 0;

        // Group findings by type
        const findingsByType = {};
        for (const finding of findings) {
            if (!findingsByType[finding.type]) {
                findingsByType[finding.type] = [];
            }
            findingsByType[finding.type].push(finding);
        }

        // Generate recommendations for each finding type
        for (const [type, typeFindings] of Object.entries(findingsByType)) {
            const relatedIds = typeFindings.map(f => f.id);
            const totalPenalty = typeFindings.reduce((sum, f) => sum + f.penalty, 0);

            let priority = 'low';
            let title = '';
            let description = '';
            let action = '';
            let impact = '';
            let estimatedImprovement = 0;

            switch (type) {
                case 'eval':
                    priority = 'critical';
                    title = 'Eliminate eval() usage';
                    description = `${typeFindings.length} instances of eval() detected across ${typeFindings.length} files`;
                    action = 'Replace all eval() calls with Function constructor or structured data parsing';
                    impact = 'Eliminates arbitrary code execution vulnerability';
                    estimatedImprovement = totalPenalty;
                    break;
                case 'innerHTML':
                    priority = 'high';
                    title = 'Replace innerHTML with safe DOM methods';
                    description = `${typeFindings.length} instances of innerHTML detected`;
                    action = 'Use textContent or safe DOM manipulation methods';
                    impact = 'Reduces XSS attack surface';
                    estimatedImprovement = totalPenalty;
                    break;
                case 'documentWrite':
                    priority = 'high';
                    title = 'Remove document.write() calls';
                    description = `${typeFindings.length} instances of document.write() detected`;
                    action = 'Use DOM manipulation methods like appendChild or insertAdjacentHTML';
                    impact = 'Prevents document overwrite and improves performance';
                    estimatedImprovement = totalPenalty;
                    break;
                case 'sqlConcat':
                    priority = 'critical';
                    title = 'Use parameterized SQL queries';
                    description = `${typeFindings.length} instances of SQL string concatenation detected`;
                    action = 'Replace string concatenation with parameterized queries or prepared statements';
                    impact = 'Eliminates SQL injection vulnerability';
                    estimatedImprovement = totalPenalty;
                    break;
                case 'hardcodedSecret':
                    priority = 'critical';
                    title = 'Remove hardcoded secrets';
                    description = `${typeFindings.length} hardcoded secrets detected`;
                    action = 'Use environment variables or secure secret management (e.g., HashiCorp Vault)';
                    impact = 'Prevents credential exposure';
                    estimatedImprovement = totalPenalty;
                    break;
                case 'unsafeDeserialization':
                    priority = 'high';
                    title = 'Secure deserialization';
                    description = `${typeFindings.length} unsafe deserialization calls detected`;
                    action = 'Validate input before parsing and use safe parsing libraries';
                    impact = 'Prevents prototype pollution and injection attacks';
                    estimatedImprovement = totalPenalty;
                    break;
                case 'xssRisk':
                    priority = 'high';
                    title = 'Mitigate XSS risks';
                    description = `${typeFindings.length} XSS risk instances detected`;
                    action = 'Sanitize HTML content using DOMPurify or similar library';
                    impact = 'Reduces cross-site scripting attack surface';
                    estimatedImprovement = totalPenalty;
                    break;
                case 'protoPollution':
                    priority = 'high';
                    title = 'Prevent prototype pollution';
                    description = `${typeFindings.length} prototype pollution risks detected`;
                    action = 'Avoid direct prototype manipulation; use Object.freeze or Map';
                    impact = 'Prevents prototype pollution attacks';
                    estimatedImprovement = totalPenalty;
                    break;
                case 'pathTraversal':
                    priority = 'high';
                    title = 'Sanitize file paths';
                    description = `${typeFindings.length} path traversal patterns detected`;
                    action = 'Use path.resolve and validate/sanitize file paths';
                    impact = 'Prevents directory traversal attacks';
                    estimatedImprovement = totalPenalty;
                    break;
                case 'highComplexity':
                    priority = 'medium';
                    title = 'Reduce cyclomatic complexity';
                    description = 'Functions with complexity > 15 detected';
                    action = 'Refactor complex functions into smaller, single-responsibility units';
                    impact = 'Improves maintainability and testability';
                    estimatedImprovement = totalPenalty;
                    break;
                case 'deepNesting':
                    priority = 'medium';
                    title = 'Reduce nesting depth';
                    description = 'Deeply nested code blocks detected';
                    action = 'Extract nested logic into separate functions or use early returns';
                    impact = 'Improves code readability and maintainability';
                    estimatedImprovement = totalPenalty;
                    break;
                case 'blockingIO':
                    priority = 'medium';
                    title = 'Replace blocking I/O';
                    description = `${typeFindings.length} blocking I/O calls detected`;
                    action = 'Use asynchronous alternatives (promises, async/await)';
                    impact = 'Improves application responsiveness';
                    estimatedImprovement = totalPenalty;
                    break;
                default:
                    continue;
            }

            recommendations.push(new Recommendation({
                id: `rec_${recId++}`,
                category: type,
                priority,
                title,
                description,
                action,
                impact,
                estimatedImprovement,
                relatedFindings: relatedIds
            }));
        }

        // Add dependency cycle recommendations
        if (dependencyAnalysis && dependencyAnalysis.cycles.length > 0) {
            recommendations.push(new Recommendation({
                id: `rec_${recId++}`,
                category: 'dependencyCycles',
                priority: 'high',
                title: 'Resolve circular dependencies',
                description: `${dependencyAnalysis.cycles.length} circular dependency cycles detected`,
                action: 'Refactor to break circular dependencies using dependency injection or interfaces',
                impact: 'Improves build reliability and prevents infinite loops',
                estimatedImprovement: dependencyAnalysis.cycles.length * 25.0,
                relatedFindings: []
            }));
        }

        // Sort by priority (critical first)
        recommendations.sort((a, b) => b.getPriorityScore() - a.getPriorityScore());

        return recommendations;
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

    // Generate deterministic certificate hash
    generateCertificateHash(data) {
        const crypto = require('crypto');
        const hashInput = JSON.stringify({
            projectName: data.projectName,
            timestamp: data.timestamp,
            aggregateCognitive: data.aggregateCognitive,
            aggregateLoadY: data.aggregateLoadY,
            aggregateFrictionW: data.aggregateFrictionW,
            aggregateOmegaT: data.aggregateOmegaT,
            totalSecurityFindings: data.totalSecurityFindings,
            totalCriticalFindings: data.totalCriticalFindings,
            isZeroDefect: data.isZeroDefect,
            files: data.fileMetricsList.map(fm => ({
                filename: fm.filename,
                language: fm.language,
                totalLines: fm.totalLines,
                cognitive: fm.cognitive,
                loadY: fm.load_y,
                security: fm.security.totalCritical
            }))
        });
        
        return crypto.createHash('sha256').update(hashInput).digest('hex');
    }

    // Format report for display
    formatReport(report) {
        const lines = [];
        
        lines.push('='.repeat(80));
        lines.push('MMSI V3.8 CODE AUDITOR - AUDIT REPORT');
        lines.push('='.repeat(80));
        lines.push(`Project: ${report.projectName}`);
        lines.push(`Timestamp: ${report.timestamp}`);
        lines.push(`Certificate Hash: ${report.certificateHash}`);
        lines.push('');
        
        lines.push('--- FIELD THEORY METRICS ---');
        lines.push(`Aggregate Cognitive Vector: [${report.aggregateCognitive.past.toFixed(4)}, ${report.aggregateCognitive.present.toFixed(4)}, ${report.aggregateCognitive.future.toFixed(4)}]`);
        lines.push(`Load Y (W(t)): ${report.aggregateLoadY.toFixed(4)}`);
        lines.push(`Friction W: ${report.aggregateFrictionW.toFixed(4)}`);
        lines.push(`Omega T (Ω(t)): ${report.aggregateOmegaT.toFixed(4)}`);
        lines.push(`Max Omega T: ${report.maxOmegaT.toFixed(4)}`);
        lines.push(`Zero-Defect: ${report.isZeroDefect ? 'YES' : 'NO'}`);
        lines.push(`Critical: ${report.isCritical ? 'YES' : 'NO'}`);
        lines.push(`Warning: ${report.isWarning ? 'YES' : 'NO'}`);
        lines.push(`Badge: ${report.zeroDefectBadge}`);
        lines.push('');
        
        lines.push('--- SECURITY FINDINGS ---');
        lines.push(`Total Security Findings: ${report.totalSecurityFindings}`);
        lines.push(`Total Critical Findings: ${report.totalCriticalFindings}`);
        lines.push('');
        
        lines.push('--- FILE METRICS ---');
        for (const fm of report.files) {
            lines.push(`  ${fm.filename} (${fm.language}):`);
            lines.push(`    Lines: ${fm.totalLines}, Functions: ${fm.totalFunctions}`);
            lines.push(`    Cognitive: [${fm.cognitive.past.toFixed(2)}, ${fm.cognitive.present.toFixed(2)}, ${fm.cognitive.future.toFixed(2)}]`);
            lines.push(`    Load Y: ${fm.load_y.toFixed(4)}, Friction W: ${fm.friction_w.toFixed(4)}, Omega T: ${fm.omega_t.toFixed(4)}`);
            lines.push(`    Security Critical: ${fm.security.totalCritical}`);
            lines.push(`    Zero-Defect: ${fm.is_zero_defect ? 'YES' : 'NO'}`);
        }
        lines.push('');
        
        lines.push('--- DETAILED FINDINGS ---');
        for (const finding of report.findings) {
            lines.push(`  [${finding.severity.toUpperCase()}] ${finding.file}:${finding.line} - ${finding.message}`);
            lines.push(`    Recommendation: ${finding.recommendation}`);
        }
        lines.push('');
        
        lines.push('--- RECOMMENDATIONS ---');
        for (const rec of report.recommendations) {
            lines.push(`  [${rec.priority.toUpperCase()}] ${rec.title}`);
            lines.push(`    ${rec.description}`);
            lines.push(`    Action: ${rec.action}`);
            lines.push(`    Impact: ${rec.impact}`);
            lines.push(`    Estimated Improvement: ${rec.estimatedImprovement.toFixed(2)}`);
        }
        lines.push('');
        
        if (report.dependencyAnalysis) {
            lines.push('--- DEPENDENCY ANALYSIS ---');
            lines.push(`Circular Dependencies: ${report.dependencyAnalysis.cycles.length}`);
            for (const cycle of report.dependencyAnalysis.cycles) {
                lines.push(`  Cycle: ${cycle.join(' -> ')}`);
            }
            lines.push('');
        }
        
        lines.push('='.repeat(80));
        lines.push('END OF REPORT');
        lines.push('='.repeat(80));
        
        return lines.join('\n');
    }
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = ReportGenerator;
}
