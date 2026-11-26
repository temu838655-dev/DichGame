import json
import tempfile
import unittest
from pathlib import Path

from knowledge_base.db import KnowledgeBase
from knowledge_base.importers import ImportValidationError, TranslationImporter


class ImporterTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.kb = KnowledgeBase(self.root / "kb.db")
        self.kb.add_game("fixture-game", "Fixture Game", "PC")
        self.importer = TranslationImporter(self.kb)

    def tearDown(self):
        self.tmp.cleanup()

    def import_path(self, name, content):
        path = self.root / name
        path.write_text(content, encoding="utf-8")
        return self.importer.import_file(
            "fixture-game", path, source_name="Unit test fixture",
            license_id="TEST-PERMISSION", confirm_rights=True,
        )

    def test_csv_and_duplicate_suppression(self):
        report = self.import_path("strings.csv",
            "source_text,translated_text,context\nHello,Xin chào,menu\n")
        self.assertEqual((report.imported, report.skipped, report.status), (1, 0, "COMPLETED"))
        again = self.import_path("strings.csv",
            "source_text,translated_text,context\nHello,Xin chào,menu\n")
        self.assertEqual((again.imported, again.skipped), (0, 1))

    def test_source_only_workbook_stages_blank_vietnamese_cells(self):
        try:
            from openpyxl import Workbook
        except ImportError:
            self.skipTest('openpyxl is not installed')
        path = self.root / 'source-only.xlsx'
        workbook = Workbook()
        sheet = workbook.active
        sheet.title = 'Core'
        sheet.append(['STT', 'ID', 'Nhóm', 'English', 'Tiếng Việt'])
        sheet.append([1, 'C0000_B381007C', 'Thoại | Core C0000', 'Hey! Eyes open!', None])
        workbook.save(path)
        report = self.importer.import_source_workbook(
            'fixture-game', path, source_name='Authorized source fixture',
            license_id='TEST-PERMISSION', confirm_rights=True,
        )
        self.assertEqual((report.imported, report.skipped, report.rejected), (1, 0, 0))
        pending = self.kb.pending_source_strings('fixture-game')
        self.assertEqual(pending[0]['external_id'], 'C0000_B381007C')
        self.assertEqual(pending[0]['source_text'], 'Hey! Eyes open!')
        self.assertEqual(pending[0]['context'], 'Thoại | Core C0000')
        self.assertIsNone(pending[0]['translated_text'])
    def test_xlsx_preserves_original_ids_and_group_context(self):
        try:
            from openpyxl import Workbook
        except ImportError:
            self.skipTest("openpyxl is not installed")
        path = self.root / "gta6.xlsx"
        workbook = Workbook()
        sheet = workbook.active
        sheet.title = "Core"
        sheet.append(["STT", "ID", "Nhóm", "English", "Tiếng Việt"])
        sheet.append([1, "C0000_B381007C", "Thoại | Core C0000", "Hey! Eyes open!", "Này! Tỉnh táo lên!"])
        workbook.save(path)
        report = self.importer.import_file(
            "fixture-game", path, source_name="Authorized Excel fixture",
            license_id="TEST-PERMISSION", confirm_rights=True,
        )
        self.assertEqual(report.imported, 1)
        self.assertEqual(
            self.kb.lookup_external_id("fixture-game", "C0000_B381007C")["translated_text"],
            "Này! Tỉnh táo lên!",
        )
    def test_xlsx_keeps_different_ids_for_identical_text(self):
        try:
            from openpyxl import Workbook
        except ImportError:
            self.skipTest('openpyxl is not installed')
        path = self.root / 'same-text.xlsx'
        workbook = Workbook()
        sheet = workbook.active
        sheet.append(['ID', 'English', 'Vietnamese'])
        sheet.append(['A_001', 'Continue', 'Tiếp tục'])
        sheet.append(['B_002', 'Continue', 'Tiếp tục'])
        workbook.save(path)
        report = self.importer.import_file(
            'fixture-game', path, source_name='Authorized duplicate-ID fixture',
            license_id='TEST-PERMISSION', confirm_rights=True,
        )
        self.assertEqual(report.imported, 2)
        self.assertEqual(self.kb.lookup_external_id('fixture-game', 'A_001')['translated_text'], 'Tiếp tục')
        self.assertEqual(self.kb.lookup_external_id('fixture-game', 'B_002')['translated_text'], 'Tiếp tục')
    def test_json(self):
        report = self.import_path("strings.json", json.dumps([
            {"source": "Start", "target": "Bắt đầu"},
            {"source_text": "Exit", "translated_text": "Thoát"}
        ], ensure_ascii=False))
        self.assertEqual(report.imported, 2)

    def test_po_singular_and_context(self):
        report = self.import_path("strings.po",
            'msgctxt "main menu"\nmsgid "Continue"\nmsgstr "Tiếp tục"\n')
        self.assertEqual(report.imported, 1)
        self.assertEqual(self.kb.lookup_exact("fixture-game", "Continue")["translated_text"], "Tiếp tục")

    def test_xliff(self):
        report = self.import_path("strings.xlf",
            '<xliff version="1.2"><file><body><trans-unit id="1"><source>Play</source><target>Chơi</target></trans-unit></body></file></xliff>')
        self.assertEqual(report.imported, 1)

    def test_tmx(self):
        report = self.import_path("strings.tmx",
            '<tmx version="1.4"><body><tu><tuv xml:lang="en"><seg>Load</seg></tuv><tuv xml:lang="vi"><seg>Tải</seg></tuv></tu></body></tmx>')
        self.assertEqual(report.imported, 1)

    def test_unknown_license_is_blocked(self):
        path = self.root / "strings.csv"
        path.write_text("source_text,translated_text\nA,B\n", encoding="utf-8")
        with self.assertRaises(ImportValidationError):
            self.importer.import_file("fixture-game", path, source_name="No rights",
                                      license_id="UNKNOWN", confirm_rights=True)

    def test_rights_confirmation_is_required(self):
        path = self.root / "strings.csv"
        path.write_text("source_text,translated_text\nA,B\n", encoding="utf-8")
        with self.assertRaises(ImportValidationError):
            self.importer.import_file("fixture-game", path, source_name="Source",
                                      license_id="CC-BY-4.0", confirm_rights=False)


if __name__ == "__main__":
    unittest.main()
