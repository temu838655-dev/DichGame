using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace DichGame.Windows;

internal static class GeminiClient
{
    private static readonly HttpClient Client = new() { Timeout = TimeSpan.FromSeconds(55) };
    private const string Endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

    public static async Task<string> TranslateAsync(byte[] jpeg, string game, string language, string rawKeys)
    {
        var keys = Regex.Split(rawKeys, @"[,;\s]+").Where(s => !string.IsNullOrWhiteSpace(s)).Distinct().ToArray();
        if (keys.Length == 0) throw new InvalidOperationException("Cần thêm ít nhất một khóa Gemini.");
        var prompt = $"""
            Bạn là biên dịch viên game sang tiếng Việt. Tên game: {game}.
            Ngôn ngữ gốc: {language}. Hãy đọc các dòng chữ thật trong ảnh chụp game.
            Ưu tiên phụ đề, hội thoại, menu và hướng dẫn có thể thấy rõ.
            Dịch sang tiếng Việt tự nhiên, chính xác theo ngữ cảnh game; giữ nguyên tên riêng.
            Không tự tưởng tượng chữ không nhìn thấy. Chỉ trả lại bản dịch tiếng Việt.
            Nếu không có chữ game cần dịch, trả lời: Không tìm thấy chữ cần dịch.
            """;
        string encodedImage = Convert.ToBase64String(jpeg);
        var json = JsonSerializer.Serialize(new
        {
            contents = new[] { new {
                parts = new object[] {
                    new { text = prompt },
                    new { inline_data = new { mime_type = "image/jpeg", data = encodedImage } }
                }
            }},
            generationConfig = new { temperature = 0.15 }
        });
        string lastError = "Không có khóa Gemini khả dụng.";
        foreach (string key in keys)
        {
            using var request = new HttpRequestMessage(HttpMethod.Post, Endpoint);
            request.Headers.Add("x-goog-api-key", key);
            request.Content = new StringContent(json, Encoding.UTF8, "application/json");
            using var response = await Client.SendAsync(request);
            var text = await response.Content.ReadAsStringAsync();
            if (!response.IsSuccessStatusCode)
            {
                var msg = $"Gemini HTTP {(int)response.StatusCode}: {text[..Math.Min(text.Length, 230)]}";
                if (response.StatusCode == HttpStatusCode.Unauthorized ||
                    response.StatusCode == HttpStatusCode.Forbidden)
                {
                    lastError = msg;
                    continue;
                }
                throw new InvalidOperationException(msg);
            }
            using var doc = JsonDocument.Parse(text);
            if (!doc.RootElement.TryGetProperty("candidates", out var candidates) ||
                candidates.GetArrayLength() == 0 ||
                !candidates[0].TryGetProperty("content", out var content) ||
                !content.TryGetProperty("parts", out var parts))
                throw new InvalidOperationException("Gemini không trả về bản dịch.");
            if (candidates[0].TryGetProperty("finishReason", out var reason) && reason.GetString() != "STOP")
                throw new InvalidOperationException("Gemini chưa hoàn tất bản dịch.");
            string result = string.Join("\n", parts.EnumerateArray()
                .Where(p => (!p.TryGetProperty("thought", out var thought) || thought.ValueKind != JsonValueKind.True) && p.TryGetProperty("text", out _))
                .Select(p => p.GetProperty("text").GetString()));
            if (string.IsNullOrWhiteSpace(result))
                throw new InvalidOperationException("Gemini không tìm thấy chữ có thể dịch.");
            return result.Trim();
        }
        throw new InvalidOperationException(lastError);
    }
}
