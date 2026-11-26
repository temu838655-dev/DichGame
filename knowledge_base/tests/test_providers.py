import json
import unittest
from unittest.mock import patch

from knowledge_base.providers import (
    GeminiProvider,
    OpenAICompatibleProvider,
    ProviderHTTPError,
)


class FakeResponse:
    def __init__(self, payload):
        self.payload = json.dumps(payload).encode("utf-8")

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def read(self, size=-1):
        return self.payload[:size]


class ProviderAdapterTests(unittest.TestCase):
    @patch("knowledge_base.providers.urlopen")
    def test_gemini_adapter_parses_text_and_usage(self, mocked):
        mocked.return_value = FakeResponse({
            "candidates": [{"content": {"parts": [{"text": "Mở cổng"}]}}],
            "usageMetadata": {"promptTokenCount": 14, "candidatesTokenCount": 4},
        })
        provider = GeminiProvider("test-secret")
        result = provider.translate(
            "Open the gate", "quest", model="gemini-test", max_output_tokens=80,
        )
        self.assertEqual(result.translated_text, "Mở cổng")
        self.assertEqual((result.input_tokens, result.output_tokens), (14, 4))
        request = mocked.call_args.args[0]
        self.assertIn("gemini-test", request.full_url)
        self.assertIn("test-secret", request.full_url)
        payload = json.loads(request.data)
        self.assertEqual(payload["generationConfig"]["maxOutputTokens"], 80)

    @patch("knowledge_base.providers.urlopen")
    def test_gemini_batch_keeps_ids_and_parses_json(self, mocked):
        mocked.return_value = FakeResponse({
            "candidates": [{"content": {"parts": [{"text": json.dumps([
                {"id": "A_001", "translation": "Xin chào"},
                {"id": "B_002", "translation": "Tạm biệt"},
            ], ensure_ascii=False)}]}}],
        })
        provider = GeminiProvider("test-secret")
        result = provider.translate_many([
            {"id": "A_001", "text": "Hello", "context": "Greeting"},
            {"id": "B_002", "text": "Goodbye", "context": "Farewell"},
        ], model="gemini-test", max_output_tokens=256)
        self.assertEqual(result, {"A_001": "Xin chào", "B_002": "Tạm biệt"})
        payload = json.loads(mocked.call_args.args[0].data)
        self.assertEqual(payload["generationConfig"]["responseMimeType"], "application/json")
    @patch("knowledge_base.providers.urlopen")
    def test_openai_compatible_adapter_sets_auth_and_usage(self, mocked):
        mocked.return_value = FakeResponse({
            "choices": [{"message": {"content": "Nhiệm vụ hoàn tất"}}],
            "usage": {"prompt_tokens": 10, "completion_tokens": 5},
        })
        provider = OpenAICompatibleProvider(
            "test-secret", base_url="https://example.test/v1/chat/completions",
        )
        result = provider.translate(
            "Quest complete", "", model="test-model", max_output_tokens=64,
        )
        self.assertEqual(result.translated_text, "Nhiệm vụ hoàn tất")
        self.assertEqual((result.input_tokens, result.output_tokens), (10, 5))
        request = mocked.call_args.args[0]
        self.assertEqual(request.get_header("Authorization"), "Bearer test-secret")
        self.assertEqual(json.loads(request.data)["max_tokens"], 64)

    @patch("knowledge_base.providers.urlopen")
    def test_malformed_response_is_reported_without_leaking_key(self, mocked):
        class BadResponse(FakeResponse):
            def __init__(self):
                self.payload = b"not json"
        mocked.return_value = BadResponse()
        provider = OpenAICompatibleProvider("private-secret")
        with self.assertRaises(ProviderHTTPError) as error:
            provider.translate("Hello", "", model="test", max_output_tokens=32)
        self.assertNotIn("private-secret", str(error.exception))

    def test_https_and_credentials_are_required(self):
        with self.assertRaises(ValueError):
            OpenAICompatibleProvider("key", base_url="http://insecure.example")
        with self.assertRaises(ValueError):
            GeminiProvider("")


if __name__ == "__main__":
    unittest.main()
