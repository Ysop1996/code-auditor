// finding.js - Data structure for audit findings
// Implements deterministic finding representation for the Code Auditor

class Finding {
    constructor(data) {
        this.id = data.id || '';
        this.type = data.type || '';
        this.severity = data.severity || 'low';
        this.message = data.message || '';
        this.line = data.line || 0;
        this.column = data.column || 0;
        this.file = data.file || '';
        this.language = data.language || '';
        this.penalty = data.penalty || 0;
        this.recommendation = data.recommendation || '';
        this.timestamp = data.timestamp || new Date().toISOString();
    }

    // Serialize for deterministic hash computation
    serialize() {
        return JSON.stringify({
            id: this.id,
            type: this.type,
            severity: this.severity,
            message: this.message,
            line: this.line,
            column: this.column,
            file: this.file,
            language: this.language,
            penalty: this.penalty,
            recommendation: this.recommendation
        });
    }

    // Get severity score (for sorting)
    getSeverityScore() {
        const scores = { critical: 4, high: 3, medium: 2, low: 1 };
        return scores[this.severity] || 0;
    }

    // Get penalty contribution
    getPenalty() {
        return this.penalty;
    }

    // Format for display
    toDisplayString() {
        return `[${this.severity.toUpperCase()}] ${this.file}:${this.line}:${this.column} - ${this.message}`;
    }
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = Finding;
}
