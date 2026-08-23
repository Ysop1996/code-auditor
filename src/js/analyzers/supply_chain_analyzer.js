// supply_chain_analyzer.js - Dependency vulnerability scanning
// Implements deterministic supply chain analysis for the Code Auditor
// Zero-egress: all CVE data is embedded locally

class SupplyChainAnalyzer {
    constructor() {
        this.PHI = 1.61803398875;
        this.OMEGA_CRITICAL = 5800.0;
        
        // Embedded CVE database (deterministic, zero-egress)
        // In production, this would be a comprehensive local database
        this.CVE_DATABASE = {
            'lodash': [
                { cve: 'CVE-2021-23337', severity: 'high', versions: '<4.17.21', description: 'Prototype pollution' },
                { cve: 'CVE-2020-8203', severity: 'high', versions: '<4.17.20', description: 'Prototype pollution' }
            ],
            'axios': [
                { cve: 'CVE-2021-3749', severity: 'high', versions: '<0.21.1', description: 'SSRF via path traversal' },
                { cve: 'CVE-2023-45855', severity: 'medium', versions: '<1.6.0', description: 'Prototype pollution' }
            ],
            'express': [
                { cve: 'CVE-2024-29001', severity: 'medium', versions: '<4.19.2', description: 'Open redirect' },
                { cve: 'CVE-2024-45591', severity: 'medium', versions: '<4.21.2', description: 'Open redirect' }
            ],
            'react': [
                { cve: 'CVE-2022-0001', severity: 'low', versions: '<18.0.0', description: 'XSS via dev mode' }
            ],
            'moment': [
                { cve: 'CVE-2022-24743', severity: 'low', versions: '<2.29.2', description: 'ReDoS' }
            ],
            'minimist': [
                { cve: 'CVE-2020-7598', severity: 'low', versions: '<1.2.2', description: 'Prototype pollution' },
                { cve: 'CVE-2021-44906', severity: 'low', versions: '<1.2.6', description: 'Prototype pollution' }
            ],
            'qs': [
                { cve: 'CVE-2022-22936', severity: 'medium', versions: '<6.5.3', description: 'Prototype pollution' },
                { cve: 'CVE-2023-22460', severity: 'medium', versions: '<6.11.0', description: 'Prototype pollution' }
            ],
            'serialize-javascript': [
                { cve: 'CVE-2020-7660', severity: 'high', versions: '<2.1.0', description: 'Arbitrary code execution' },
                { cve: 'CVE-2021-32819', severity: 'high', versions: '<3.1.0', description: 'Arbitrary code execution' }
            ],
            'shelljs': [
                { cve: 'CVE-2022-5369', severity: 'high', versions: '<0.8.5', description: 'Arbitrary code execution' }
            ],
            'node-fetch': [
                { cve: 'CVE-2022-0544', severity: 'high', versions: '<2.6.7', description: 'SSRF' },
                { cve: 'CVE-2023-22473', severity: 'medium', versions: '<2.7.0', description: 'SSRF' }
            ],
            'ua-parser-js': [
                { cve: 'CVE-2021-4377', severity: 'high', versions: '<0.7.22', description: 'ReDoS' },
                { cve: 'CVE-2022-31125', severity: 'high', versions: '<0.7.31', description: 'ReDoS' }
            ],
            'tar': [
                { cve: 'CVE-2021-37278', severity: 'high', versions: '<6.1.4', description: 'Arbitrary file write' },
                { cve: 'CVE-2021-37280', severity: 'high', versions: '<6.1.5', description: 'Arbitrary file write' }
            ],
            'glob-parent': [
                { cve: 'CVE-2021-27091', severity: 'medium', versions: '<5.1.2', description: 'ReDoS' },
                { cve: 'CVE-2021-27092', severity: 'medium', versions: '<6.1.1', description: 'ReDoS' }
            ],
            'ws': [
                { cve: 'CVE-2021-32749', severity: 'high', versions: '<7.4.6', description: 'ReDoS' },
                { cve: 'CVE-2021-32754', severity: 'high', versions: '<7.4.7', description: 'ReDoS' }
            ],
            'ini': [
                { cve: 'CVE-2020-7597', severity: 'high', versions: '<1.3.6', description: 'Prototype pollution' },
                { cve: 'CVE-2021-33502', severity: 'high', versions: '<2.0.5', description: 'Prototype pollution' }
            ],
            'hosted-git-info': [
                { cve: 'CVE-2020-7598', severity: 'high', versions: '<2.8.9', description: 'Regular expression denial of service' },
                { cve: 'CVE-2021-23305', severity: 'high', versions: '<2.8.9', description: 'Regular expression denial of service' }
            ]
        };
        
        // Known vulnerable version ranges (simplified semver comparison)
        this.VULNERABLE_PATTERNS = [
            { name: 'lodash', pattern: /lodash["']?\s*:\s*["']?([^"'\s,}]+)/gi },
            { name: 'axios', pattern: /axios["']?\s*:\s*["']?([^"'\s,}]+)/gi },
            { name: 'express', pattern: /express["']?\s*:\s*["']?([^"'\s,}]+)/gi },
            { name: 'react', pattern: /react["']?\s*:\s*["']?([^"'\s,}]+)/gi },
            { name: 'moment', pattern: /moment["']?\s*:\s*["']?([^"'\s,}]+)/gi },
            { name: 'minimist', pattern: /minimist["']?\s*:\s*["']?([^"'\s,}]+)/gi },
            { name: 'qs', pattern: /qs["']?\s*:\s*["']?([^"'\s,}]+)/gi },
            { name: 'serialize-javascript', pattern: /serialize-javascript["']?\s*:\s*["']?([^"'\s,}]+)/gi },
            { name: 'shelljs', pattern: /shelljs["']?\s*:\s*["']?([^"'\s,}]+)/gi },
            { name: 'node-fetch', pattern: /node-fetch["']?\s*:\s*["']?([^"'\s,}]+)/gi },
            { name: 'ua-parser-js', pattern: /ua-parser-js["']?\s*:\s*["']?([^"'\s,}]+)/gi },
            { name: 'tar', pattern: /["']tar["']?\s*:\s*["']?([^"'\s,}]+)/gi },
            { name: 'glob-parent', pattern: /glob-parent["']?\s*:\s*["']?([^"'\s,}]+)/gi },
            { name: 'ws', pattern: /["']ws["']?\s*:\s*["']?([^"'\s,}]+)/gi },
            { name: 'ini', pattern: /["']ini["']?\s*:\s*["']?([^"'\s,}]+)/gi },
            { name: 'hosted-git-info', pattern: /hosted-git-info["']?\s*:\s*["']?([^"'\s,}]+)/gi }
        ];
    }

    // Analyze package.json or similar dependency files
    analyzeDependencies(content, filename) {
        const findings = [];
        const vulnerabilities = [];
        
        for (const { name, pattern } of this.VULNERABLE_PATTERNS) {
            const matches = content.matchAll(pattern);
            for (const match of matches) {
                const version = match[1];
                const vulns = this._checkVersion(name, version);
                
                for (const vuln of vulns) {
                    vulnerabilities.push({
                        package: name,
                        version: version,
                        cve: vuln.cve,
                        severity: vuln.severity,
                        description: vuln.description,
                        recommendation: `Update ${name} to a patched version`
                    });
                    
                    findings.push({
                        type: 'supplyChain',
                        severity: vuln.severity === 'high' ? 'critical' : 
                                 vuln.severity === 'medium' ? 'high' : 'medium',
                        message: `Vulnerable dependency: ${name}@${version} - ${vuln.cve}`,
                        file: filename,
                        line: 0,
                        penalty: vuln.severity === 'high' ? 50.0 : 
                                vuln.severity === 'medium' ? 30.0 : 15.0,
                        recommendation: `Update ${name} to a patched version (${vuln.description})`
                    });
                }
            }
        }
        
        return { vulnerabilities, findings };
    }

    // Check if a specific version is vulnerable
    _checkVersion(packageName, version) {
        const vulns = this.CVE_DATABASE[packageName] || [];
        const results = [];
        
        for (const vuln of vulns) {
            if (this._isVersionVulnerable(version, vuln.versions)) {
                results.push(vuln);
            }
        }
        
        return results;
    }

    // Simplified semver comparison
    _isVersionVulnerable(version, vulnerableRange) {
        // Remove leading ^, ~, >, <, = etc.
        const cleanVersion = version.replace(/[\^~>=<\s]/g, '');
        const cleanRange = vulnerableRange.replace(/[\^~>=<\s]/g, '');
        
        // Simple comparison: if version starts with cleanRange, it's vulnerable
        // This is a simplified check - production would use proper semver
        if (vulnerableRange.startsWith('<')) {
            const threshold = vulnerableRange.substring(1).trim();
            return this._compareVersions(cleanVersion, threshold) < 0;
        }
        
        if (vulnerableRange.startsWith('>=')) {
            const threshold = vulnerableRange.substring(2).trim();
            return this._compareVersions(cleanVersion, threshold) >= 0;
        }
        
        return cleanVersion === cleanRange;
    }

    _compareVersions(v1, v2) {
        const parts1 = v1.split('.').map(Number);
        const parts2 = v2.split('.').map(Number);
        
        for (let i = 0; i < Math.max(parts1.length, parts2.length); i++) {
            const a = parts1[i] || 0;
            const b = parts2[i] || 0;
            if (a !== b) return a - b;
        }
        return 0;
    }

    // Analyze for dependency confusion attacks
    analyzeDependencyConfusion(content) {
        const findings = [];
        
        // Check for internal packages exposed to public registries
        const internalPackagePattern = /["'](@[[\w-]+\/)?[\w-]+["']\s*:\s*["']file:/gi;
        const matches = content.matchAll(internalPackagePattern);
        
        for (const match of matches) {
            findings.push({
                type: 'dependencyConfusion',
                severity: 'high',
                message: `Internal package exposed to public registry: ${match[0]}`,
                file: '',
                line: 0,
                penalty: 45.0,
                recommendation: 'Use private registry for internal packages'
            });
        }
        
        return findings;
    }

    // Analyze for outdated dependencies
    analyzeOutdatedDependencies(content) {
        const findings = [];
        const now = new Date();
        const oneYearAgo = new Date(now.getTime() - 365 * 24 * 60 * 60 * 1000);
        
        // Check for old package versions
        const packagePatterns = [
            { name: 'lodash', maxVersion: '4.17.21' },
            { name: 'axios', maxVersion: '1.6.0' },
            { name: 'express', maxVersion: '4.21.2' }
        ];
        
        for (const { name, maxVersion } of packagePatterns) {
            const pattern = new RegExp(`["']${name}["']\\s*:\\s*["']([^"']+)["']`, 'gi');
            const matches = content.matchAll(pattern);
            
            for (const match of matches) {
                const version = match[1].replace(/[\^~>=<\s]/g, '');
                if (this._compareVersions(version, maxVersion) < 0) {
                    findings.push({
                        type: 'outdatedDependency',
                        severity: 'medium',
                        message: `Outdated dependency: ${name}@${version} (latest: ${maxVersion})`,
                        file: '',
                        line: 0,
                        penalty: 15.0,
                        recommendation: `Update ${name} to ${maxVersion} or later`
                    });
                }
            }
        }
        
        return findings;
    }

    // Full supply chain analysis
    analyze(content, filename) {
        const depVulns = this.analyzeDependencies(content, filename);
        const confusionFindings = this.analyzeDependencyConfusion(content);
        const outdatedFindings = this.analyzeOutdatedDependencies(content);
        
        const allFindings = [...depVulns.findings, ...confusionFindings, ...outdatedFindings];
        const allVulns = [...depVulns.vulnerabilities];
        
        return {
            vulnerabilities: allVulns,
            findings: allFindings,
            totalVulnerabilities: allVulns.length,
            totalFindings: allFindings.length
        };
    }
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = SupplyChainAnalyzer;
}
