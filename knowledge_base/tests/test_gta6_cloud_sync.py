import tempfile
import unittest
from pathlib import Path

from openpyxl import Workbook

from knowledge_base.gta6_cloud_sync import rows_from_workbook


class Gta6CloudSyncTests(unittest.TestCase):
    def test_reads_vietnamese_headers_and_keeps_sheet_context(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'source.xlsx'
            workbook = Workbook()
            sheet = workbook.active
            sheet.title = 'Story'
            sheet.append(['STT', 'ID', 'Nhóm', 'English', 'Tiếng Việt'])
            sheet.append([1, 'S_001', 'Story | Mission A', 'We need to move.', None])
            workbook.save(path)
            rows = list(rows_from_workbook(path))
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]['external_id'], 'S_001')
        self.assertEqual(rows[0]['original'], 'We need to move.')
        self.assertEqual(rows[0]['vi'], '')
        self.assertEqual(rows[0]['group_name'], 'Story | Mission A')
        self.assertEqual(rows[0]['sheet_name'], 'Story')
        self.assertEqual(rows[0]['row_number'], 2)


if __name__ == '__main__':
    unittest.main()
