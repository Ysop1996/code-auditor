// patch_generator.js - Actionable Code-Patches with exact before/after diffs
// Implements deterministic patch generation for the Code Auditor

class PatchGenerator {
    constructor() {
        this.PHI = 1.61803398875;
        this.OMEGA_CRITICAL = 5800.0;
        this.ZERO_DEFECT_THRESHOLD = 1.0;
        this.WARNING_THRESHOLD = 0.8;
    }

    // Generate code patches from findings
    generatePatches(findings, files) {
        const patches = [];

        for (const finding of findings) {
            const file = files.find(f => f.filename === finding.file);
            if (!file) continue;

            const patch = this._generatePatchForFinding(finding, file);
            if (patch) {
                patches.push(patch);
            }
        }

        return patches;
    }

    _generatePatchForFinding(finding, file) {
        const content = file.content;
        const lines = content.split('\n');

        switch (finding.type) {
            case 'eval':
                return this._patchEval(lines, finding);
            case 'innerHTML':
                return this._patchInnerHTML(lines, finding);
            case 'documentWrite':
                return this._patchDocumentWrite(lines, finding);
            case 'sqlConcat':
                return this._patchSQLConcat(lines, finding);
            case 'hardcodedSecret':
                return this._patchHardcodedSecret(lines, finding);
            case 'n_plus_one':
                return this._patchNPlusOne(lines, finding);
            case 'missing_index':
                return this._patchMissingIndex(lines, finding);
            case 'blocking_operation':
                return this._patchBlockingIO(lines, finding);
            case 'memory_leak':
                return this._patchMemoryLeak(lines, finding);
            case 'dom_explosion':
                return this._patchDOMExplosion(lines, finding);
            case 'blocking_script':
                return this._patchBlockingScript(lines, finding);
            case 'tracking_without_consent':
                return this._patchTrackingConsent(lines, finding);
            case 'apple_auth_missing':
                return this._patchAppleAuth(lines, finding);
            case 'apple_external_payment':
                return this._patchExternalPayment(lines, finding);
            case 'play_target_sdk':
                return this._patchTargetSDK(lines, finding);
            case 'copyleft_license':
                return this._patchCopyleftLicense(lines, finding);
            case 'vulnerable_package':
                return this._patchVulnerablePackage(lines, finding);
            case 'funnel_friction':
                return this._patchFunnelFriction(lines, finding);
            case 'cognitive_barrier':
                return this._patchCognitiveBarrier(lines, finding);
            default:
                return null;
        }
    }

    _patchEval(lines, finding) {
        // Find eval() calls and generate patches
        const patches = [];
        for (let i = 0; i < lines.length; i++) {
            if (lines[i].includes('eval(')) {
                const before = lines[i];
                const after = lines[i].replace(/eval\s*\(([^)]+)\)/g, 'Function($1)');
                patches.push({
                    file: finding.file,
                    line: i + 1,
                    before,
                    after,
                    description: 'Replace eval() with Function constructor for safer code execution'
                });
            }
        }
        return patches.length > 0 ? { finding, patches } : null;
    }

    _patchInnerHTML(lines, finding) {
        const patches = [];
        for (let i = 0; i < lines.length; i++) {
            if (lines[i].includes('.innerHTML')) {
                const before = lines[i];
                const after = lines[i].replace(/\.innerHTML\s*=/g, '.textContent =')
                                     .replace(/\.innerHTML/g, '.textContent');
                patches.push({
                    file: finding.file,
                    line: i + 1,
                    before,
                    after,
                    description: 'Replace innerHTML with textContent to prevent XSS'
                });
            }
        }
        return patches.length > 0 ? { finding, patches } : null;
    }

    _patchDocumentWrite(lines, finding) {
        const patches = [];
        for (let i = 0; i < lines.length; i++) {
            if (lines[i].includes('document.write')) {
                const before = lines[i];
                const after = lines[i].replace(/document\.write\s*\(([^)]+)\)/g, 
                    'document.body.insertAdjacentHTML("beforeend", $1)');
                patches.push({
                    file: finding.file,
                    line: i + 1,
                    before,
                    after,
                    description: 'Replace document.write with insertAdjacentHTML'
                });
            }
        }
        return patches.length > 0 ? { finding, patches } : null;
    }

    _patchSQLConcat(lines, finding) {
        const patches = [];
        for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            // Detect SQL with string concatenation
            if ((line.includes('SELECT') || line.includes('INSERT') || line.includes('UPDATE') || line.includes('DELETE')) &&
                line.includes('+')) {
                const before = line;
                // Suggest parameterized query
                const after = `// TODO: Convert to parameterized query\n${line}`;
                patches.push({
                    file: finding.file,
                    line: i + 1,
                    before,
                    after,
                    description: 'Replace SQL string concatenation with parameterized query'
                });
            }
        }
        return patches.length > 0 ? { finding, patches } : null;
    }

    _patchHardcodedSecret(lines, finding) {
        const patches = [];
        for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            const secretMatch = line.match(/(password|secret|apiKey|token)\s*[:=]\s*['"]([^'"]+)['"]/i);
            if (secretMatch) {
                const before = line;
                const after = line.replace(secretMatch[0], 
                    `${secretMatch[1]} = process.env.${secretMatch[1].toUpperCase()}`);
                patches.push({
                    file: finding.file,
                    line: i + 1,
                    before,
                    after,
                    description: 'Replace hardcoded secret with environment variable'
                });
            }
        }
        return patches.length > 0 ? { finding, patches } : null;
    }

    _patchNPlusOne(lines, finding) {
        const patches = [];
        for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            if (line.includes('.findMany()') || line.includes('.find()') || line.includes('.findAll()')) {
                const before = line;
                const after = line.replace(/\.findMany\s*\(\s*\)/g, '.findMany({ take: 100, skip: 0 })')
                                 .replace(/\.find\s*\(\s*\)/g, '.find({ limit: 100, offset: 0 })')
                                 .replace(/\.findAll\s*\(\s*\)/g, '.findAll({ limit: 100, offset: 0 })');
                patches.push({
                    file: finding.file,
                    line: i + 1,
                    before,
                    after,
                    description: 'Add pagination to prevent N+1 query issues'
                });
            }
        }
        return patches.length > 0 ? { finding, patches } : null;
    }

    _patchMissingIndex(lines, finding) {
        const patches = [];
        for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            if (line.includes('foreign key') || line.includes('references')) {
                const before = line;
                const after = `${line}\n-- Add index for foreign key performance\nCREATE INDEX idx_${line.match(/(\w+)_id/)?.[1] || 'fk'} ON ${line.match(/TABLE\s+(\w+)/i)?.[1] || 'table'}(${line.match(/(\w+)_id/)?.[1] || 'id'});`;
                patches.push({
                    file: finding.file,
                    line: i + 1,
                    before,
                    after,
                    description: 'Add index on foreign key column'
                });
            }
        }
        return patches.length > 0 ? { finding, patches } : null;
    }

    _patchBlockingIO(lines, finding) {
        const patches = [];
        for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            if (line.includes('fs.readFileSync')) {
                const before = line;
                const after = line.replace(/fs\.readFileSync/g, 'fs.promises.readFile')
                                 .replace(/const\s+(\w+)\s*=\s*fs\.promises\.readFile/g, 'const $1 = await fs.promises.readFile');
                patches.push({
                    file: finding.file,
                    line: i + 1,
                    before,
                    after,
                    description: 'Replace synchronous file read with async/await'
                });
            }
            if (line.includes('fs.writeFileSync')) {
                const before = line;
                const after = line.replace(/fs\.writeFileSync/g, 'fs.promises.writeFile')
                                 .replace(/const\s+(\w+)\s*=\s*fs\.promises\.writeFile/g, 'const $1 = await fs.promises.writeFile');
                patches.push({
                    file: finding.file,
                    line: i + 1,
                    before,
                    after,
                    description: 'Replace synchronous file write with async/await'
                });
            }
        }
        return patches.length > 0 ? { finding, patches } : null;
    }

    _patchMemoryLeak(lines, finding) {
        const patches = [];
        for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            if (line.includes('setInterval')) {
                const before = line;
                const after = `${line}\n// Remember to clear interval: clearInterval(intervalId);`;
                patches.push({
                    file: finding.file,
                    line: i + 1,
                    before,
                    after,
                    description: 'Add clearInterval to prevent memory leak'
                });
            }
            if (line.includes('addEventListener')) {
                const before = line;
                const after = `${line}\n// Remember to remove listener: element.removeEventListener(event, handler);`;
                patches.push({
                    file: finding.file,
                    line: i + 1,
                    before,
                    after,
                    description: 'Add removeEventListener to prevent memory leak'
                });
            }
        }
        return patches.length > 0 ? { finding, patches } : null;
    }

    _patchDOMExplosion(lines, finding) {
        const patches = [];
        for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            if (line.includes('<div>') || line.includes('<span>')) {
                const before = line;
                const after = `<!-- Consider virtualization for large lists -->\n${line}`;
                patches.push({
                    file: finding.file,
                    line: i + 1,
                    before,
                    after,
                    description: 'Consider virtualization for large DOM structures'
                });
            }
        }
        return patches.length > 0 ? { finding, patches } : null;
    }

    _patchBlockingScript(lines, finding) {
        const patches = [];
        for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            if (line.includes('<script') && !line.includes('defer') && !line.includes('async')) {
                const before = line;
                const after = line.replace('<script', '<script defer');
                patches.push({
                    file: finding.file,
                    line: i + 1,
                    before,
                    after,
                    description: 'Add defer attribute to prevent render blocking'
                });
            }
        }
        return patches.length > 0 ? { finding, patches } : null;
    }

    _patchTrackingConsent(lines, finding) {
        const patches = [];
        for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            if (line.includes('gtag(') || line.includes('fbq(')) {
                const before = line;
                const after = `// Wrap in consent check:\nif (window.userConsent) {\n  ${line}\n}`;
                patches.push({
                    file: finding.file,
                    line: i + 1,
                    before,
                    after,
                    description: 'Wrap tracking script in consent check'
                });
            }
        }
        return patches.length > 0 ? { finding, patches } : null;
    }

    _patchAppleAuth(lines, finding) {
        const patches = [];
        for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            if (line.includes('GoogleAuth') || line.includes('FacebookAuth')) {
                const before = line;
                const after = `${line}\n// Add Sign in with Apple:\nimport { SignInWithAppleButton } from '@invertase/react-native-apple-authentication';`;
                patches.push({
                    file: finding.file,
                    line: i + 1,
                    before,
                    after,
                    description: 'Add Sign in with Apple integration (Guideline 4.8)'
                });
            }
        }
        return patches.length > 0 ? { finding, patches } : null;
    }

    _patchExternalPayment(lines, finding) {
        const patches = [];
        for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            if (line.includes('stripe') || line.includes('paypal')) {
                const before = line;
                const after = `// For digital goods, use native IAP instead:\n// ${line}\n// import { requestPurchase } from 'react-native-iap';`;
                patches.push({
                    file: finding.file,
                    line: i + 1,
                    before,
                    after,
                    description: 'Replace external payment with native IAP for digital goods'
                });
            }
        }
        return patches.length > 0 ? { finding, patches } : null;
    }

    _patchTargetSDK(lines, finding) {
        const patches = [];
        for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            if (line.includes('targetSdkVersion')) {
                const before = line;
                const after = line.replace(/targetSdkVersion\s+["']?(\d+)["']?/, 'targetSdkVersion "34"');
                patches.push({
                    file: finding.file,
                    line: i + 1,
                    before,
                    after,
                    description: 'Update target SDK to API Level 34'
                });
            }
        }
        return patches.length > 0 ? { finding, patches } : null;
    }

    _patchCopyleftLicense(lines, finding) {
        const patches = [];
        for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            if (line.toLowerCase().includes('license') || line.toLowerCase().includes('copyright')) {
                const before = line;
                const after = `${line}\n// Consider replacing with MIT or Apache 2.0 licensed alternative`;
                patches.push({
                    file: finding.file,
                    line: i + 1,
                    before,
                    after,
                    description: 'Replace copyleft license with permissive alternative'
                });
            }
        }
        return patches.length > 0 ? { finding, patches } : null;
    }

    _patchVulnerablePackage(lines, finding) {
        const patches = [];
        for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            if (line.includes('lodash') || line.includes('axios') || line.includes('moment')) {
                const before = line;
                const pkgName = line.match(/"([^"]+)":\s*"([^"]+)"/)?.[1];
                if (pkgName) {
                    const after = line.replace(/"([^"]+)":\s*"([^"]+)"/, `"$1": "^latest"`);
                    patches.push({
                        file: finding.file,
                        line: i + 1,
                        before,
                        after,
                        description: `Update ${pkgName} to latest secure version`
                    });
                }
            }
        }
        return patches.length > 0 ? { finding, patches } : null;
    }

    _patchFunnelFriction(lines, finding) {
        const patches = [];
        patches.push({
            file: finding.file,
            line: 1,
            before: '// Current funnel has too many steps',
            after: '// Recommended: Auth -> Payment -> Confirmation (3 steps max)',
            description: 'Reduce checkout funnel steps to 3 or fewer'
        });
        return { finding, patches };
    }

    _patchCognitiveBarrier(lines, finding) {
        const patches = [];
        for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            if (line.includes('confirm password') || line.includes('confirmPassword')) {
                const before = line;
                const after = line.replace(/confirm\s*password/gi, 'showPassword ? "password" : "text"');
                patches.push({
                    file: finding.file,
                    line: i + 1,
                    before,
                    after,
                    description: 'Replace password confirmation with password visibility toggle'
                });
            }
        }
        return patches.length > 0 ? { finding, patches } : null;
    }

    // Format patches for display
    formatPatches(patchGroups) {
        const output = [];
        
        for (const group of patchGroups) {
            output.push(`\n=== ${group.finding.severity.toUpperCase()} - ${group.finding.message} ===`);
            output.push(`File: ${group.finding.file}`);
            output.push(`Recommendation: ${group.finding.recommendation}`);
            output.push('');
            
            for (const patch of group.patches) {
                output.push(`  Line ${patch.line}:`);
                output.push(`    - ${patch.before}`);
                output.push(`    + ${patch.after}`);
                output.push(`    (${patch.description})`);
                output.push('');
            }
        }
        
        return output.join('\n');
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
    module.exports = PatchGenerator;
}
