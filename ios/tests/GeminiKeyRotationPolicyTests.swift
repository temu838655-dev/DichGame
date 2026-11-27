import Foundation

@main struct GeminiKeyRotationPolicyTests {
    static func main() {
        precondition(GeminiKeyRotationPolicy.shouldTryNextKey(
            statusCode: 429,
            responseBody: #"{"error":{"status":"RESOURCE_EXHAUSTED","message":"Rate limit exceeded"}}"#
        ))
        precondition(!GeminiKeyRotationPolicy.shouldTryNextKey(
            statusCode: 429,
            responseBody: #"{"error":{"code":"credit_balance_exhausted"}}"#
        ))
        precondition(GeminiKeyRotationPolicy.shouldTryNextKey(statusCode: 401, responseBody: ""))
        precondition(GeminiKeyRotationPolicy.shouldTryNextKey(statusCode: 403, responseBody: "API key was reported as leaked"))
        precondition(GeminiKeyRotationPolicy.shouldTryNextKey(statusCode: 400, responseBody: "API key not valid"))
        precondition(!GeminiKeyRotationPolicy.shouldTryNextKey(statusCode: 403, responseBody: "Model not enabled for this project"))
        precondition(!GeminiKeyRotationPolicy.shouldTryNextKey(statusCode: 400, responseBody: "Invalid JSON payload"))
        precondition(!GeminiKeyRotationPolicy.shouldTryNextKey(statusCode: 503, responseBody: "server unavailable"))
        precondition(GeminiKeyRotationPolicy.retryAfterDescription("2") == "2 giây")
        precondition(GeminiKeyRotationPolicy.retryAfterDescription("0") == nil)
        print("iOS Gemini key rotation policy tests passed")
    }
}
