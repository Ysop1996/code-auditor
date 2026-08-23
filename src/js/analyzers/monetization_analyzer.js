// monetization_analyzer.js - Monetarisierungsmodell-Bewertung & Anpassungsvorschläge
// Implements deterministic monetization model analysis for the Code Auditor

class MonetizationAnalyzer {
    constructor() {
        this.PHI = 1.61803398875;
        this.OMEGA_CRITICAL = 5800.0;
        this.ZERO_DEFECT_THRESHOLD = 1.0;
        this.WARNING_THRESHOLD = 0.8;

        // Platform fee structures
        this.PLATFORM_FEES = {
            apple: {
                inAppPurchase: 0.30, // 30% for first year, 15% after
                subscription: 0.30,
                physicalGoods: 0.00, // No fee for physical goods
                b2b: 0.00 // No fee for B2B
            },
            google: {
                inAppPurchase: 0.30, // 30% for first year, 15% after
                subscription: 0.30,
                physicalGoods: 0.00,
                b2b: 0.00
            },
            web: {
                stripe: 0.029 + 0.30, // 2.9% + $0.30 per transaction
                paypal: 0.029 + 0.30,
                direct: 0.00
            }
        };

        // Revenue models
        this.REVENUE_MODELS = {
            subscription: 'Subscription',
            oneTime: 'One-time purchase',
            payPerUse: 'Pay-per-use',
            inAppPurchase: 'In-app purchase',
            freemium: 'Freemium',
            advertising: 'Advertising',
            marketplace: 'Marketplace commission'
        };
    }

    // Analyze monetization model
    analyzeMonetization(files) {
        const findings = [];
        const metrics = {
            revenueModel: 'unknown',
            platformFees: {},
            optimizationOpportunities: [],
            paymentIntegration: 'none',
            totalViolations: 0
        };

        // Detect revenue model from code
        for (const file of files) {
            const content = file.content;
            const filename = file.filename;

            // Subscription model
            if (content.includes('subscription') || content.includes('recurring')) {
                metrics.revenueModel = 'subscription';
            }

            // One-time purchase
            if (content.includes('one-time') || content.includes('single purchase')) {
                metrics.revenueModel = 'oneTime';
            }

            // Pay-per-use
            if (content.includes('pay-per-use') || content.includes('pay per use') || 
                content.includes('credit') || content.includes('token')) {
                metrics.revenueModel = 'payPerUse';
            }

            // In-app purchase
            if (content.includes('in_app_purchase') || content.includes('InAppPurchase') ||
                content.includes('IAP') || content.includes('in-app purchase')) {
                metrics.revenueModel = 'inAppPurchase';
            }

            // Freemium
            if (content.includes('freemium') || content.includes('free tier') ||
                content.includes('premium') || content.includes('pro')) {
                if (metrics.revenueModel === 'unknown') {
                    metrics.revenueModel = 'freemium';
                }
            }

            // Detect payment integration
            if (content.includes('stripe') || content.includes('Stripe')) {
                metrics.paymentIntegration = 'stripe';
            } else if (content.includes('paypal') || content.includes('PayPal')) {
                metrics.paymentIntegration = 'paypal';
            } else if (content.includes('in_app_purchase') || content.includes('InAppPurchase')) {
                metrics.paymentIntegration = 'native';
            }

            // Check for platform-specific compliance
            if (filename.includes('AndroidManifest.xml') || filename.includes('build.gradle')) {
                if (content.includes('com.android.vending') || content.includes('com.google.android.play')) {
                    // Google Play billing detected
                    const hasGoogleBilling = content.includes('BillingClient') || 
                                           content.includes('com.android.billingclient');
                    
                    if (metrics.revenueModel === 'inAppPurchase' && !hasGoogleBilling) {
                        metrics.totalViolations++;
                        findings.push({
                            type: 'play_missing_billing',
                            severity: 'critical',
                            message: 'Digital goods sold without Google Play Billing integration',
                            file: filename,
                            penalty: 50.0,
                            recommendation: 'Integrate Google Play Billing Library for in-app purchases'
                        });
                    }
                }
            }

            if (filename.includes('Info.plist')) {
                if (content.includes('SKPayment') || content.includes('StoreKit')) {
                    // Apple IAP detected
                    if (metrics.revenueModel === 'inAppPurchase') {
                        // Check if Sign in with Apple is also present
                        if (!content.includes('ASAuthorization')) {
                            metrics.totalViolations++;
                            findings.push({
                                type: 'apple_missing_signin',
                                severity: 'critical',
                                message: 'In-app purchases detected without Sign in with Apple',
                                file: filename,
                                penalty: 45.0,
                                recommendation: 'Add Sign in with Apple (Guideline 4.8)'
                            });
                        }
                    }
                }
            }
        }

        // Analyze platform fees and optimization opportunities
        this._analyzePlatformFees(metrics, findings);

        // Check for hybrid payment model opportunities
        this._analyzeHybridPayment(metrics, findings);

        // Check for paywall and funnel optimization
        this._analyzePaywallFunnel(files, metrics, findings);

        return { metrics, findings };
    }

    _analyzePlatformFees(metrics, findings) {
        if (metrics.revenueModel === 'inAppPurchase') {
            metrics.platformFees = {
                apple: this.PLATFORM_FEES.apple.inAppPurchase,
                google: this.PLATFORM_FEES.google.inAppPurchase
            };

            // Check for optimization opportunities
            if (metrics.paymentIntegration === 'native') {
                metrics.optimizationOpportunities.push({
                    type: 'hybrid_payment',
                    description: 'Consider hybrid payment model: native IAP for digital goods, web billing for physical/B2B',
                    potentialSavings: '30% platform fee reduction for eligible transactions'
                });
            }
        } else if (metrics.revenueModel === 'subscription') {
            metrics.platformFees = {
                apple: this.PLATFORM_FEES.apple.subscription,
                google: this.PLATFORM_FEES.google.subscription
            };
        } else if (metrics.revenueModel === 'oneTime') {
            metrics.platformFees = {
                apple: this.PLATFORM_FEES.apple.inAppPurchase,
                google: this.PLATFORM_FEES.google.inAppPurchase
            };
        }
    }

    _analyzeHybridPayment(metrics, findings) {
        // Identify opportunities for web-based billing to avoid platform fees
        if (metrics.revenueModel === 'inAppPurchase' || metrics.revenueModel === 'subscription') {
            findings.push({
                type: 'hybrid_payment_opportunity',
                severity: 'medium',
                message: 'Consider hybrid payment model to reduce platform fees',
                file: 'project root',
                penalty: 0,
                recommendation: 'For physical goods or B2B services, use web-based billing (Stripe/PayPal) instead of platform IAP to avoid 30% fees'
            });

            metrics.optimizationOpportunities.push({
                type: 'fee_optimization',
                description: 'Switch from platform IAP to web billing for eligible goods',
                potentialSavings: 'Up to 30% fee reduction per transaction'
            });
        }
    }

    _analyzePaywallFunnel(files, metrics, findings) {
        let hasAuth = false;
        let hasCheckout = false;
        let hasOnboarding = false;
        let hasConfirmation = false;

        for (const file of files) {
            const content = file.content;
            
            if (content.includes('signup') || content.includes('register') || 
                content.includes('login') || content.includes('auth')) {
                hasAuth = true;
            }
            
            if (content.includes('checkout') || content.includes('payment') ||
                content.includes('buy') || content.includes('purchase')) {
                hasCheckout = true;
            }
            
            if (content.includes('onboarding') || content.includes('tutorial') ||
                content.includes('walkthrough')) {
                hasOnboarding = true;
            }
            
            if (content.includes('confirm') || content.includes('success') ||
                content.includes('thank')) {
                hasConfirmation = true;
            }
        }

        // Check for friction points
        if (hasAuth && hasCheckout) {
            // Check for too many steps
            const steps = [hasAuth, hasOnboarding, hasCheckout, hasConfirmation].filter(Boolean).length;
            if (steps > 3) {
                findings.push({
                    type: 'funnel_friction',
                    severity: 'medium',
                    message: `Checkout funnel has ${steps} steps - potential for high abandonment`,
                    file: 'project root',
                    penalty: 15.0,
                    recommendation: 'Reduce funnel steps to 3 or fewer (auth -> payment -> confirmation)'
                });
            }
        }

        // Check for cognitive barriers
        for (const file of files) {
            const content = file.content;
            if (content.includes('password') && content.includes('confirm password')) {
                findings.push({
                    type: 'cognitive_barrier',
                    severity: 'low',
                    message: 'Password confirmation field increases cognitive load',
                    file: file.filename,
                    penalty: 5.0,
                    recommendation: 'Consider passwordless authentication or single password field with visibility toggle'
                });
            }
        }
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
    module.exports = MonetizationAnalyzer;
}
