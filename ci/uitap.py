#!/usr/bin/env python3
"""Đọc tệp uiautomator dump, in toạ độ giữa của nút đầu tiên có chữ khớp mẫu (regex, không phân biệt hoa thường).
Dùng: uitap.py <ui.xml> <mẫu>   (không thấy thì in rỗng, thoát mã 1)
      uitap.py <ui.xml> --has <mẫu>   (chỉ kiểm tra có chữ đó trên màn hình, thoát mã 0 nếu có)"""
import re, sys
import xml.etree.ElementTree as ET

def main():
    path = sys.argv[1]
    has = sys.argv[2] == "--has"
    pat = re.compile(sys.argv[3] if has else sys.argv[2], re.I)
    try:
        root = ET.parse(path).getroot()
    except Exception:
        sys.exit(1)
    for n in root.iter("node"):
        t = (n.get("text") or "") + "\n" + (n.get("content-desc") or "")
        if any(pat.search(line.strip()) for line in t.split("\n") if line.strip()):
            if has:
                sys.exit(0)
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds") or "")
            if m:
                a, b, c, d = map(int, m.groups())
                print((a + c) // 2, (b + d) // 2)
                return
    sys.exit(1)

main()
