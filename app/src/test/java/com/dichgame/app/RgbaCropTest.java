package com.dichgame.app;

import java.nio.ByteBuffer;
import org.junit.Test;
import static org.junit.Assert.*;

public class RgbaCropTest {
    private final RgbaCrop crop = new RgbaCrop();

    private static byte[] bytes(ByteBuffer buffer) {
        byte[] out = new byte[buffer.remaining()];
        buffer.get(out);
        return out;
    }

    @Test public void croppedPaddedRowsWithShortLastRow() {
        byte[] data = new byte[22];
        for (int i = 0; i < data.length; i++) data[i] = (byte) i;
        ByteBuffer frame = ByteBuffer.wrap(data);
        frame.position(2);
        assertArrayEquals(new byte[]{6,7,8,9,18,19,20,21}, bytes(crop.pixels(frame,12,1,0,1,2)));
        assertEquals(2, frame.position());
        assertArrayEquals(new byte[]{14,15,16,17}, bytes(crop.pixels(frame,12,0,1,1,1)));
    }

    @Test public void contiguousFrame() {
        byte[] data = {1,2,3,4,5,6,7,8};
        assertArrayEquals(data, bytes(crop.pixels(ByteBuffer.wrap(data),4,0,0,1,2)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingVisiblePixel() {
        crop.pixels(ByteBuffer.allocate(19),12,0,0,2,2);
    }
}
