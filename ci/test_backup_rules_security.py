import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res" / "xml"


def excluded_paths(element):
    return {
        node.attrib.get("path")
        for node in element.findall(".//exclude")
        if node.attrib.get("domain") == "sharedpref"
    }


class BackupRulesSecurityTest(unittest.TestCase):
    def test_legacy_preferences_are_excluded_from_full_backup(self):
        root = ET.parse(RES / "backup_rules.xml").getroot()
        self.assertIn("dg.xml", excluded_paths(root))
        self.assertIn("dg_api_secrets.xml", excluded_paths(root))
        self.assertIn("ps.xml", excluded_paths(root))
        self.assertIn("ps_remote_play.xml", excluded_paths(root))

    def test_legacy_preferences_are_excluded_from_cloud_and_device_transfer(self):
        root = ET.parse(RES / "data_extraction_rules.xml").getroot()
        cloud = root.find("cloud-backup")
        transfer = root.find("device-transfer")
        self.assertIsNotNone(cloud)
        self.assertIsNotNone(transfer)
        for section in (cloud, transfer):
            paths = excluded_paths(section)
            self.assertIn("dg.xml", paths)
            self.assertIn("dg_api_secrets.xml", paths)
            self.assertIn("ps.xml", paths)
            self.assertIn("ps_remote_play.xml", paths)


if __name__ == "__main__":
    unittest.main()
