// recommendation.js - Data structure for audit recommendations
// Implements deterministic recommendation generation for the Code Auditor

class Recommendation {
    constructor(data) {
        this.id = data.id || '';
        this.category = data.category || '';
        this.priority = data.priority || 'low';
        this.title = data.title || '';
        this.description = data.description || '';
        this.action = data.action || '';
        this.impact = data.impact || '';
        this.estimatedImprovement = data.estimatedImprovement || 0;
        this.relatedFindings = data.relatedFindings || [];
        this.timestamp = data.timestamp || new Date().toISOString();
    }

    // Serialize for deterministic hash computation
    serialize() {
        return JSON.stringify({
            id: this.id,
            category: this.category,
            priority: this.priority,
            title: this.title,
            description: this.description,
            action: this.action,
            impact: this.impact,
            estimatedImprovement: this.estimatedImprovement,
            relatedFindings: this.relatedFindings
        });
    }

    // Get priority score (for sorting)
    getPriorityScore() {
        const scores = { critical: 4, high: 3, medium: 2, low: 1 };
        return scores[this.priority] || 0;
    }

    // Format for display
    toDisplayString() {
        return `[${this.priority.toUpperCase()}] ${this.title}: ${this.description}`;
    }
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = Recommendation;
}
