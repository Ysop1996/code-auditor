// copyright_scanner.js - Copyright & Open-Source license integrity scanner
// Implements deterministic license compliance checking for the Code Auditor

class CopyrightScanner {
    constructor() {
        this.PHI = 1.61803398875;
        this.OMEGA_CRITICAL = 5800.0;
        this.ZERO_DEFECT_THRESHOLD = 1.0;
        this.WARNING_THRESHOLD = 0.8;

        // Copyleft licenses that require source disclosure
        this.COPYLEFT_LICENSES = [
            'GPL-2.0', 'GPL-3.0', 'AGPL-3.0', 'LGPL-2.1', 'LGPL-3.0',
            'GPL-2.0-only', 'GPL-3.0-only', 'AGPL-3.0-only',
            'GPL-2.0-or-later', 'GPL-3.0-or-later', 'AGPL-3.0-or-later',
            'MPL-2.0', 'CDDL-1.0', 'CPL-1.0'
        ];

        // Known vulnerable packages (simplified CVE database)
        this.KNOWN_VULNERABILITIES = {
            'lodash': { versions: ['<4.17.21'], cve: 'CVE-2021-23337' },
            'axios': { versions: ['<0.21.1'], cve: 'CVE-2019-11358' },
            'moment': { versions: ['<2.29.2'], cve: 'CVE-2021-1498' },
            'minimist': { versions: ['<1.2.6'], cve: 'CVE-2020-7598' },
            'lodash.template': { versions: ['<4.17.21'], cve: 'CVE-2021-23337' },
            'node-fetch': { versions: ['<2.6.7'], cve: 'CVE-2022-0081' },
            'ua-parser-js': { versions: ['<0.7.32'], cve: 'CVE-2022-29072' },
            'qs': { versions: ['<6.5.3'], cve: 'CVE-2022-29072' }
        };

        // Unlicensed assets
        this.UNLICENSED_ASSETS = {
            fonts: ['Montserrat', 'Roboto', 'Open Sans', 'Lato', 'Poppins'],
            images: ['Unsplash', 'Pexels', 'Pixabay']
        };
    }

    // Scan for copyright and license issues
    scanCopyright(files) {
        const findings = [];
        const metrics = {
            copyleftLicenses: [],
            vulnerablePackages: [],
            unlicensedAssets: [],
            missingLicenseHeaders: [],
            totalViolations: 0
        };

        for (const file of files) {
            const content = file.content;
            const filename = file.filename;

            // Check for copyleft licenses in package.json
            if (filename === 'package.json') {
                try {
                    const pkg = JSON.parse(content);
                    const allDeps = {
                        ...pkg.dependencies,
                        ...pkg.devDependencies,
                        ...pkg.peerDependencies
                    };

                    for (const [pkgName, version] of Object.entries(allDeps)) {
                        // Check for vulnerable packages
                        if (this.KNOWN_VULNERABILITIES[pkgName]) {
                            const vuln = this.KNOWN_VULNERABILITIES[pkgName];
                            if (this._versionMatches(version, vuln.versions)) {
                                metrics.vulnerablePackages.push({
                                    name: pkgName,
                                    version,
                                    cve: vuln.cve
                                });
                                metrics.totalViolations++;
                                findings.push({
                                    type: 'vulnerable_package',
                                    severity: 'high',
                                    message: `Package ${pkgName}@${version} has known vulnerability ${vuln.cve}`,
                                    file: filename,
                                    penalty: 40.0,
                                    recommendation: `Update ${pkgName} to a secure version`
                                });
                            }
                        }
                    }
                } catch (e) {
                    // Invalid package.json
                }
            }

            // Check for license files
            if (filename.toLowerCase().includes('license') || 
                filename.toLowerCase().includes('copying')) {
                // Check for copyleft license text
                for (const license of this.COPYLEFT_LICENSES) {
                    if (content.toLowerCase().includes(license.toLowerCase()) ||
                        content.toLowerCase().includes('gnu general public')) {
                        metrics.copyleftLicenses.push({ license, file: filename });
                        metrics.totalViolations++;
                        findings.push({
                            type: 'copyleft_license',
                            severity: 'critical',
                            message: `Copyleft license detected: ${license} - may require source disclosure`,
                            file: filename,
                            penalty: 60.0,
                            recommendation: 'Remove or replace copyleft-licensed code with permissive alternatives'
                        });
                    }
                }
            }

            // Check for missing license headers in source files
            if (filename.endsWith('.js') || filename.endsWith('.ts') || 
                filename.endsWith('.java') || filename.endsWith('.kt') ||
                filename.endsWith('.py') || filename.endsWith('.go') ||
                filename.endsWith('.rs') || filename.endsWith('.swift')) {
                
                const hasLicenseHeader = content.includes('License') || 
                                        content.includes('LICENSE') ||
                                        content.includes('Copyright') ||
                                        content.includes('copyright') ||
                                        content.startsWith('/*') && content.includes('license', 0, 200);
                
                if (!hasLicenseHeader && content.length > 100) {
                    metrics.missingLicenseHeaders.push(filename);
                    metrics.totalViolations++;
                    findings.push({
                        type: 'missing_license_header',
                        severity: 'low',
                        message: 'Source file missing license header',
                        file: filename,
                        penalty: 5.0,
                        recommendation: 'Add license header to source file'
                    });
                }
            }

            // Check for unlicensed assets
            if (filename.endsWith('.css') || filename.endsWith('.scss')) {
                for (const font of this.UNLICENSED_ASSETS.fonts) {
                    if (content.includes(font)) {
                        metrics.unlicensedAssets.push({ type: 'font', name: font, file: filename });
                        metrics.totalViolations++;
                        findings.push({
                            type: 'unlicensed_font',
                            severity: 'medium',
                            message: `Potentially unlicensed font usage: ${font}`,
                            file: filename,
                            penalty: 15.0,
                            recommendation: `Verify licensing for ${font} or use a properly licensed alternative`
                        });
                    }
                }
            }

            // Check for unlicensed images
            if (filename.endsWith('.html') || filename.endsWith('.js') || filename.endsWith('.ts')) {
                for (const imageSource of this.UNLICENSED_ASSETS.images) {
                    if (content.includes(imageSource) || content.includes(imageSource.toLowerCase())) {
                        metrics.unlicensedAssets.push({ type: 'image', name: imageSource, file: filename });
                        metrics.totalViolations++;
                        findings.push({
                            type: 'unlicensed_image',
                            severity: 'medium',
                            message: `Potentially unlicensed image source: ${imageSource}`,
                            file: filename,
                            penalty: 10.0,
                            recommendation: `Verify licensing for ${imageSource} images or use CC0 alternatives`
                        });
                    }
                }
            }
        }

        return { metrics, findings };
    }

    _versionMatches(version, vulnerableRanges) {
        for (const range of vulnerableRanges) {
            if (range.startsWith('<')) {
                const targetVersion = range.substring(1);
                if (this._compareVersions(version, targetVersion) < 0) {
                    return true;
                }
            } else if (range.startsWith('>=')) {
                const targetVersion = range.substring(2);
                if (this._compareVersions(version, targetVersion) >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    _compareVersions(v1, v2) {
        const parts1 = v1.replace(/[^0-9.]/g, '').split('.').map(Number);
        const parts2 = v2.replace(/[^0-9.]/g, '').split('.').map(Number);
        
        for (let i = 0; i < Math.max(parts1.length, parts2.length); i++) {
            const a = parts1[i] || 0;
            const b = parts2[i] || 0;
            if (a < b) return -1;
            if (a > b) return 1;
        }
        return 0;
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
    module.exports = CopyrightScanner;
}
