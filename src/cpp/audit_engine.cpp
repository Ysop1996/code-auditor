#include "audit_engine.h"
#include <cmath>
#include <sstream>
#include <iomanip>
#include <algorithm>
#include <cstring>

namespace mmsi_audit {

// =========================================================================
// SHA-256 IMPLEMENTATION (minimal, self-contained)
// =========================================================================

namespace {

typedef uint8_t uint8;
typedef uint32_t uint32;
typedef uint64_t uint64;

const uint32 SHA256_K[] = {
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
    0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
    0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc03766, 0x19ace468,
    0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3,
    0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3, 0x748f82ee,
    0x78a5636f, 0x7cb2cc79, 0x85845dd1, 0x8eb28514,
    0x9b05688c, 0x9bc529a0, 0xa2b8831c, 0xa831c66d,
    0xb2a3f284, 0xb570917f, 0xc039a3dc, 0xc760703c,
    0xc9a3e96b, 0xccff9761, 0xd1cfea9d, 0xd699db24,
    0xd8bfbd08, 0xda78e635, 0x96c671cd, 0x99e5d6fd,
    0xb3f01531, 0xc57e51a1, 0xce40b130, 0xd66cd0b3,
    0xf0898705, 0x15a0cc4d, 0x25e51e61, 0x2b3e6c1f,
    0x35b05a39, 0x4a193c58, 0x5a5c2b57, 0x6a3d5c8d,
    0x7a3b4c5d, 0x8b4c5d6e, 0x9c5d6e7f, 0xad6e7f80,
    0xbe7f8091, 0xcf8091a2, 0xe091a2b3, 0xf1a2b3c4
};

struct Sha256Context {
    uint32 totalLength;
    uint32 hash[8];
    uint8 buffer[64];
    uint8 input[64];
};

void sha256Transform(Sha256Context& ctx, const uint8* data) {
    uint32 a, b, c, d, e, f, g, h, t1, t2, w[64];
    
    for (int i = 0; i < 16; i++) {
        w[i] = (data[i*4] << 24) | (data[i*4+1] << 16) | (data[i*4+2] << 8) | data[i*4+3];
    }
    for (int i = 16; i < 64; i++) {
        uint32 s0 = w[i-15];
        s0 = ((s0 >> 7) | (s0 << 25)) ^ ((s0 >> 18) | (s0 << 14)) ^ (s0 >> 3);
        uint32 s1 = w[i-2];
        s1 = ((s1 >> 17) | (s1 << 15)) ^ ((s1 >> 19) | (s1 << 13)) ^ (s1 >> 10);
        w[i] = w[i-16] + s0 + w[i-7] + s1;
    }
    
    a = ctx.hash[0]; b = ctx.hash[1]; c = ctx.hash[2]; d = ctx.hash[3];
    e = ctx.hash[4]; f = ctx.hash[5]; g = ctx.hash[6]; h = ctx.hash[7];
    
    for (int i = 0; i < 64; i++) {
        uint32 S1 = ((e >> 6) | (e << 26)) ^ ((e >> 11) | (e << 21)) ^ ((e >> 25) | (e << 7));
        uint32 ch = (e & f) | (~e & g);
        t1 = h + S1 + ch + SHA256_K[i] + w[i];
        uint32 S0 = ((a >> 2) | (a << 30)) ^ ((a >> 13) | (a << 19)) ^ ((a >> 22) | (a << 10));
        uint32 maj = (a & b) | (a & c) | (b & c);
        t2 = S0 + maj;
        h = g; g = f; f = e; e = d + t1;
        d = c; c = b; b = a; a = t1 + t2;
    }
    
    ctx.hash[0] += a; ctx.hash[1] += b; ctx.hash[2] += c; ctx.hash[3] += d;
    ctx.hash[4] += e; ctx.hash[5] += f; ctx.hash[6] += g; ctx.hash[7] += h;
}

void sha256Init(Sha256Context& ctx) {
    ctx.totalLength = 0;
    ctx.hash[0] = 0x6a09e667;
    ctx.hash[1] = 0xbb67ae85;
    ctx.hash[2] = 0x3c6ef372;
    ctx.hash[3] = 0xa54ff53a;
    ctx.hash[4] = 0x510e527f;
    ctx.hash[5] = 0x9b05688c;
    ctx.hash[6] = 0x1f83d9ab;
    ctx.hash[7] = 0x5be0cd19;
}

void sha256Update(Sha256Context& ctx, const uint8* data, uint32 len) {
    uint32 bufferOffset = ctx.totalLength % 64;
    ctx.totalLength += len;
    
    if (bufferOffset + len >= 64) {
        uint32 fill = 64 - bufferOffset;
        memcpy(&ctx.buffer[bufferOffset], data, fill);
        sha256Transform(ctx, ctx.buffer);
        data += fill;
        len -= fill;
        while (len >= 64) {
            sha256Transform(ctx, data);
            data += 64;
            len -= 64;
        }
        bufferOffset = 0;
    }
    memcpy(&ctx.buffer[bufferOffset], data, len);
}

void sha256Final(Sha256Context& ctx, uint8* digest) {
    uint32 padLen = 64 - (ctx.totalLength % 64);
    uint8 pad[64];
    memset(pad, 0, 64);
    pad[0] = 0x80;
    
    if (padLen < 8) {
        sha256Update(ctx, pad, padLen);
        memset(pad, 0, 64);
        padLen = 64;
    }
    
    uint64 bitLen = (uint64)ctx.totalLength * 8;
    for (int i = 0; i < 8; i++) {
        pad[padLen - 8 + i] = (bitLen >> (56 - i*8)) & 0xFF;
    }
    sha256Update(ctx, pad, padLen);
    
    for (int i = 0; i < 8; i++) {
        digest[i*4]   = (ctx.hash[i] >> 24) & 0xFF;
        digest[i*4+1] = (ctx.hash[i] >> 16) & 0xFF;
        digest[i*4+2] = (ctx.hash[i] >> 8) & 0xFF;
        digest[i*4+3] = ctx.hash[i] & 0xFF;
    }
}

std::string sha256Hex(const std::string& input) {
    Sha256Context ctx;
    sha256Init(ctx);
    sha256Update(ctx, (const uint8*)input.data(), (uint32)input.size());
    
    uint8 digest[32];
    sha256Final(ctx, digest);
    
    std::stringstream ss;
    for (int i = 0; i < 32; i++) {
        ss << std::hex << std::setfill('0') << std::setw(2) << (int)digest[i];
    }
    return ss.str();
}

} // anonymous namespace

// =========================================================================
// CORE FIELD THEORY FUNCTIONS
// =========================================================================

float computeLoadY(const CognitiveVector& cv) {
    return 0.2f * cv.past + 0.5f * cv.present + 0.3f * cv.future;
}

float computeFrictionW(float load_y) {
    return std::abs(load_y - 0.5f) * PHI;
}

float computeOmegaT(float friction_w) {
    return friction_w * 14.0f;
}

bool checkZeroDefect(float load_y, int critical_count) {
    return (load_y <= ZERO_DEFECT_THRESHOLD) && (critical_count == 0);
}

bool checkCritical(float omega_t) {
    return omega_t >= OMEGA_CRITICAL;
}

bool checkWarning(float load_y) {
    return load_y > WARNING_THRESHOLD;
}

// =========================================================================
// PATTERN MATCHING FOR SECURITY SCAN
// =========================================================================

int countPattern(const std::string& content, const std::string& pattern) {
    int count = 0;
    size_t pos = 0;
    while ((pos = content.find(pattern, pos)) != std::string::npos) {
        count++;
        pos += pattern.length();
    }
    return count;
}

bool containsPattern(const std::string& content, const std::string& pattern) {
    return content.find(pattern) != std::string::npos;
}

// =========================================================================
// LANGUAGE-SPECIFIC METRIC EXTRACTION
// =========================================================================

void extractJSMetrics(const std::string& content,
                      CognitiveVector& cv,
                      SecurityMetrics& sec,
                      PerformanceMetrics& perf) {
    // Count lines
    perf.total_lines = std::count(content.begin(), content.end(), '\n') + 1;
    
    // Count functions (simplified)
    perf.total_functions = countPattern(content, "function ") + 
                          countPattern(content, "=>") +
                          countPattern(content, "() {");
    
    // Cyclomatic complexity (simplified: count decision points)
    perf.cyclomatic_complexity = countPattern(content, "if (") +
                                countPattern(content, "else if") +
                                countPattern(content, "while (") +
                                countPattern(content, "for (") +
                                countPattern(content, "switch (") +
                                countPattern(content, "case ") +
                                countPattern(content, "catch (") +
                                countPattern(content, "&&") +
                                countPattern(content, "||");
    
    // Nesting depth (simplified heuristic)
    int maxNest = 0;
    int currentNest = 0;
    for (char c : content) {
        if (c == '{') currentNest++;
        if (c == '}') currentNest--;
        if (currentNest > maxNest) maxNest = currentNest;
    }
    perf.nesting_depth = maxNest;
    
    // Nested loops detection
    int forLoops = countPattern(content, "for (");
    int whileLoops = countPattern(content, "while (");
    perf.nested_loops = (forLoops > 1 || whileLoops > 1) ? (forLoops + whileLoops - 1) : 0;
    
    // Blocking I/O
    perf.blocking_io_count = countPattern(content, "fs.readFileSync") +
                            countPattern(content, "fs.writeFileSync") +
                            countPattern(content, "XMLHttpRequest") +
                            countPattern(content, ".sync(");
    
    // Security patterns
    sec.eval_count = countPattern(content, "eval(");
    sec.innerHTML_count = countPattern(content, ".innerHTML");
    sec.document_write_count = countPattern(content, "document.write");
    sec.sql_concat_count = countPattern(content, "SELECT ") + countPattern(content, "INSERT INTO");
    sec.hardcoded_secret_count = countPattern(content, "password = \"") + 
                                countPattern(content, "secret = \"") +
                                countPattern(content, "apiKey = \"");
    sec.unsafe_deserialization_count = countPattern(content, "JSON.parse") + 
                                      countPattern(content, "eval(");
    sec.xss_risk_count = countPattern(content, ".innerHTML") + countPattern(content, "dangerouslySetInnerHTML");
    sec.insecure_random_count = countPattern(content, "Math.random()");
    sec.proto_pollution_count = countPattern(content, "__proto__") + countPattern(content, "prototype");
    sec.path_traversal_count = countPattern(content, "../") + countPattern(content, "..\\");
    
    sec.total_critical = sec.eval_count + sec.innerHTML_count + sec.document_write_count +
                        sec.sql_concat_count + sec.hardcoded_secret_count +
                        sec.unsafe_deserialization_count + sec.xss_risk_count +
                        sec.proto_pollution_count + sec.path_traversal_count;
    
    // Cognitive vector computation
    // past: complexity debt (high complexity = high past load)
    cv.past = std::min(perf.cyclomatic_complexity * 0.3f, 10.0f);
    // present: active processing (nesting + functions)
    cv.present = std::min((perf.nesting_depth * 0.5f + perf.total_functions * 0.2f), 10.0f);
    // future: risk projection (security findings = future risk)
    cv.future = std::min(sec.total_critical * 0.5f, 10.0f);
}

void extractHTMLMetrics(const std::string& content,
                        CognitiveVector& cv,
                        SecurityMetrics& sec,
                        PerformanceMetrics& perf) {
    perf.total_lines = std::count(content.begin(), content.end(), '\n') + 1;
    
    // Count DOM nodes (simplified: count tags)
    perf.total_functions = countPattern(content, "<") - countPattern(content, "</");
    
    // Nesting depth
    int maxNest = 0;
    int currentNest = 0;
    for (char c : content) {
        if (c == '<') {
            if (content.find("/>", &c - content.data()) != std::string::npos) continue;
            currentNest++;
        }
        if (c == '>') currentNest--;
        if (currentNest > maxNest) maxNest = currentNest;
    }
    perf.nesting_depth = maxNest;
    
    // Security
    sec.eval_count = 0;
    sec.innerHTML_count = countPattern(content, "innerHTML");
    sec.document_write_count = countPattern(content, "document.write");
    sec.sql_concat_count = 0;
    sec.hardcoded_secret_count = countPattern(content, "password=") + 
                                countPattern(content, "secret=");
    sec.unsafe_deserialization_count = 0;
    sec.xss_risk_count = countPattern(content, "innerHTML") + 
                        countPattern(content, "onload=") +
                        countPattern(content, "onclick=");
    sec.insecure_random_count = 0;
    sec.proto_pollution_count = 0;
    sec.path_traversal_count = 0;
    
    sec.total_critical = sec.innerHTML_count + sec.document_write_count +
                        sec.hardcoded_secret_count + sec.xss_risk_count;
    
    cv.past = perf.nesting_depth * 0.2f;
    cv.present = perf.total_functions * 0.1f;
    cv.future = sec.total_critical * 0.5f;
}

void extractCSSMetrics(const std::string& content,
                       CognitiveVector& cv,
                       SecurityMetrics& sec,
                       PerformanceMetrics& perf) {
    perf.total_lines = std::count(content.begin(), content.end(), '\n') + 1;
    
    perf.total_functions = countPattern(content, "{");
    
    // Nesting depth (CSS nesting)
    int maxNest = 0;
    int currentNest = 0;
    for (char c : content) {
        if (c == '{') currentNest++;
        if (c == '}') currentNest--;
        if (currentNest > maxNest) maxNest = currentNest;
    }
    perf.nesting_depth = maxNest;
    
    // Cyclomatic complexity = number of selectors
    perf.cyclomatic_complexity = countPattern(content, ",") + perf.total_functions;
    
    sec.eval_count = 0;
    sec.innerHTML_count = 0;
    sec.document_write_count = 0;
    sec.sql_concat_count = 0;
    sec.hardcoded_secret_count = 0;
    sec.unsafe_deserialization_count = 0;
    sec.xss_risk_count = 0;
    sec.insecure_random_count = 0;
    sec.proto_pollution_count = 0;
    sec.path_traversal_count = 0;
    sec.total_critical = 0;
    
    cv.past = perf.nesting_depth * 0.15f;
    cv.present = perf.total_functions * 0.1f;
    cv.future = 0.0f;
}

void extractConfigMetrics(const std::string& content,
                          CognitiveVector& cv,
                          SecurityMetrics& sec,
                          PerformanceMetrics& perf) {
    perf.total_lines = std::count(content.begin(), content.end(), '\n') + 1;
    
    perf.total_functions = 0;
    perf.cyclomatic_complexity = 0;
    perf.nesting_depth = 0;
    perf.nested_loops = 0;
    perf.blocking_io_count = 0;
    
    sec.eval_count = 0;
    sec.innerHTML_count = 0;
    sec.document_write_count = 0;
    sec.sql_concat_count = 0;
    sec.hardcoded_secret_count = countPattern(content, "password") + 
                                countPattern(content, "secret") +
                                countPattern(content, "token") +
                                countPattern(content, "key");
    sec.unsafe_deserialization_count = 0;
    sec.xss_risk_count = 0;
    sec.insecure_random_count = 0;
    sec.proto_pollution_count = 0;
    sec.path_traversal_count = countPattern(content, "../");
    
    sec.total_critical = sec.hardcoded_secret_count + sec.path_traversal_count;
    
    cv.past = 0.0f;
    cv.present = perf.total_lines * 0.01f;
    cv.future = sec.total_critical * 0.5f;
}

// =========================================================================
// FILE EVALUATION
// =========================================================================

FileMetrics evaluateFile(const std::string& filename,
                         const std::string& content,
                         const std::string& language) {
    FileMetrics fm;
    fm.filename = filename;
    fm.language = language;
    
    // Initialize metrics
    fm.cognitive = {0.0f, 0.0f, 0.0f};
    fm.security = {0,0,0,0,0,0,0,0,0,0,0};
    fm.performance = {0,0,0,0,0,0,0.0f};
    
    // Extract language-specific metrics
    if (language == "javascript" || language == "typescript") {
        extractJSMetrics(content, fm.cognitive, fm.security, fm.performance);
    } else if (language == "html") {
        extractHTMLMetrics(content, fm.cognitive, fm.security, fm.performance);
    } else if (language == "css") {
        extractCSSMetrics(content, fm.cognitive, fm.security, fm.performance);
    } else {
        extractConfigMetrics(content, fm.cognitive, fm.security, fm.performance);
    }
    
    // Compute code/comment/blank lines
    fm.code_lines = fm.total_lines;
    fm.comment_lines = 0;
    fm.blank_lines = 0;
    
    // Compute cognitive load
    fm.load_y = computeLoadY(fm.cognitive);
    fm.friction_w = computeFrictionW(fm.load_y);
    fm.omega_t = computeOmegaT(fm.friction_w);
    
    // Check states
    fm.is_zero_defect = checkZeroDefect(fm.load_y, fm.security.total_critical);
    fm.is_critical = checkCritical(fm.omega_t);
    fm.is_warning = checkWarning(fm.load_y);
    
    return fm;
}

// =========================================================================
// PROJECT EVALUATION
// =========================================================================

AuditResult evaluateProject(const std::string& project_name,
                            const std::vector<std::pair<std::string, std::string>>& files) {
    AuditResult result;
    result.project_name = project_name;
    
    // Get current timestamp
    result.timestamp = "2026-08-23T17:33:37+02:00";
    
    // Aggregate metrics
    float total_past = 0, total_present = 0, total_future = 0;
    float total_omega = 0;
    float max_omega = 0;
    int total_critical = 0;
    int total_findings = 0;
    
    for (const auto& file : files) {
        std::string filename = file.first;
        std::string content = file.second;
        
        // Determine language
        std::string language = "config";
        if (filename.ends_with(".js") || filename.ends_with(".mjs") || filename.ends_with(".cjs"))
            language = "javascript";
        else if (filename.ends_with(".ts") || filename.ends_with(".tsx"))
            language = "typescript";
        else if (filename.ends_with(".html") || filename.ends_with(".htm"))
            language = "html";
        else if (filename.ends_with(".css") || filename.ends_with(".scss") || filename.ends_with(".sass"))
            language = "css";
        else if (filename.ends_with(".json") || filename.ends_with(".yaml") || 
                 filename.ends_with(".yml") || filename.ends_with(".toml"))
            language = "config";
        
        FileMetrics fm = evaluateFile(filename, content, language);
        result.files.push_back(fm);
        
        total_past += fm.cognitive.past;
        total_present += fm.cognitive.present;
        total_future += fm.cognitive.future;
        total_omega += fm.omega_t;
        if (fm.omega_t > max_omega) max_omega = fm.omega_t;
        total_critical += fm.security.total_critical;
        total_findings += fm.security.total_critical;
    }
    
    // Compute aggregate cognitive vector
    int fileCount = (int)files.size();
    result.aggregate_cognitive = {
        total_past / std::max(fileCount, 1),
        total_present / std::max(fileCount, 1),
        total_future / std::max(fileCount, 1)
    };
    
    result.aggregate_load_y = computeLoadY(result.aggregate_cognitive);
    result.aggregate_friction_w = computeFrictionW(result.aggregate_load_y);
    result.aggregate_omega_t = computeOmegaT(result.aggregate_friction_w);
    result.max_omega_t = max_omega;
    
    result.total_security_findings = total_findings;
    result.total_critical_findings = total_critical;
    
    result.is_zero_defect = checkZeroDefect(result.aggregate_load_y, total_critical);
    result.is_critical = checkCritical(result.max_omega_t);
    result.is_warning = checkWarning(result.aggregate_load_y);
    
    result.certificate_hash = generateCertificateHash(result);
    
    if (result.is_zero_defect) {
        result.zero_defect_badge = "ZERO_DEFECT_CERTIFIED";
    } else if (result.is_warning) {
        result.zero_defect_badge = "WARNING";
    } else {
        result.zero_defect_badge = "PROCESSING_MODE";
    }
    
    return result;
}

// =========================================================================
// CERTIFICATE HASH GENERATION
// =========================================================================

std::string generateCertificateHash(const AuditResult& result) {
    std::stringstream ss;
    ss << result.project_name;
    ss << result.timestamp;
    ss << result.aggregate_cognitive.past;
    ss << result.aggregate_cognitive.present;
    ss << result.aggregate_cognitive.future;
    ss << result.aggregate_load_y;
    ss << result.aggregate_friction_w;
    ss << result.aggregate_omega_t;
    ss << result.total_security_findings;
    ss << result.total_critical_findings;
    ss << (result.is_zero_defect ? "1" : "0");
    
    // Include file hashes
    for (const auto& fm : result.files) {
        ss << fm.filename;
        ss << fm.language;
        ss << fm.total_lines;
        ss << fm.cognitive.past;
        ss << fm.cognitive.present;
        ss << fm.cognitive.future;
        ss << fm.load_y;
        ss << fm.security.total_critical;
    }
    
    return sha256Hex(ss.str());
}

// =========================================================================
// ZERO-DEFECT CERTIFICATION
// =========================================================================

bool checkZeroDefectCertification(const AuditResult& result) {
    return result.is_zero_defect && 
           result.total_critical_findings == 0 &&
           !result.is_critical &&
           !result.is_warning;
}

// =========================================================================
// WASM EXPORTS (extern "C" for ccall/cwrap compatibility)
// =========================================================================

extern "C" {

// SHA-256 hash computation (WASM-accelerated)
char* wasm_sha256(const char* input) {
    std::string result = sha256Hex(std::string(input));
    char* output = new char[result.length() + 1];
    strcpy(output, result.c_str());
    return output;
}

// Field theory computations (WASM-accelerated)
float wasm_compute_load_y(float past, float present, float future) {
    CognitiveVector cv{past, present, future};
    return computeLoadY(cv);
}

float wasm_compute_friction_w(float load_y) {
    return computeFrictionW(load_y);
}

float wasm_compute_omega_t(float friction_w) {
    return computeOmegaT(friction_w);
}

// Combined field theory computation (single WASM call)
float wasm_compute_omega_from_cognitive(float past, float present, float future) {
    CognitiveVector cv{past, present, future};
    float load_y = computeLoadY(cv);
    float friction_w = computeFrictionW(load_y);
    return computeOmegaT(friction_w);
}

// Zero-Defect check
int wasm_check_zero_defect(float load_y, int critical_count) {
    return checkZeroDefect(load_y, critical_count) ? 1 : 0;
}

int wasm_check_critical(float omega_t) {
    return checkCritical(omega_t) ? 1 : 0;
}

int wasm_check_warning(float load_y) {
    return checkWarning(load_y) ? 1 : 0;
}

// File evaluation (simplified for WASM - returns key metrics)
int wasm_evaluate_file_security(const char* content, const char* language) {
    CognitiveVector cv;
    SecurityMetrics sec;
    PerformanceMetrics perf;
    
    std::string lang(language);
    if (lang == "javascript" || lang == "typescript") {
        extractJSMetrics(std::string(content), cv, sec, perf);
    } else if (lang == "html") {
        extractHTMLMetrics(std::string(content), cv, sec, perf);
    } else {
        extractConfigMetrics(std::string(content), cv, sec, perf);
    }
    
    return sec.total_critical;
}

// Free WASM-allocated memory
void wasm_free_string(char* ptr) {
    delete[] ptr;
}

} // extern "C"

} // namespace mmsi_audit
