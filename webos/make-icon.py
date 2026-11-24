import struct, zlib, sys
def chunk(t, d):
    return struct.pack(">I",len(d))+t+d+struct.pack(">I",zlib.crc32(t+d)&0xffffffff)
n=80
rows=[]
for y in range(n):
    row=bytearray()
    for x in range(n):
        white=(15<=x<65 and (45<=y<51 or 57<=y<63))
        row.extend((255,255,255,255) if white else (24,42,68,255))
    rows.append(b'\0'+row)
with open(sys.argv[1],"wb") as f:
    f.write(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack(">IIBBBBB",n,n,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(rows)))+chunk(b'IEND',b''))
