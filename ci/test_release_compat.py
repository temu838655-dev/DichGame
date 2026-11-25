import unittest

from release_compat import expected_versions, validate_release


class ReleaseCompatibilityTests(unittest.TestCase):
    def test_accepts_matching_phone_and_tv_build_deltas(self):
        self.assertEqual(
            (1759, 1742),
            validate_release(1467, 1739, 1487, 1470, 1759, 1742),
        )

    def test_rejects_non_increasing_build_number(self):
        with self.assertRaisesRegex(ValueError, "phải lớn hơn"):
            expected_versions(1467, 1467, 1487, 1470)

    def test_rejects_phone_version_code_mismatch(self):
        with self.assertRaisesRegex(ValueError, "điện thoại"):
            validate_release(1467, 1739, 1487, 1470, 1758, 1742)

    def test_rejects_tv_version_code_mismatch(self):
        with self.assertRaisesRegex(ValueError, "Android TV"):
            validate_release(1467, 1739, 1487, 1470, 1759, 1741)

    def test_rejects_non_positive_values(self):
        with self.assertRaisesRegex(ValueError, "số nguyên dương"):
            expected_versions(0, 1739, 1487, 1470)


if __name__ == "__main__":
    unittest.main()
