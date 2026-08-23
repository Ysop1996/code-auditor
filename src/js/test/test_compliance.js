// test_compliance.js - Tests for compliance scanners (Apple/Play, Legal, Copyright)
// Verifies deterministic detection of platform, legal, and copyright violations

const assert = require('assert');

const ApplePlayComplianceScanner = require('../scanners/apple_play_compliance');
const LegalComplianceScanner = require('../scanners/legal_compliance');
const CopyrightScanner = require('../scanners/copyright_scanner');

const tests = {
    // === Apple Play Compliance Tests ===
    
    testAppleSignInWithAppleMissing: function() {
        const scanner = new ApplePlayComplianceScanner();
        const files = [
            { filename: 'App.js', content: 'import { GoogleAuth } from "react-native-google-auth";' }
        ];
        
        const result = scanner.scanAppleCompliance(files);
        assert.strictEqual(result.metrics.signInWithApple, true, 'Should detect missing Sign in with Apple');
        assert.ok(result.metrics.totalViolations > 0, 'Should have violations');
        assert.ok(result.findings.some(f => f.type === 'apple_auth_missing'), 'Should find apple_auth_missing');
        console.log('✓ testAppleSignInWithAppleMissing passed');
    },

    testAppleSignInWithApplePresent: function() {
        const scanner = new ApplePlayComplianceScanner();
        const files = [
            { filename: 'App.js', content: 'import { AppleAuth } from "react-native-apple-authentication";' }
        ];
        
        const result = scanner.scanAppleCompliance(files);
        assert.strictEqual(result.metrics.signInWithApple, false, 'Should not flag when Apple auth is present');
        console.log('✓ testAppleSignInWithApplePresent passed');
    },

    testAppleExternalPayment: function() {
        const scanner = new ApplePlayComplianceScanner();
        const files = [
            { filename: 'App.js', content: 'import Stripe from "stripe"; const subscription = true;' }
        ];
        
        const result = scanner.scanAppleCompliance(files);
        assert.ok(result.findings.some(f => f.type === 'apple_external_payment'), 'Should detect external payment for digital goods');
        console.log('✓ testAppleExternalPayment passed');
    },

    testAppleWebViewOnly: function() {
        const scanner = new ApplePlayComplianceScanner();
        const files = [
            { filename: 'App.js', content: 'const WebView = require("webview"); WebView.load("https://example.com");' }
        ];
        
        const result = scanner.scanAppleCompliance(files);
        assert.ok(result.findings.some(f => f.type === 'apple_webview_only'), 'Should detect webview-only app');
        console.log('✓ testAppleWebViewOnly passed');
    },

    testAppleMissingAppIcon: function() {
        const scanner = new ApplePlayComplianceScanner();
        const files = [
            { filename: 'Info.plist', content: '<plist><dict></dict></plist>' }
        ];
        
        const result = scanner.scanAppleCompliance(files);
        assert.ok(result.findings.some(f => f.type === 'apple_missing_icon'), 'Should detect missing app icon');
        console.log('✓ testAppleMissingAppIcon passed');
    },

    testAppleMissingLaunchScreen: function() {
        const scanner = new ApplePlayComplianceScanner();
        const files = [
            { filename: 'Info.plist', content: '<plist><dict><key>CFBundleIcons</key></dict></plist>' }
        ];
        
        const result = scanner.scanAppleCompliance(files);
        assert.ok(result.findings.some(f => f.type === 'apple_missing_launch'), 'Should detect missing launch screen');
        console.log('✓ testAppleMissingLaunchScreen passed');
    },

    testAppleCleanCompliance: function() {
        const scanner = new ApplePlayComplianceScanner();
        const files = [
            { filename: 'App.js', content: 'import { AppleAuth } from "react-native-apple-authentication";' },
            { filename: 'Info.plist', content: '<plist><dict><key>CFBundleIcons</key><key>UILaunchStoryboardName</key></dict></plist>' }
        ];
        
        const result = scanner.scanAppleCompliance(files);
        assert.strictEqual(result.metrics.totalViolations, 0, 'Clean app should have no violations');
        console.log('✓ testAppleCleanCompliance passed');
    },

    // === Google Play Compliance Tests ===
    
    testPlayTargetSdkBelow34: function() {
        const scanner = new ApplePlayComplianceScanner();
        const manifest = '<manifest android:targetSdkVersion="33"></manifest>';
        
        const result = scanner.scanPlayCompliance([], manifest);
        assert.ok(result.findings.some(f => f.type === 'play_target_sdk'), 'Should detect target SDK below 34');
        assert.strictEqual(result.metrics.targetSdkLevel, 33, 'Should parse target SDK level');
        console.log('✓ testPlayTargetSdkBelow34 passed');
    },

    testPlayTargetSdkAt34: function() {
        const scanner = new ApplePlayComplianceScanner();
        const manifest = '<manifest android:targetSdkVersion="34"></manifest>';
        
        const result = scanner.scanPlayCompliance([], manifest);
        assert.strictEqual(result.metrics.targetSdkLevel, 34, 'Should parse target SDK level 34');
        assert.ok(!result.findings.some(f => f.type === 'play_target_sdk'), 'Should not flag SDK 34');
        console.log('✓ testPlayTargetSdkAt34 passed');
    },

    testPlayBackgroundPermissions: function() {
        const scanner = new ApplePlayComplianceScanner();
        const manifest = '<manifest><uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION"></uses-permission></manifest>';
        
        const result = scanner.scanPlayCompliance([], manifest);
        assert.ok(result.findings.some(f => f.type === 'play_bg_permission'), 'Should detect background permission');
        console.log('✓ testPlayBackgroundPermissions passed');
    },

    testPlayClosedTesting: function() {
        const scanner = new ApplePlayComplianceScanner();
        const files = [
            { filename: 'build.gradle', content: 'testOptions { unitTests { includeAndroidResources = true } }' }
        ];
        
        const result = scanner.scanPlayCompliance(files, null);
        assert.strictEqual(result.metrics.closedTestingReady, true, 'Should detect testOptions in build.gradle');
        console.log('✓ testPlayClosedTesting passed');
    },

    testPlayMissingClosedTesting: function() {
        const scanner = new ApplePlayComplianceScanner();
        const files = [
            { filename: 'build.gradle', content: 'android { compileSdkVersion 34 }' }
        ];
        
        const result = scanner.scanPlayCompliance(files, null);
        assert.ok(result.findings.some(f => f.type === 'play_closed_testing'), 'Should flag missing closed testing setup');
        console.log('✓ testPlayMissingClosedTesting passed');
    },

    // === Legal Compliance Tests ===
    
    testLegalTrackingWithoutConsent: function() {
        const scanner = new LegalComplianceScanner();
        const files = [
            { filename: 'index.html', content: '<script>gtag("config", "GA_MEASUREMENT_ID");</script>' }
        ];
        
        const result = scanner.scanLegalCompliance(files);
        assert.ok(result.findings.some(f => f.type === 'tracking_without_consent'), 'Should detect tracking without consent');
        console.log('✓ testLegalTrackingWithoutConsent passed');
    },

    testLegalTrackingWithConsent: function() {
        const scanner = new LegalComplianceScanner();
        const files = [
            { filename: 'index.html', content: '<script>gtag("config", "GA_MEASUREMENT_ID");</script><script>consentGranted = true;</script>' }
        ];
        
        const result = scanner.scanLegalCompliance(files);
        assert.ok(!result.findings.some(f => f.type === 'tracking_without_consent'), 'Should not flag tracking with consent');
        console.log('✓ testLegalTrackingWithConsent passed');
    },

    testLegalMissingImprint: function() {
        const scanner = new LegalComplianceScanner();
        const files = [
            { filename: 'App.js', content: 'function App() { return <div>Hello</div>; }' }
        ];
        
        const result = scanner.scanLegalCompliance(files);
        assert.ok(result.findings.some(f => f.type === 'missing_imprint_file'), 'Should detect missing imprint file');
        console.log('✓ testLegalMissingImprint passed');
    },

    testLegalMissingPrivacyPolicy: function() {
        const scanner = new LegalComplianceScanner();
        const files = [
            { filename: 'App.js', content: 'function App() { return <div>Hello</div>; }' }
        ];
        
        const result = scanner.scanLegalCompliance(files);
        assert.ok(result.findings.some(f => f.type === 'missing_privacy_file'), 'Should detect missing privacy policy file');
        console.log('✓ testLegalMissingPrivacyPolicy passed');
    },

    testLegalImprintElements: function() {
        const scanner = new LegalComplianceScanner();
        const files = [
            { filename: 'imprint.html', content: '<h1>Impressum</h1><p>Name: Max Mustermann</p><p>Adresse: Musterstrasse 1</p>' }
        ];
        
        const result = scanner.scanLegalCompliance(files);
        assert.ok(result.findings.some(f => f.type === 'missing_imprint_elements'), 'Should detect missing imprint elements');
        console.log('✓ testLegalImprintElements passed');
    },

    testLegalPrivacyPolicySections: function() {
        const scanner = new LegalComplianceScanner();
        const files = [
            { filename: 'privacy.html', content: '<h1>Datenschutzerklärung</h1><p>Zwecke: Analytics</p>' }
        ];
        
        const result = scanner.scanLegalCompliance(files);
        assert.ok(result.findings.some(f => f.type === 'incomplete_privacy_policy'), 'Should detect incomplete privacy policy');
        console.log('✓ testLegalPrivacyPolicySections passed');
    },

    testLegalAGBFairness: function() {
        const scanner = new LegalComplianceScanner();
        const files = [
            { filename: 'terms.html', content: 'We reserve the right to change these terms at any time without notice.' }
        ];
        
        const result = scanner.scanLegalCompliance(files);
        assert.ok(result.findings.some(f => f.type === 'agb_unfair_term'), 'Should detect unfair AGB terms');
        console.log('✓ testLegalAGBFairness passed');
    },

    testLegalCleanCompliance: function() {
        const scanner = new LegalComplianceScanner();
        const files = [
            { filename: 'imprint.html', content: 'Impressum\nName: Max Mustermann\nAdresse: Musterstrasse 1\nTelefon: 0123456789\nE-Mail: max@example.com\nVertreter: Max Mustermann' },
            { filename: 'privacy.html', content: 'Datenschutzerklärung\nZwecke: Analytics\nRechtsgrundlage: Art. 6\nSpeicherdauer: 30 Tage\nBetroffenenrechte: Auskunft, Löschung' }
        ];
        
        const result = scanner.scanLegalCompliance(files);
        assert.strictEqual(result.metrics.totalViolations, 0, 'Clean legal compliance should have no violations');
        console.log('✓ testLegalCleanCompliance passed');
    },

    // === Copyright Scanner Tests ===
    
    testCopyrightCopyleftLicense: function() {
        const scanner = new CopyrightScanner();
        const files = [
            { filename: 'LICENSE', content: 'GNU GENERAL PUBLIC LICENSE Version 3' }
        ];
        
        const result = scanner.scanCopyright(files);
        assert.ok(result.findings.some(f => f.type === 'copyleft_license'), 'Should detect copyleft license');
        console.log('✓ testCopyrightCopyleftLicense passed');
    },

    testCopyrightVulnerablePackage: function() {
        const scanner = new CopyrightScanner();
        const files = [
            { filename: 'package.json', content: JSON.stringify({ dependencies: { lodash: '4.17.10' } }) }
        ];
        
        const result = scanner.scanCopyright(files);
        assert.ok(result.findings.some(f => f.type === 'vulnerable_package'), 'Should detect vulnerable package');
        console.log('✓ testCopyrightVulnerablePackage passed');
    },

    testCopyrightSafePackage: function() {
        const scanner = new CopyrightScanner();
        const files = [
            { filename: 'package.json', content: JSON.stringify({ dependencies: { lodash: '4.17.21' } }) }
        ];
        
        const result = scanner.scanCopyright(files);
        assert.ok(!result.findings.some(f => f.type === 'vulnerable_package'), 'Should not flag safe package version');
        console.log('✓ testCopyrightSafePackage passed');
    },

    testCopyrightMissingLicenseHeader: function() {
        const scanner = new CopyrightScanner();
        const longContent = 'function ' + 'x'.repeat(200) + '() { return true; }';
        const files = [
            { filename: 'app.js', content: longContent }
        ];
        
        const result = scanner.scanCopyright(files);
        assert.ok(result.findings.some(f => f.type === 'missing_license_header'), 'Should detect missing license header');
        console.log('✓ testCopyrightMissingLicenseHeader passed');
    },

    testCopyrightWithLicenseHeader: function() {
        const scanner = new CopyrightScanner();
        const longContent = '// Copyright 2026 My Company\nfunction ' + 'x'.repeat(200) + '() { return true; }';
        const files = [
            { filename: 'app.js', content: longContent }
        ];
        
        const result = scanner.scanCopyright(files);
        assert.ok(!result.findings.some(f => f.type === 'missing_license_header'), 'Should not flag file with license header');
        console.log('✓ testCopyrightWithLicenseHeader passed');
    },

    testCopyrightUnlicensedFont: function() {
        const scanner = new CopyrightScanner();
        const files = [
            { filename: 'styles.css', content: 'body { font-family: "Montserrat", sans-serif; }' }
        ];
        
        const result = scanner.scanCopyright(files);
        assert.ok(result.findings.some(f => f.type === 'unlicensed_font'), 'Should detect unlicensed font');
        console.log('✓ testCopyrightUnlicensedFont passed');
    },

    testCopyrightUnlicensedImage: function() {
        const scanner = new CopyrightScanner();
        const files = [
            { filename: 'index.html', content: '<img src="https://unsplash.com/photo.jpg" />' }
        ];
        
        const result = scanner.scanCopyright(files);
        assert.ok(result.findings.some(f => f.type === 'unlicensed_image'), 'Should detect unlicensed image source');
        console.log('✓ testCopyrightUnlicensedImage passed');
    },

    testCopyrightClean: function() {
        const scanner = new CopyrightScanner();
        const files = [
            { filename: 'package.json', content: JSON.stringify({ dependencies: { express: '4.18.2' } }) },
            { filename: 'app.js', content: '// Licensed under MIT\nfunction app() { return true; }' }
        ];
        
        const result = scanner.scanCopyright(files);
        assert.strictEqual(result.metrics.totalViolations, 0, 'Clean copyright should have no violations');
        console.log('✓ testCopyrightClean passed');
    },

    testFieldTheoryConstants: function() {
        const scanner = new ApplePlayComplianceScanner();
        assert.strictEqual(scanner.PHI, 1.61803398875, 'PHI should be golden ratio');
        assert.strictEqual(scanner.OMEGA_CRITICAL, 5800.0, 'OMEGA_CRITICAL should be 5800');
        assert.strictEqual(scanner.ZERO_DEFECT_THRESHOLD, 1.0, 'ZERO_DEFECT_THRESHOLD should be 1.0');
        
        const legalScanner = new LegalComplianceScanner();
        assert.strictEqual(legalScanner.PHI, 1.61803398875, 'Legal scanner PHI should be golden ratio');
        
        const copyrightScanner = new CopyrightScanner();
        assert.strictEqual(copyrightScanner.OMEGA_CRITICAL, 5800.0, 'Copyright scanner OMEGA_CRITICAL should be 5800');
        
        console.log('✓ testFieldTheoryConstants passed');
    },

    runAll: function() {
        console.log('Running Compliance Scanner Tests...\n');
        
        // Apple/Play compliance
        this.testAppleSignInWithAppleMissing();
        this.testAppleSignInWithApplePresent();
        this.testAppleExternalPayment();
        this.testAppleWebViewOnly();
        this.testAppleMissingAppIcon();
        this.testAppleMissingLaunchScreen();
        this.testAppleCleanCompliance();
        this.testPlayTargetSdkBelow34();
        this.testPlayTargetSdkAt34();
        this.testPlayBackgroundPermissions();
        this.testPlayClosedTesting();
        this.testPlayMissingClosedTesting();
        
        // Legal compliance
        this.testLegalTrackingWithoutConsent();
        this.testLegalTrackingWithConsent();
        this.testLegalMissingImprint();
        this.testLegalMissingPrivacyPolicy();
        this.testLegalImprintElements();
        this.testLegalPrivacyPolicySections();
        this.testLegalAGBFairness();
        this.testLegalCleanCompliance();
        
        // Copyright scanner
        this.testCopyrightCopyleftLicense();
        this.testCopyrightVulnerablePackage();
        this.testCopyrightSafePackage();
        this.testCopyrightMissingLicenseHeader();
        this.testCopyrightWithLicenseHeader();
        this.testCopyrightUnlicensedFont();
        this.testCopyrightUnlicensedImage();
        this.testCopyrightClean();
        
        // Constants
        this.testFieldTheoryConstants();
        
        console.log('\n✅ All compliance tests passed!');
    }
};

// Run tests
tests.runAll();
