import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from check_pack_update import check

class UpdateTest(unittest.TestCase):
    def test_update(self):
        with tempfile.TemporaryDirectory() as d:
            a, b = Path(d)/"old", Path(d)/"new"
            for root, version in [(a,1),(b,2)]:
                root.mkdir()
                data = b"source,vi,game\nHello,Chao,GTA VI\n"
                (root/"pack.csv").write_bytes(data)
                (root/"pack.dichgame.json").write_text(json.dumps({"schema":1,"game":"GTA VI","version":version,"url":f"https://example.com/v{version}/pack.csv","bytes":len(data),"sha256":hashlib.sha256(data).hexdigest()}))
            self.assertEqual(check(a,b,"GTA VI")["candidate"]["version"],2)
            with self.assertRaises(ValueError):
                check(b,a,"GTA VI")
            p=b/"pack.dichgame.json"
            m=json.loads(p.read_text())
            m["url"]="https://example.com/v1/pack.csv"
            p.write_text(json.dumps(m))
            with self.assertRaises(ValueError):
                check(a,b,"GTA VI")

    def test_hidden_removal(self):
        with tempfile.TemporaryDirectory() as d:
            a, b = Path(d)/"old", Path(d)/"new"
            for root, version, rows in [
                (a, 1, "Hello,Chao,GTA VI\nGoodbye,Tam biet,GTA VI\n"),
                (b, 2, "Hello,Chao,GTA VI\nYes,Vang,GTA VI\nNo,Khong,GTA VI\n")]:
                root.mkdir()
                data = ("source,vi,game\n" + rows).encode()
                (root/"pack.csv").write_bytes(data)
                (root/"pack.dichgame.json").write_text(json.dumps({"schema":1,"game":"GTA VI","version":version,"url":f"https://example.com/v{version}/pack.csv","bytes":len(data),"sha256":hashlib.sha256(data).hexdigest()}))
            with self.assertRaisesRegex(ValueError, "remove 1"):
                check(a,b,"GTA VI")
            result = check(a,b,"GTA VI",allow_removals=True)
            self.assertEqual((result["removed"],result["added"],result["changed"]),(1,2,0))
