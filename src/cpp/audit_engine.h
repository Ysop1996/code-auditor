#pragma once

#include <cstdint>
#include <string>
#include <vector>
#include <array>

namespace mmsi_audit {

// =========================================================================
// CONSTANTS (replicating MMSI V3.8 field theory)
// =========================================================================

constexpr float PHI = 1.61803398875f;
constexpr float OMEGA_CRITICAL = 5800.0f;
constexpr float ZERO_DEFECT_THRESHOLD = 1.0f;
constexpr float WARNING_THRESHOLD = 0.8f;
constexpr float W_BASE = 0.7557f;
constexpr float DW = 0.6690f;
constexpr float E_PENALTY = 1.201301f;
constexpr float M_MASK = 0.643047f;
constexpr float S_VN = 0.69229f;

// Hard-gate penalty weights
constexpr float PENALTY_EVAL = 50.0f;
constexpr float PENALTY_INNERHTML = 40.0f;
constexpr float PENALTY_DOCUMENT_WRITE = 35.0f;
constexpr float PENALTY_SQL_CONCAT = 45.0f;
constexpr float PENALTY_HARDCODED_SECRET = 60.0f;
constexpr float PENALTY_MISSING_ERROR_BOUNDARY = 25.0f;
constexpr float PENALTY_UNCONTROLLED_INPUT = 20.0f;
constexpr float PENALTY_NESTED_LOOP = 15.0f;
constexpr float PENALTY_BLOCKING_IO = 30.0f;
constexpr float PENALTY_UNSAFE_DESERIALIZATION = 50.0f;
constexpr float PENALTY_XSS_RISK = 35.0f;
constexpr float PENALTY_INSECURE_RANDOM = 15.0f;
constexpr float PENALTY_PROTO_POLLUTION = 40.0f;
constexpr float PENALTY_NO_RATE_LIMIT = 10.0f;
constexpr float PENALTY_MISSING_AUTH = 30.0f;
constexpr float PENALTY_LOG_INJECTION = 20.0f;
constexpr float PENALTY_PATH_TRAVERSAL = 45.0f;

// =========================================================================
// DATA STRUCTURES
// =========================================================================

struct CognitiveVector {
    float past;
    float present;
    float future;
};

struct SecurityMetrics {
    int eval_count;
    int innerhtml_count;
    int document_write_count;
    int sql_concat_count;
    int hardcoded_secret_count;
    int unsafe_deserialization_count;
    int xss_risk_count;
    int insecure_random_count;
    int proto_pollution_count;
    int path_traversal_count;
    int total_critical;
};

struct PerformanceMetrics {
    int cyclomatic_complexity;
    int nesting_depth;
    int nested_loops;
    int blocking_io_count;
    int total_lines;
    int total_functions;
    float avg_function_length;
};

struct FileMetrics {
    std::string filename;
    std::string language;
    int total_lines;
    int code_lines;
    int comment_lines;
    int blank_lines;
    CognitiveVector cognitive;
    SecurityMetrics security;
    PerformanceMetrics performance;
    float load_y;        // loadY = 0.2*i_past + 0.5*i_present + 0.3*i_future
    float friction_w;    // frictionW = |loadY - 0.5| * PHI
    float omega_t;       // Ω(t) = frictionW * 14.0
    bool is_zero_defect;  // W(t) <= 1.0 && E_critical == 0
    bool is_critical;    // Ω(t) >= OMEGA_CRITICAL
    bool is_warning;     // W(t) > WARNING_THRESHOLD
};

struct AuditResult {
    std::string project_name;
    std::string timestamp;
    std::vector<FileMetrics> files;
    CognitiveVector aggregate_cognitive;
    float aggregate_load_y;
    float aggregate_friction_w;
    float aggregate_omega_t;
    float max_omega_t;
    int total_security_findings;
    int total_critical_findings;
    bool is_zero_defect;
    bool is_critical;
    bool is_warning;
    std::string certificate_hash;
    std::string zero_defect_badge;
};

// =========================================================================
// CORE FUNCTIONS
// =========================================================================

// Compute loadY from cognitive vector
float computeLoadY(const CognitiveVector& cv);

// Compute frictionW from loadY
float computeFrictionW(float load_y);

// Compute Ω(t) from frictionW
float computeOmegaT(float friction_w);

// Check if in Zero-Defect
bool checkZeroDefect(float load_y, int critical_count);

// Check if critical
bool checkCritical(float omega_t);

// Check if warning
bool checkWarning(float load_y);

// Generate SHA-256 certificate hash
std::string generateCertificateHash(const AuditResult& result);

// Evaluate a single file
FileMetrics evaluateFile(const std::string& filename,
                         const std::string& content,
                         const std::string& language);

// Evaluate a full project
AuditResult evaluateProject(const std::string& project_name,
                            const std::vector<std::pair<std::string, std::string>>& files);

// Check Zero-Defect certification
bool checkZeroDefectCertification(const AuditResult& result);

} // namespace mmsi_audit
