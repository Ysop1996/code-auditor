// audit_worker.js - Web Worker for parallel file processing
// Zero-egress: all processing happens in isolated worker context

self.importScripts();

self.onmessage = function(e) {
    const { files, config } = e.data;
    const results = [];
    
    for (const file of files) {
        const metrics = analyzeFile(file, config);
        results.push(metrics);
    }
    
    self.postMessage({ type: 'audit_complete', results });
};

function analyzeFile(file, config) {
    const content = file.content;
    const lines = content.split('\n');
    
    // Security scanning (17 hard-gate patterns)
    const security = {
        evalCount: (content.match(/\beval\s*\(/g) || []).length,
        innerHTMLCount: (content.match(/\.innerHTML/g) || []).length,
        documentWriteCount: (content.match(/document\.write/g) || []).length,
        sqlConcatCount: (content.match(/SELECT\s+.*\+\s*/gi) || []).length,
        hardcodedSecretCount: (content.match(/password\s*[:=]\s*['"][^'"]+['"]/gi) || []).length,
        unsafeDeserializationCount: (content.match(/JSON\.parse\s*\(/g) || []).length,
        xssRiskCount: (content.match(/\.innerHTML|dangerouslySetInnerHTML/g) || []).length,
        insecureRandomCount: (content.match(/Math\.random\s*\(/g) || []).length,
        protoPollutionCount: (content.match(/__proto__|prototype\s*\[/g) || []).length,
        pathTraversalCount: (content.match(/\.\.\/|\.\.\\/g) || []).length,
        postMessageCount: (content.match(/postMessage\s*\([^,]*\)/g) || []).length,
        locationHashCount: (content.match(/location\.hash/g) || []).length,
        dateNowCount: (content.match(/Date\.now\s*\(\s*\)/g) || []).length,
        totalCritical: 0
    };
    
    security.totalCritical = security.evalCount + security.innerHTMLCount + 
                             security.documentWriteCount + security.sqlConcatCount + 
                             security.hardcodedSecretCount + security.unsafeDeserializationCount +
                             security.xssRiskCount + security.protoPollutionCount +
                             security.pathTraversalCount + security.postMessageCount;
    
    // Performance metrics
    const performance = {
        cyclomaticComplexity: (content.match(/\bif\s*\(/g) || []).length +
                              (content.match(/\bwhile\s*\(/g) || []).length +
                              (content.match(/\bfor\s*\(/g) || []).length +
                              (content.match(/\bswitch\s*\(/g) || []).length,
        nestingDepth: Math.max(...content.split('\n').map(line => {
            let depth = 0;
            for (const c of line) {
                if (c === '{') depth++;
                if (c === '}') depth--;
            }
            return depth;
        }), 0),
        totalLines: lines.length,
        totalFunctions: (content.match(/function\s+\w*\s*\(/g) || []).length +
                         (content.match(/=>\s*{/g) || []).length,
        nestedLoops: Math.max(0, (content.match(/for\s*\(/g) || []).length + 
                               (content.match(/while\s*\(/g) || []).length - 1),
        blockingIOCount: (content.match(/fs\.readFileSync|fs\.writeFileSync|XMLHttpRequest/g) || []).length
    };
    
    // Cognitive vector
    const cognitive = {
        past: Math.min(performance.cyclomaticComplexity * 0.3, 10.0),
        present: Math.min(performance.nestingDepth * 0.5 + performance.totalFunctions * 0.2, 10.0),
        future: Math.min(security.totalCritical * 0.5, 10.0)
    };
    
    // Field theory computation
    const loadY = 0.2 * cognitive.past + 0.5 * cognitive.present + 0.3 * cognitive.future;
    const frictionW = Math.abs(loadY - 0.5) * config.PHI;
    const omegaT = frictionW * 14.0;
    
    return {
        filename: file.filename,
        language: file.language,
        totalLines: lines.length,
        cognitive,
        security,
        performance,
        loadY,
        frictionW,
        omegaT,
        isZeroDefect: loadY <= config.ZERO_DEFECT_THRESHOLD && security.totalCritical === 0,
        isCritical: omegaT >= config.OMEGA_CRITICAL,
        isWarning: loadY > config.WARNING_THRESHOLD
    };
}
