// test_monetization.js - Tests for monetization model analyzer
// Verifies deterministic detection of revenue models, platform fees, and optimization opportunities

const assert = require('assert');

const MonetizationAnalyzer = require('../analyzers/monetization_analyzer');

const tests = {
    // === Revenue Model Detection Tests ===
    
    testSubscriptionModel: function() {
        const analyzer = new MonetizationAnalyzer();
        const files = [
            { filename: 'App.js', content: 'const plan = "subscription"; const recurring = true;' }
        ];
        
        const result = analyzer.analyzeMonetization(files);
        assert.strictEqual(result.metrics.revenueModel, 'subscription', 'Should detect subscription model');
        console.log('✓ testSubscriptionModel passed');
    },

    testOneTimePurchase: function() {
        const analyzer = new MonetizationAnalyzer();
        const files = [
            { filename: 'App.js', content: 'const type = "one-time"; const single = "purchase";' }
        ];
        
        const result = analyzer.analyzeMonetization(files);
        assert.strictEqual(result.metrics.revenueModel, 'oneTime', 'Should detect one-time purchase model');
        console.log('✓ testOneTimePurchase passed');
    },

    testPayPerUse: function() {
        const analyzer = new MonetizationAnalyzer();
        const files = [
            { filename: 'App.js', content: 'const credits = 100; const tokens = 50;' }
        ];
        
        const result = analyzer.analyzeMonetization(files);
        assert.strictEqual(result.metrics.revenueModel, 'payPerUse', 'Should detect pay-per-use model');
        console.log('✓ testPayPerUse passed');
    },

    testInAppPurchase: function() {
        const analyzer = new MonetizationAnalyzer();
        const files = [
            { filename: 'App.js', content: 'import { InAppPurchase } from "expo-in-app-purchases";' }
        ];
        
        const result = analyzer.analyzeMonetization(files);
        assert.strictEqual(result.metrics.revenueModel, 'inAppPurchase', 'Should detect in-app purchase model');
        console.log('✓ testInAppPurchase passed');
    },

    testFreemium: function() {
        const analyzer = new MonetizationAnalyzer();
        const files = [
            { filename: 'App.js', content: 'const tier = "freemium"; const isPremium = false;' }
        ];
        
        const result = analyzer.analyzeMonetization(files);
        assert.strictEqual(result.metrics.revenueModel, 'freemium', 'Should detect freemium model');
        console.log('✓ testFreemium passed');
    },

    testUnknownModel: function() {
        const analyzer = new MonetizationAnalyzer();
        const files = [
            { filename: 'App.js', content: 'function App() { return <div>Hello</div>; }' }
        ];
        
        const result = analyzer.analyzeMonetization(files);
        assert.strictEqual(result.metrics.revenueModel, 'unknown', 'Should detect unknown revenue model');
        console.log('✓ testUnknownModel passed');
    },

    // === Payment Integration Tests ===
    
    testStripeIntegration: function() {
        const analyzer = new MonetizationAnalyzer();
        const files = [
            { filename: 'App.js', content: 'import Stripe from "stripe"; const stripe = Stripe("pk_test");' }
        ];
        
        const result = analyzer.analyzeMonetization(files);
        assert.strictEqual(result.metrics.paymentIntegration, 'stripe', 'Should detect Stripe integration');
        console.log('✓ testStripeIntegration passed');
    },

    testPaypalIntegration: function() {
        const analyzer = new MonetizationAnalyzer();
        const files = [
            { filename: 'App.js', content: 'import PayPal from "paypal-checkout";' }
        ];
        
        const result = analyzer.analyzeMonetization(files);
        assert.strictEqual(result.metrics.paymentIntegration, 'paypal', 'Should detect PayPal integration');
        console.log('✓ testPaypalIntegration passed');
    },

    testNativePaymentIntegration: function() {
        const analyzer = new MonetizationAnalyzer();
        const files = [
            { filename: 'App.js', content: 'import { InAppPurchase } from "expo-in-app-purchases";' }
        ];
        
        const result = analyzer.analyzeMonetization(files);
        assert.strictEqual(result.metrics.paymentIntegration, 'native', 'Should detect native payment integration');
        console.log('✓ testNativePaymentIntegration passed');
    },

    testNoPaymentIntegration: function() {
        const analyzer = new MonetizationAnalyzer();
        const files = [
            { filename: 'App.js', content: 'function App() { return <div>Hello</div>; }' }
        ];
        
        const result = analyzer.analyzeMonetization(files);
        assert.strictEqual(result.metrics.paymentIntegration, 'none', 'Should detect no payment integration');
        console.log('✓ testNoPaymentIntegration passed');
    },

    // === Platform Fee Tests ===
    
    testPlatformFeesIAP: function() {
        const analyzer = new MonetizationAnalyzer();
        const files = [
            { filename: 'App.js', content: 'import { InAppPurchase } from "expo-in-app-purchases"; const IAP = true;' }
        ];
        
        const result = analyzer.analyzeMonetization(files);
        assert.ok(result.metrics.platformFees.apple !== undefined, 'Should have Apple platform fee');
        assert.ok(result.metrics.platformFees.google !== undefined, 'Should have Google platform fee');
        assert.strictEqual(result.metrics.platformFees.apple, 0.30, 'Apple IAP fee should be 30%');
        assert.strictEqual(result.metrics.platformFees.google, 0.30, 'Google IAP fee should be 30%');
        console.log('✓ testPlatformFeesIAP passed');
    },

    testPlatformFeesSubscription: function() {
        const analyzer = new MonetizationAnalyzer();
        const files = [
            { filename: 'App.js', content: 'const plan = "subscription"; const recurring = true;' }
        ];
        
        const result = analyzer.analyzeMonetization(files);
        assert.ok(result.metrics.platformFees.apple !== undefined, 'Should have Apple platform fee for subscription');
        assert.strictEqual(result.metrics.platformFees.apple, 0.30, 'Apple subscription fee should be 30%');
        console.log('✓ testPlatformFeesSubscription passed');
    },

    testPlatformFeesOneTime: function() {
        const analyzer = new MonetizationAnalyzer();
        const files = [
            { filename: 'App.js', content: 'const type = "one-time"; const single = "purchase";' }
        ];
        
        const result = analyzer.analyzeMonetization(files);
        assert.ok(result.metrics.platformFees.apple !== undefined, 'Should have Apple platform fee for one-time');
        assert.strictEqual(result.metrics.platformFees.apple, 0.30, 'Apple one-time purchase fee should be 30%');
        console.log('✓ testPlatformFeesOneTime passed');
    },

    // === Optimization Opportunities Tests ===
    
    testHybridPaymentOpportunity: function() {
        const analyzer = new MonetizationAnalyzer();
        const files = [
            { filename: 'App.js', content: 'import { InAppPurchase } from "expo-in-app-purchases"; const IAP = true;' }
        ];
        
        const result = analyzer.analyzeMonetization(files);
        assert.ok(result.metrics.optimizationOpportunities.length > 0, 'Should have optimization opportunities');
        assert.ok(result.metrics.optimizationOpportunities.some(o => o.type === 'hybrid_payment'), 
            'Should suggest hybrid payment model');
        console.log('✓ testHybridPaymentOpportunity passed');
    },

    testHybridPaymentFinding: function() {
        const analyzer = new MonetizationAnalyzer();
        const files = [
            { filename: 'App.js', content: 'import { InAppPurchase } from "expo-in-app-purchases"; const IAP = true;' }
        ];
        
        const result = analyzer.analyzeMonetization(files);
        assert.ok(result.findings.some(f => f.type === 'hybrid_payment_opportunity'), 
            'Should generate hybrid payment opportunity finding');
        assert.strictEqual(result.findings.find(f => f.type === 'hybrid_payment_opportunity').severity, 'medium',
            'Hybrid payment opportunity should be medium severity');
        console.log('✓ testHybridPaymentFinding passed');
    },

    // === Paywall Funnel Tests ===
    
    testFunnelFriction: function() {
        const analyzer = new MonetizationAnalyzer();
        const files = [
            { filename: 'App.js', content: 'signup(); onboarding(); checkout(); confirm();' }
        ];
        
        const result = analyzer.analyzeMonetization(files);
        assert.ok(result.findings.some(f => f.type === 'funnel_friction'), 
            'Should detect funnel friction with too many steps');
        console.log('✓ testFunnelFriction passed');
    },

    testNoFunnelFriction: function() {
        const analyzer = new MonetizationAnalyzer();
        const files = [
            { filename: 'App.js', content: 'login(); checkout(); confirm();' }
        ];
        
        const result = analyzer.analyzeMonetization(files);
        assert.ok(!result.findings.some(f => f.type === 'funnel_friction'), 
            'Should not flag funnel with 3 or fewer steps');
        console.log('✓ testNoFunnelFriction passed');
    },

    testCognitiveBarrier: function() {
        const analyzer = new MonetizationAnalyzer();
        const files = [
            { filename: 'App.js', content: 'password = "test"; confirm password = "test";' }
        ];
        
        const result = analyzer.analyzeMonetization(files);
        assert.ok(result.findings.some(f => f.type === 'cognitive_barrier'), 
            'Should detect cognitive barrier with password confirmation');
        console.log('✓ testCognitiveBarrier passed');
    },

    // === Platform Compliance Tests ===
    
    testPlayMissingBilling: function() {
        const analyzer = new MonetizationAnalyzer();
        const files = [
            { filename: 'App.js', content: 'in_app_purchase = true;' },
            { filename: 'AndroidManifest.xml', content: 'com.android.vending' }
        ];
        
        const result = analyzer.analyzeMonetization(files);
        assert.ok(result.findings.some(f => f.type === 'play_missing_billing'), 
            'Should detect missing Google Play billing for digital goods');
        console.log('✓ testPlayMissingBilling passed');
    },

    testAppleMissingSignIn: function() {
        const analyzer = new MonetizationAnalyzer();
        const files = [
            { filename: 'App.js', content: 'in_app_purchase = true;' },
            { filename: 'Info.plist', content: 'SKPayment; StoreKit' }
        ];
        
        const result = analyzer.analyzeMonetization(files);
        assert.ok(result.findings.some(f => f.type === 'apple_missing_signin'), 
            'Should detect missing Sign in with Apple for IAP');
        console.log('✓ testAppleMissingSignIn passed');
    },

    testAppleSignInPresent: function() {
        const analyzer = new MonetizationAnalyzer();
        const files = [
            { filename: 'Info.plist', content: 'SKPayment; StoreKit; ASAuthorization' }
        ];
        
        const result = analyzer.analyzeMonetization(files);
        assert.ok(!result.findings.some(f => f.type === 'apple_missing_signin'), 
            'Should not flag when Sign in with Apple is present');
        console.log('✓ testAppleSignInPresent passed');
    },

    // === Field Theory Constants ===
    
    testFieldTheoryConstants: function() {
        const analyzer = new MonetizationAnalyzer();
        assert.strictEqual(analyzer.PHI, 1.61803398875, 'PHI should be golden ratio');
        assert.strictEqual(analyzer.OMEGA_CRITICAL, 5800.0, 'OMEGA_CRITICAL should be 5800');
        assert.strictEqual(analyzer.ZERO_DEFECT_THRESHOLD, 1.0, 'ZERO_DEFECT_THRESHOLD should be 1.0');
        console.log('✓ testFieldTheoryConstants passed');
    },

    testPlatformFeeStructure: function() {
        const analyzer = new MonetizationAnalyzer();
        
        // Verify platform fee structure
        assert.strictEqual(analyzer.PLATFORM_FEES.apple.inAppPurchase, 0.30, 'Apple IAP fee should be 30%');
        assert.strictEqual(analyzer.PLATFORM_FEES.apple.subscription, 0.30, 'Apple subscription fee should be 30%');
        assert.strictEqual(analyzer.PLATFORM_FEES.apple.physicalGoods, 0.00, 'Apple physical goods fee should be 0%');
        assert.strictEqual(analyzer.PLATFORM_FEES.apple.b2b, 0.00, 'Apple B2B fee should be 0%');
        
        assert.strictEqual(analyzer.PLATFORM_FEES.google.inAppPurchase, 0.30, 'Google IAP fee should be 30%');
        assert.strictEqual(analyzer.PLATFORM_FEES.google.subscription, 0.30, 'Google subscription fee should be 30%');
        
        assert.strictEqual(analyzer.PLATFORM_FEES.web.stripe, 0.029 + 0.30, 'Stripe fee should be 2.9% + $0.30');
        assert.strictEqual(analyzer.PLATFORM_FEES.web.paypal, 0.029 + 0.30, 'PayPal fee should be 2.9% + $0.30');
        assert.strictEqual(analyzer.PLATFORM_FEES.web.direct, 0.00, 'Direct web fee should be 0%');
        
        console.log('✓ testPlatformFeeStructure passed');
    },

    testRevenueModels: function() {
        const analyzer = new MonetizationAnalyzer();
        
        assert.strictEqual(analyzer.REVENUE_MODELS.subscription, 'Subscription');
        assert.strictEqual(analyzer.REVENUE_MODELS.oneTime, 'One-time purchase');
        assert.strictEqual(analyzer.REVENUE_MODELS.payPerUse, 'Pay-per-use');
        assert.strictEqual(analyzer.REVENUE_MODELS.inAppPurchase, 'In-app purchase');
        assert.strictEqual(analyzer.REVENUE_MODELS.freemium, 'Freemium');
        assert.strictEqual(analyzer.REVENUE_MODELS.advertising, 'Advertising');
        assert.strictEqual(analyzer.REVENUE_MODELS.marketplace, 'Marketplace commission');
        
        console.log('✓ testRevenueModels passed');
    },

    testCleanMonetization: function() {
        const analyzer = new MonetizationAnalyzer();
        const files = [
            { filename: 'App.js', content: 'function App() { return <div>Hello</div>; }' }
        ];
        
        const result = analyzer.analyzeMonetization(files);
        assert.strictEqual(result.metrics.revenueModel, 'unknown', 'Clean app should have unknown revenue model');
        assert.strictEqual(result.metrics.paymentIntegration, 'none', 'Clean app should have no payment integration');
        assert.strictEqual(result.metrics.totalViolations, 0, 'Clean app should have no violations');
        console.log('✓ testCleanMonetization passed');
    },

    runAll: function() {
        console.log('Running Monetization Analyzer Tests...\n');
        
        // Revenue model detection
        this.testSubscriptionModel();
        this.testOneTimePurchase();
        this.testPayPerUse();
        this.testInAppPurchase();
        this.testFreemium();
        this.testUnknownModel();
        
        // Payment integration
        this.testStripeIntegration();
        this.testPaypalIntegration();
        this.testNativePaymentIntegration();
        this.testNoPaymentIntegration();
        
        // Platform fees
        this.testPlatformFeesIAP();
        this.testPlatformFeesSubscription();
        this.testPlatformFeesOneTime();
        
        // Optimization opportunities
        this.testHybridPaymentOpportunity();
        this.testHybridPaymentFinding();
        
        // Paywall funnel
        this.testFunnelFriction();
        this.testNoFunnelFriction();
        this.testCognitiveBarrier();
        
        // Platform compliance
        this.testPlayMissingBilling();
        this.testAppleMissingSignIn();
        this.testAppleSignInPresent();
        
        // Constants and structures
        this.testFieldTheoryConstants();
        this.testPlatformFeeStructure();
        this.testRevenueModels();
        this.testCleanMonetization();
        
        console.log('\n✅ All monetization tests passed!');
    }
};

// Run tests
tests.runAll();
