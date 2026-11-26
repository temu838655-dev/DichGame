package com.dichgame.app

import org.junit.Assert.*
import org.junit.Test

class PromptTemplatesTest {
    @Test fun migrateCopyRenameDeleteAndReload() {
        val legacy = PromptTemplates.decode(null, "Giữ tên riêng")
        val copy = legacy.save(null, "Thân mật", "Giữ tên riêng\nXưng hô thân mật")
        val renamed = copy.save("Thân mật", "Đồng đội", copy.entries.getValue("Thân mật"))
        assertEquals(renamed, PromptTemplates.decode(renamed.encode(), "old"))
        assertEquals("Đồng đội", renamed.selected)
        val removed = renamed.remove("Đồng đội")
        assertEquals("", removed.selected)
        assertEquals(legacy.entries, removed.entries)
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateNameDoesNotReplaceOriginal() {
        PromptTemplates.decode(null, "").save(null, "A", "Một").save(null, "A", "Hai")
    }
}
