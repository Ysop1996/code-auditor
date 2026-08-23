// apple_play_compliance.js - Apple App Store & Google Play Store compliance scanner
// Implements deterministic compliance checking for the Code Auditor

class ApplePlayComplianceScanner {
    constructor() {
        this.PHI = 1.61803398875;
        this.OMEGA_CRITICAL = 5800.0;
        this.ZERO_DEFECT_THRESHOLD = 1.0;
        this.WARNING_THRESHOLD = 0.8;
    }

    // Scan for Apple App Store compliance issues
    scanAppleCompliance(files) {
        const findings = [];
        const metrics = {
            signInWithApple: false,
            externalPaymentProvider: false,
            webViewOnly: false,
            missingAppIcon: false,
            missingLaunchScreen: false,
            totalViolations: 0
        };

        for (const file of files) {
            const content = file.content;
            const filename = file.filename;

            // Guideline 4.8: Sign in with Apple required
            if (this._detectAuthProviders(content)) {
                const providers = this._getAuthProviders(content);
                if (!providers.includes('apple') && providers.length > 0) {
                    metrics.signInWithApple = true;
                    metrics.totalViolations++;
                    findings.push({
                        type: 'apple_auth_missing',
                        guideline: 'Guideline 4.8',
                        severity: 'critical',
                        message: 'Sign in with Apple not detected - required for apps using third-party auth',
                        file: filename,
                        penalty: 50.0,
                        recommendation: 'Integrate Sign in with Apple using AuthenticationServices framework'
                    });
                }
            }

            // Guideline 3.1.1: External payment providers blocked for digital goods
            if (this._detectPaymentIntegration(content)) {
                const paymentType = this._getPaymentType(content);
                if (paymentType === 'external' && this._isDigitalGoods(content)) {
                    metrics.externalPaymentProvider = true;
                    metrics.totalViolations++;
                    findings.push({
                        type: 'apple_external_payment',
                        guideline: 'Guideline 3.1.1',
                        severity: 'critical',
                        message: 'External payment provider detected for digital goods - App Store IAP required',
                        file: filename,
                        penalty: 60.0,
                        recommendation: 'Use Apple In-App Purchase for digital goods and services'
                    });
                }
            }

            // Guideline 4.2: Pure webviews without app value
            if (this._detectWebViewOnly(content)) {
                metrics.webViewOnly = true;
                metrics.totalViolations++;
                findings.push({
                    type: 'apple_webview_only',
                    guideline: 'Guideline 4.2',
                    severity: 'high',
                    message: 'App appears to be a webview wrapper without native functionality',
                    file: filename,
                    penalty: 40.0,
                    recommendation: 'Add native functionality beyond web content display'
                });
            }

            // Missing app icon
            if (filename.includes('Info.plist') && !content.includes('CFBundleIcons')) {
                metrics.missingAppIcon = true;
                metrics.totalViolations++;
                findings.push({
                    type: 'apple_missing_icon',
                    guideline: 'Guideline 2.2',
                    severity: 'medium',
                    message: 'App icon configuration missing from Info.plist',
                    file: filename,
                    penalty: 15.0,
                    recommendation: 'Add CFBundleIcons with appropriate icon sizes'
                });
            }

            // Missing launch screen
            if (filename.includes('Info.plist') && !content.includes('UILaunchStoryboardName')) {
                metrics.missingLaunchScreen = true;
                metrics.totalViolations++;
                findings.push({
                    type: 'apple_missing_launch',
                    guideline: 'Guideline 2.2',
                    severity: 'medium',
                    message: 'Launch screen storyboard not configured',
                    file: filename,
                    penalty: 10.0,
                    recommendation: 'Add UILaunchStoryboardName to Info.plist'
                });
            }
        }

        return { metrics, findings };
    }

    // Scan for Google Play Store compliance issues
    scanPlayCompliance(files, manifestContent = null) {
        const findings = [];
        const metrics = {
            targetSdkLevel: 0,
            backgroundPermissions: [],
            missingPrivacyPolicy: false,
            closedTestingReady: false,
            totalViolations: 0
        };

        // Check AndroidManifest.xml for target SDK
        if (manifestContent) {
            const sdkMatch = manifestContent.match(/android:targetSdkVersion=["'](\d+)["']/);
            if (sdkMatch) {
                metrics.targetSdkLevel = parseInt(sdkMatch[1]);
                if (metrics.targetSdkLevel < 34) {
                    metrics.totalViolations++;
                    findings.push({
                        type: 'play_target_sdk',
                        severity: 'critical',
                        message: `Target SDK level ${metrics.targetSdkLevel} is below required API Level 34`,
                        file: 'AndroidManifest.xml',
                        penalty: 50.0,
                        recommendation: 'Update targetSdkVersion to 34 or higher'
                    });
                }
            }

            // Check for background permissions
            const bgPermissionPatterns = [
                /android\.permission\.ACCESS_FINE_LOCATION.*usesPermission.*android:required="true"/g,
                /android\.permission\.ACCESS_BACKGROUND_LOCATION/g,
                /android\.permission\.READ_CONTACTS.*android:usesPermission.*background/g
            ];

            for (const pattern of bgPermissionPatterns) {
                const matches = manifestContent.match(pattern);
                if (matches) {
                    metrics.backgroundPermissions.push(...matches);
                    metrics.totalViolations++;
                    findings.push({
                        type: 'play_bg_permission',
                        severity: 'high',
                        message: 'Background permission detected - requires special declaration',
                        file: 'AndroidManifest.xml',
                        penalty: 30.0,
                        recommendation: 'Add uses-permission with android:usesPermissionFlags="neverForApp" or justify background access'
                    });
                }
            }
        }

        // Check for 20-tester requirement
        for (const file of files) {
            if (file.filename.includes('build.gradle')) {
                if (file.content.includes('testOptions')) {
                    metrics.closedTestingReady = true;
                }
            }
        }

        if (!metrics.closedTestingReady && files.length > 0) {
            findings.push({
                type: 'play_closed_testing',
                severity: 'medium',
                message: 'Closed testing track may not meet 20-tester requirement',
                file: 'build.gradle',
                penalty: 15.0,
                recommendation: 'Ensure at least 20 testers are enrolled in closed testing track'
            });
        }

        return { metrics, findings };
    }

    _detectAuthProviders(content) {
        return content.includes('GoogleAuth') || 
               content.includes('FacebookAuth') ||
               content.includes('Auth0') ||
               content.includes('firebase.auth') ||
               content.includes('Signin') ||
               content.includes('login');
    }

    _getAuthProviders(content) {
        const providers = [];
        if (content.includes('apple') || content.includes('AppleAuth') || content.includes('AuthenticationServices')) {
            providers.push('apple');
        }
        if (content.includes('google') || content.includes('GoogleAuth')) {
            providers.push('google');
        }
        if (content.includes('facebook') || content.includes('FacebookAuth')) {
            providers.push('facebook');
        }
        return providers;
    }

    _detectPaymentIntegration(content) {
        return content.includes('stripe') || 
               content.includes('paypal') ||
               content.includes('braintree') ||
               content.includes('IAP') ||
               content.includes('in_app_purchase');
    }

    _getPaymentType(content) {
        if (content.includes('stripe') || content.includes('paypal') || content.includes('braintree')) {
            return 'external';
        }
        if (content.includes('IAP') || content.includes('in_app_purchase') || content.includes('InAppPurchase')) {
            return 'native';
        }
        return 'unknown';
    }

    _isDigitalGoods(content) {
        return content.includes('subscription') ||
               content.includes('premium') ||
               content.includes('unlock') ||
               content.includes('pro') ||
               content.includes('digital');
    }

    _detectWebViewOnly(content) {
        return (content.includes('WebView') || content.includes('webview')) &&
               !content.includes('native') &&
               !content.includes('Swift') &&
               !content.includes('Kotlin') &&
               !content.includes('Java');
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
    module.exports = ApplePlayComplianceScanner;
}
