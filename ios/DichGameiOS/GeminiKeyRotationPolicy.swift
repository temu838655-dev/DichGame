import Foundation

/// Decides whether a failed Gemini request should move to another configured API key.
/// Billing/credit exhaustion is account-wide and must not fan out requests to every key.
enum GeminiKeyRotationPolicy {
    private static let billingMarkers = [
        "credit_balance_exhausted",
        "insufficient_quota",
        "billing_hard_limit_reached",
        "organization_spend_limit_exceeded",
        "project_spend_limit_exceeded",
        "billing_not_active"
    ]

    private static let invalidKeyMarkers = [
        "api_key_invalid",
        "api key not valid",
        "api key expired",
        "api key was reported as leaked",
        "reported as leaked",
        "api key is invalid"
    ]

    static func isBillingLimit(_ responseBody: String) -> Bool {
        let body = responseBody.lowercased()
        return billingMarkers.contains { body.contains($0) }
    }

    static func isInvalidKey(_ responseBody: String) -> Bool {
        let body = responseBody.lowercased()
        return invalidKeyMarkers.contains { body.contains($0) }
    }

    static func shouldTryNextKey(statusCode: Int, responseBody: String) -> Bool {
        if statusCode == 429 { return !isBillingLimit(responseBody) }
        if statusCode == 401 { return true }
        if statusCode == 400 || statusCode == 403 { return isInvalidKey(responseBody) }
        return false
    }

    static func retryAfterDescription(_ value: String?, now: Date = Date()) -> String? {
        guard let raw = value?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else {
            return nil
        }
        if let seconds = Double(raw), seconds.isFinite, seconds > 0 {
            return "\(Int(ceil(seconds))) giây"
        }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "EEE',' dd MMM yyyy HH':'mm':'ss z"
        guard let date = formatter.date(from: raw) else { return nil }
        let seconds = Int(ceil(date.timeIntervalSince(now)))
        return seconds > 0 ? "\(seconds) giây" : nil
    }
}
