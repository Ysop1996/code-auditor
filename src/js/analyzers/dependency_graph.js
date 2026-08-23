// dependency_graph.js - Dependency graph analysis with Tarjan's cycle detection
// Implements deterministic dependency analysis for the Code Auditor

class DependencyGraph {
    constructor() {
        this.PHI = 1.61803398875;
        this.OMEGA_CRITICAL = 5800.0;
        this.ZERO_DEFECT_THRESHOLD = 1.0;
        this.WARNING_THRESHOLD = 0.8;
    }

    // Build dependency graph from file list
    buildGraph(files) {
        const graph = new Map();
        const reverseGraph = new Map();
        const fileNames = [];

        // Initialize nodes
        for (const file of files) {
            const name = file.filename;
            fileNames.push(name);
            graph.set(name, []);
            reverseGraph.set(name, []);
        }

        // Extract dependencies from each file
        for (const file of files) {
            const deps = this._extractDependencies(file.content, file.filename);
            for (const dep of deps) {
                if (graph.has(dep)) {
                    graph.get(file.filename).push(dep);
                    reverseGraph.get(dep).push(file.filename);
                }
            }
        }

        return { graph, reverseGraph, fileNames };
    }

    // Extract dependencies from file content
    _extractDependencies(content, filename) {
        const deps = new Set();

        // JavaScript/TypeScript imports
        const importPatterns = [
            /import\s+.*\s+from\s+['"]([^'"]+)['"]/g,
            /import\s+['"]([^'"]+)['"]/g,
            /require\s*\(\s*['"]([^'"]+)['"]\s*\)/g,
            /const\s+\w+\s*=\s*require\s*\(\s*['"]([^'"]+)['"]\s*\)/g,
            /import\s+\*\s+as\s+\w+\s+from\s+['"]([^'"]+)['"]/g
        ];

        for (const pattern of importPatterns) {
            const matches = content.matchAll(pattern);
            for (const match of matches) {
                const dep = match[1];
                // Resolve relative imports
                if (dep.startsWith('.')) {
                    const resolved = this._resolvePath(filename, dep);
                    if (resolved) deps.add(resolved);
                } else {
                    deps.add(dep);
                }
            }
        }

        // CSS @import
        const cssImportMatches = content.match(/@import\s+['"]([^'"]+)['"]/g);
        if (cssImportMatches) {
            for (const match of cssImportMatches) {
                const dep = match.match(/['"]([^'"]+)['"]/)[1];
                deps.add(dep);
            }
        }

        // HTML <link> and <script> tags
        const linkMatches = content.match(/<link[^>]+href\s*=\s*['"]([^'"]+)['"]/gi);
        if (linkMatches) {
            for (const match of linkMatches) {
                const dep = match.match(/href\s*=\s*['"]([^'"]+)['"]/i)[1];
                deps.add(dep);
            }
        }

        const scriptMatches = content.match(/<script[^>]+src\s*=\s*['"]([^'"]+)['"]/gi);
        if (scriptMatches) {
            for (const match of scriptMatches) {
                const dep = match.match(/src\s*=\s*['"]([^'"]+)['"]/i)[1];
                deps.add(dep);
            }
        }

        return Array.from(deps);
    }

    // Resolve relative path
    _resolvePath(fromFile, importPath) {
        const fromDir = fromFile.substring(0, fromFile.lastIndexOf('/'));
        const parts = fromPath.split('/');
        const resolvedParts = fromDir.split('/');
        
        for (const part of parts) {
            if (part === '.') continue;
            if (part === '..') resolvedParts.pop();
            else resolvedParts.push(part);
        }
        
        return resolvedParts.join('/');
    }

    // Tarjan's algorithm for strongly connected components (cycle detection)
    findCycles(graph, fileNames) {
        const indexCounter = { value: 0 };
        const stack = [];
        const lowlinks = new Map();
        const index = new Map();
        const onStack = new Map();
        const sccs = [];

        const strongConnect = (node) => {
            index.set(node, indexCounter.value);
            lowlinks.set(node, indexCounter.value);
            indexCounter.value++;
            stack.push(node);
            onStack.set(node, true);

            const neighbors = graph.get(node) || [];
            for (const neighbor of neighbors) {
                if (!index.has(neighbor)) {
                    strongConnect(neighbor);
                    lowlinks.set(node, Math.min(lowlinks.get(node), lowlinks.get(neighbor)));
                } else if (onStack.get(neighbor)) {
                    lowlinks.set(node, Math.min(lowlinks.get(node), index.get(neighbor)));
                }
            }

            if (lowlinks.get(node) === index.get(node)) {
                const scc = [];
                let w;
                do {
                    w = stack.pop();
                    onStack.set(w, false);
                    scc.push(w);
                } while (w !== node);
                if (scc.length > 1) {
                    sccs.push(scc);
                }
            }
        };

        for (const node of fileNames) {
            if (!index.has(node)) {
                strongConnect(node);
            }
        }

        return sccs;
    }

    // Compute coupling metrics
    computeCoupling(graph, reverseGraph, fileNames) {
        const afferent = new Map(); // Ca: incoming dependencies
        const efferent = new Map(); // Ce: outgoing dependencies

        for (const file of fileNames) {
            afferent.set(file, (reverseGraph.get(file) || []).length);
            efferent.set(file, (graph.get(file) || []).length);
        }

        return { afferent, efferent };
    }

    // Compute cohesion metrics (simplified)
    computeCohesion(files) {
        const cohesion = new Map();
        
        for (const file of files) {
            const content = file.content;
            const lines = content.split('\n');
            
            // Count related functions/classes in same file
            const functions = (content.match(/\bfunction\s+\w+/g) || []).length;
            const classes = (content.match(/\bclass\s+\w+/g) || []).length;
            const relatedEntities = functions + classes;
            
            // Cohesion = related entities / total lines (normalized)
            const score = relatedEntities > 0 ? 
                Math.min(relatedEntities / Math.max(lines.length, 1) * 10, 1.0) : 0;
            
            cohesion.set(file.filename, score);
        }
        
        return cohesion;
    }

    // Analyze full dependency structure
    analyze(files) {
        const { graph, reverseGraph, fileNames } = this.buildGraph(files);
        const cycles = this.findCycles(graph, fileNames);
        const { afferent, efferent } = this.computeCoupling(graph, reverseGraph, fileNames);
        const cohesion = this.computeCohesion(files);

        // Compute instability: I = Ce / (Ca + Ce)
        const instability = new Map();
        for (const file of fileNames) {
            const ca = afferent.get(file) || 0;
            const ce = efferent.get(file) || 0;
            instability.set(file, ca + ce > 0 ? ce / (ca + ce) : 0);
        }

        // Compute abstractness: A = (classes + interfaces) / (total classes + interfaces + dependencies)
        const abstractness = new Map();
        for (const file of files) {
            const classes = (file.content.match(/\bclass\s+\w+/g) || []).length;
            const interfaces = (file.content.match(/\binterface\s+\w+/g) || []).length;
            const deps = (graph.get(file.filename) || []).length;
            abstractness.set(file.filename, classes + interfaces + deps > 0 ? 
                (classes + interfaces) / (classes + interfaces + deps) : 0);
        }

        // Compute cognitive impact of dependency structure
        const cognitive = {
            past: cycles.length * 0.5, // Circular dependencies create past debt
            present: fileNames.length * 0.1, // Number of files affects current processing
            future: cycles.length * 0.8 // Circular dependencies create future risk
        };

        return {
            graph,
            reverseGraph,
            fileNames,
            cycles,
            afferent,
            efferent,
            cohesion,
            instability,
            abstractness,
            cognitive
        };
    }

    // Compute loadY from dependency analysis
    computeLoadY(cv) {
        return 0.2 * cv.past + 0.5 * cv.present + 0.3 * cv.future;
    }

    // Compute frictionW
    computeFrictionW(loadY) {
        return Math.abs(loadY - 0.5) * this.PHI;
    }

    // Compute Ω(t)
    computeOmegaT(frictionW) {
        return frictionW * 14.0;
    }

    // Check Zero-Defect
    checkZeroDefect(loadY, criticalCount) {
        return loadY <= this.ZERO_DEFECT_THRESHOLD && criticalCount === 0;
    }

    // Check critical
    checkCritical(omegaT) {
        return omegaT >= this.OMEGA_CRITICAL;
    }

    // Check warning
    checkWarning(loadY) {
        return loadY > this.WARNING_THRESHOLD;
    }
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = DependencyGraph;
}
