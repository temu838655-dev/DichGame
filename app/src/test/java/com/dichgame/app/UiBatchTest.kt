package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiBatchTest {
    @Test fun matchesByIndexNotByOrder() {
        val r = UiBatch.parse("""```json
[{"i":2,"v":"Thoát"},{"i":0,"v":"Cài đặt"}]
```""", 3)
        assertEquals(listOf("Cài đặt", null, "Thoát"), r)
    }

    @Test fun rejectsDuplicatesOutOfRangeAndGarbage() {
        assertEquals(listOf<String?>(null, "b"), UiBatch.parse("""[{"i":0,"v":"a"},{"i":0,"v":"x"},{"i":1,"v":"b"},{"i":9,"v":"z"}]""", 2))
        assertEquals(listOf<String?>(null, null), UiBatch.parse("xin lỗi, tôi không dịch được", 2))
        assertEquals(listOf<String?>(null, null), UiBatch.parse("[{\"i\":0,", 2))
    }

    @Test fun softSuspicionIsUsableButWrongNumberIsNot() {
        val longer = TranslationGuard.check("Open the old gate", "Hãy mau mở cái cánh cổng cũ kỹ đã đóng từ rất lâu ở phía trước kia")
        assertEquals(TranslationGuard.Level.SUSPECT, longer.level)
        assertTrue(longer.usable)
        val number = TranslationGuard.check("You need 25 coins to pass", "Bạn cần 52 đồng xu để đi qua")
        assertFalse(number.usable)
    }
}
