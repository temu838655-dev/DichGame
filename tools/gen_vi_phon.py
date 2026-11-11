#!/usr/bin/env python3
"""
Tạo app/src/main/assets/vi_phon.txt: bảng "âm tiết tiếng Việt -> chuỗi phiên âm" cho giọng đọc offline (Piper).

Vì sao cần: giọng Piper cần chuỗi phiên âm kiểu eSpeak NG. Thư viện sherpa-onnx từ bản 2.0 không còn gói eSpeak,
nên app tự đổi chữ -> phiên âm bằng bảng này (xem ViPhon.kt). eSpeak NG (GPL-3.0) chỉ được dùng ở máy dựng bảng,
KHÔNG có mã eSpeak nào nằm trong app.

Cách chạy (cần eSpeak NG đã build, có file thực thi do espeak_phonemes.c biên dịch ra):
  python3 tools/gen_vi_phon.py <espeak_phonemes> <thư mục espeak-ng-data> <tokens.txt của giọng> <đầu ra>
"""
import subprocess, sys, unicodedata, itertools

helper, data_dir, tokens_path, out_path = sys.argv[1:5]

TONES = {"": "", "s": "́", "f": "̀", "r": "̉", "x": "̃", "j": "̣"}
MARKED = set("ăâêôơư")

INITIALS = ["", "b", "c", "ch", "d", "đ", "g", "gh", "gi", "h", "k", "kh", "l", "m", "n", "ng", "ngh", "nh",
            "p", "ph", "qu", "r", "s", "t", "th", "tr", "v", "x"]
# nguyên âm + phụ âm cuối (nhóm "đóng": có thể có đuôi c ch m n ng nh p t)
NUCLEI = ["a", "ă", "â", "e", "ê", "i", "y", "o", "ô", "ơ", "u", "ư", "iê", "yê", "uô", "ươ",
          "oa", "oă", "oe", "uê", "uy", "uâ", "uyê", "uơ"]
CODAS = ["", "c", "ch", "m", "n", "ng", "nh", "p", "t"]
STOP = {"c", "ch", "p", "t"}
# vần kết thúc bằng bán nguyên âm / vần mở đặc biệt
GLIDE_RIMES = ["ai", "ay", "ao", "au", "âu", "ây", "eo", "êu", "iu", "oi", "ôi", "ơi", "ui", "ưi", "ưu",
               "iêu", "yêu", "oai", "oay", "oeo", "uây", "uôi", "ươi", "ươu", "uai", "uay", "uyu", "uya",
               "ia", "ua", "ưa", "yêu", "uya"]
# nguyên âm không đi với phụ âm cuối nào ở dạng đóng
NO_OPEN = {"ă", "â", "iê", "yê", "uô", "ươ", "oă", "uâ", "uyê"}
NEEDS_CODA = NO_OPEN


def tone_index(nuc, coda, old_style=False):
    marked = [i for i, ch in enumerate(nuc) if ch in MARKED]
    if marked:
        return marked[-1]
    n = len(nuc)
    if n == 1:
        return 0
    if n == 2:
        if coda != "":
            return 1
        if nuc in ("oa", "oe", "uy"):
            return 1 if old_style else 0
        return 0
    return 1


def with_tone(nuc, idx, tone):
    chars = list(nuc)
    chars[idx] = chars[idx] + TONES[tone]
    return unicodedata.normalize("NFC", "".join(chars))


def ok_orthography(ini, nuc):
    first = nuc[0]
    front = first in "eêi" or nuc.startswith(("iê", "yê", "y")) or first == "y"
    if ini in ("c",) and front:
        return False
    if ini == "k" and not front:
        return False
    if ini in ("gh", "ngh") and not (first in "eêi" or nuc.startswith("iê")):
        return False
    if ini in ("g", "ng") and (first in "eêi" or nuc.startswith("iê")):
        return False
    if ini == "gi" and (first == "i" or nuc.startswith(("iê", "yê"))):
        return False
    if ini == "qu" and (first in "uo" or nuc.startswith("uy")):
        return False
    return True


def tones_for(coda):
    return ["s", "j"] if coda in STOP else ["", "f", "s", "r", "x", "j"]


cands = set()
for ini in INITIALS:
    for nuc in NUCLEI:
        if not ok_orthography(ini, nuc):
            continue
        for coda in CODAS:
            if coda == "" and nuc in NEEDS_CODA:
                continue
            for t in tones_for(coda):
                for old in ((False, True) if (coda == "" and nuc in ("oa", "oe", "uy")) else (False,)):
                    idx = tone_index(nuc, coda, old)
                    cands.add(ini + with_tone(nuc, idx, t) + coda)
    for r in GLIDE_RIMES:
        if not ok_orthography(ini, r):
            continue
        for t in tones_for(""):
            idx = tone_index(r, "", False)
            cands.add(ini + with_tone(r, idx, t))
# "gì", "gìn", "gỉ"...: viết là g + i nhưng đọc như gi + i
for coda in ("", "n", "nh", "ng"):
    for t in tones_for(coda):
        cands.add("g" + with_tone("i", 0, t) + coda)
cands = sorted(cands)
print("số âm tiết ứng viên:", len(cands), file=sys.stderr)

tokens = set()
for line in open(tokens_path, encoding="utf-8"):
    line = line.rstrip("\n")
    if not line:
        continue
    sym = line.rsplit(" ", 1)[0] if " " in line.strip() and line.strip() != "" else line
    # dòng dạng "<ký hiệu> <số>"; riêng dấu cách là dòng "  3"
    parts = line.split(" ")
    sym = " " if line.startswith("  ") else parts[0]
    tokens.add(sym)


def run(lines):
    p = subprocess.run([helper, data_dir], input="\n".join(lines) + "\n", capture_output=True, text=True, encoding="utf-8")
    out = p.stdout.split("\n")
    if out and out[-1] == "":
        out.pop()
    if len(out) != len(lines):
        raise SystemExit("số dòng ra không khớp: %d != %d" % (len(out), len(lines)))
    return out


def parse(line):
    """'[ph]{,} [ph2]{.S}' -> chuỗi phiên âm chỉ gồm 1 mệnh đề (bỏ dấu câu)."""
    chunks = []
    i = 0
    while i < len(line):
        a = line.find("[", i)
        if a < 0:
            break
        b = line.find("]{", a)
        chunks.append(line[a + 1:b])
        i = line.find("}", b) + 1
    return chunks


table = {}
bad = 0
outs = run(cands)
for syl, o in zip(cands, outs):
    ch = parse(o)
    if len(ch) != 1 or "(" in ch[0] or not ch[0].strip():
        bad += 1
        continue
    ph = unicodedata.normalize("NFD", ch[0].strip())
    if " " in ph or any(c not in tokens for c in ph):
        bad += 1
        continue
    table[syl] = ph
print("hợp lệ:", len(table), "bỏ:", bad, file=sys.stderr)

# Từ không nhấn (eSpeak đánh dấu $u): lấy dạng khi đứng giữa câu.
UNSTRESSED = ["bị", "bởi", "các", "cái", "chẳng", "cho", "chưa", "con", "còn", "của", "dẫu", "dù", "đang", "đã", "đến",
              "để", "được", "hoặc", "khi", "khoảng", "không", "là", "lúc", "mà", "mọi", "mỗi", "nên", "nếu", "như",
              "nhưng", "những", "phải", "sẽ", "thì", "tới", "từ", "và", "vẫn", "vì", "với"]
ctx = run(["tôi %s nhà" % w for w in UNSTRESSED])
n_ctx = 0
for w, o in zip(UNSTRESSED, ctx):
    ch = parse(o)
    if len(ch) != 1:
        continue
    words = ch[0].split(" ")
    if len(words) == 3:
        ph = unicodedata.normalize("NFD", words[1].strip())
        if all(c in tokens for c in ph):
            table[w] = ph
            n_ctx += 1
print("từ không nhấn:", n_ctx, file=sys.stderr)

# Tên chữ cái (đọc từ viết tắt, tên riêng lạ)
letters = "abcdefghijklmnopqrstuvwxyzđ"
lo = run(list(letters))
for c, o in zip(letters, lo):
    ch = parse(o)
    if len(ch) == 1 and ch[0].strip():
        table["#" + c] = unicodedata.normalize("NFD", ch[0].strip())
# chữ số đọc từng số một (dự phòng)
print("tên chữ cái:", sum(1 for k in table if k.startswith("#")), file=sys.stderr)

with open(out_path, "w", encoding="utf-8", newline="\n") as f:
    for k in sorted(table):
        f.write(k + "\t" + table[k] + "\n")
print("đã ghi", len(table), "dòng ->", out_path, file=sys.stderr)
