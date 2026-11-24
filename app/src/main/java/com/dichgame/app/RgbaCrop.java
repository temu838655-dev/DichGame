package com.dichgame.app;

import java.nio.ByteBuffer;

/** Packs only the requested pixels. The ImageReader may omit padding after its last row. */
final class RgbaCrop {
    private ByteBuffer scratch;

    ByteBuffer pixels(ByteBuffer source, int stride, int x, int y, int width, int height) {
        if (x < 0 || y < 0 || width <= 0 || height <= 0 || ((long) x + width) * 4 > stride) {
            throw new IllegalArgumentException("Invalid RGBA rectangle");
        }
        int rowBytes = Math.multiplyExact(width, 4);
        int size = Math.multiplyExact(height, rowBytes);
        long first = source.position() + (long) y * stride + (long) x * 4;
        long end = first + (long) (height - 1) * stride + rowBytes;
        if (end > source.limit()) throw new IllegalArgumentException("Incomplete RGBA frame");
        ByteBuffer rows = source.duplicate();
        if (height == 1 || rowBytes == stride) {
            rows.position((int) first);
            rows.limit((int) end);
            return rows.slice();
        }
        if (scratch == null || scratch.capacity() < size) scratch = ByteBuffer.allocateDirect(size);
        scratch.clear();
        for (int row = 0; row < height; row++) {
            int start = (int) (first + (long) row * stride);
            rows.limit(start + rowBytes);
            rows.position(start);
            scratch.put(rows);
        }
        scratch.flip();
        return scratch;
    }
}
