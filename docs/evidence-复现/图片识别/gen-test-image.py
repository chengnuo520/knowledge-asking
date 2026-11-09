#!/usr/bin/env python3
"""生成 Vision 测试图 vision-test.png（纯标准库，无需 PIL）

图案：320x240 白底 + 左侧红色实心圆 + 右侧蓝色矩形 + 底部黑色横条。
模型若返回"红圆 / 蓝方 / 黑色横条"即说明图片内容被真正理解，而不是瞎编。
用法：python gen-test-image.py
"""
import struct
import zlib

W, H = 320, 240
rows = []
for y in range(H):
    row = []
    for x in range(W):
        r, g, b = 255, 255, 255
        if (x - 105) ** 2 + (y - 105) ** 2 <= 55 ** 2:      # 红色实心圆
            r, g, b = 220, 38, 38
        if 195 <= x <= 280 and 55 <= y <= 155:              # 蓝色矩形
            r, g, b = 22, 93, 255
        if 195 <= y <= 215:                                  # 底部黑色横条
            r, g, b = 29, 33, 41
        row.append((r, g, b))
    rows.append(row)

raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in rows)


def chunk(tag, data):
    body = tag + data
    return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)


ihdr = struct.pack(">IIBBBBB", W, H, 8, 2, 0, 0, 0)   # 8bit TrueColor
with open("vision-test.png", "wb") as f:
    f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
print("vision-test.png written:", W, "x", H)
