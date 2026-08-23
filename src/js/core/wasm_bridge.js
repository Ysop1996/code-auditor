// wasm_bridge.js - Deterministic WASM bridge for field theory computations
// Zero-egress: all computations happen locally in browser WASM module

class WASMBridge {
    constructor() {
        this.module = null;
        this.isReady = false;
        this.PHI = 1.61803398875;
        this.OMEGA_CRITICAL = 5800.0;
        this.ZERO_DEFECT_THRESHOLD = 1.0;
        this.WARNING_THRESHOLD = 0.8;
    }

    async init(wasmModuleFactory) {
        try {
            this.module = await wasmModuleFactory();
            this.isReady = true;
            return true;
        } catch (error) {
            console.warn('WASM engine unavailable, falling back to JS:', error);
            this.isReady = false;
            return false;
        }
    }

    // SHA-256 via WASM (deterministic, zero-egress)
    sha256(input) {
        if (this.isReady && this.module._wasm_sha256) {
            const inputPtr = this.module._malloc(input.length + 1);
            this.module.setValue(inputPtr, input, 'i8');
            const resultPtr = this.module._wasm_sha256(inputPtr);
            const result = this.module.UTF8ToString(resultPtr);
            this.module._wasm_free_string(resultPtr);
            this.module._free(inputPtr);
            return result;
        }
        // Fallback to JS crypto.subtle
        return this._jsSHA256(input);
    }

    // Field theory computations via WASM
    computeLoadY(past, present, future) {
        if (this.isReady && this.module._wasm_compute_load_y) {
            return this.module._wasm_compute_load_y(past, present, future);
        }
        return 0.2 * past + 0.5 * present + 0.3 * future;
    }

    computeFrictionW(loadY) {
        if (this.isReady && this.module._wasm_compute_friction_w) {
            return this.module._wasm_compute_friction_w(loadY);
        }
        return Math.abs(loadY - 0.5) * this.PHI;
    }

    computeOmegaT(frictionW) {
        if (this.isReady && this.module._wasm_compute_omega_t) {
            return this.module._wasm_compute_omega_t(frictionW);
        }
        return frictionW * 14.0;
    }

    // Combined computation (single WASM call for performance)
    computeOmegaFromCognitive(past, present, future) {
        if (this.isReady && this.module._wasm_compute_omega_from_cognitive) {
            return this.module._wasm_compute_omega_from_cognitive(past, present, future);
        }
        const loadY = this.computeLoadY(past, present, future);
        const frictionW = this.computeFrictionW(loadY);
        return this.computeOmegaT(frictionW);
    }

    checkZeroDefect(loadY, criticalCount) {
        if (this.isReady && this.module._wasm_check_zero_defect) {
            return this.module._wasm_check_zero_defect(loadY, criticalCount) === 1;
        }
        return loadY <= this.ZERO_DEFECT_THRESHOLD && criticalCount === 0;
    }

    checkCritical(omegaT) {
        if (this.isReady && this.module._wasm_check_critical) {
            return this.module._wasm_check_critical(omegaT) === 1;
        }
        return omegaT >= this.OMEGA_CRITICAL;
    }

    checkWarning(loadY) {
        if (this.isReady && this.module._wasm_check_warning) {
            return this.module._wasm_check_warning(loadY) === 1;
        }
        return loadY > this.WARNING_THRESHOLD;
    }

    // File security scan via WASM (returns critical count)
    evaluateFileSecurity(content, language) {
        if (this.isReady && this.module._wasm_evaluate_file_security) {
            const contentPtr = this.module._malloc(content.length + 1);
            const langPtr = this.module._malloc(language.length + 1);
            this.module.setValue(contentPtr, content, 'i8');
            this.module.setValue(langPtr, language, 'i8');
            const result = this.module._wasm_evaluate_file_security(contentPtr, langPtr);
            this.module._free(contentPtr);
            this.module._free(langPtr);
            return result;
        }
        return null; // Signal fallback needed
    }

    // Fallback JS SHA-256 (for when WASM unavailable)
    async _jsSHA256(input) {
        const encoder = new TextEncoder();
        const data = encoder.encode(input);
        const hashBuffer = await crypto.subtle.digest('SHA-256', data);
        const hashArray = Array.from(new Uint8Array(hashBuffer));
        return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
    }
}

// Singleton instance
const wasmBridge = new WASMBridge();

if (typeof module !== 'undefined' && module.exports) {
    module.exports = { WASMBridge, wasmBridge };
}
