package com.dichgame.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogueSplitterTest {
    private fun line(t: String, l: Int, top: Int, r: Int, b: Int) = OcrLine(t, Box(l, top, r, b))

    @Test fun castNameAboveDialogueBecomesSpeaker() {
        val book = SpeakerBook(listOf("Geralt", "Yennefer"))
        val r = DialogueSplitter.split(
            listOf(line("Geralt", 100, 800, 220, 830), line("I hate portals.", 100, 840, 600, 870)),
            cjk = false, book = book
        )
        assertEquals(1, r.size)
        assertEquals("Geralt", r[0].speaker)
        assertEquals("I hate portals.", r[0].text)
    }

    @Test fun twoSpeakersAreNeverMerged() {
        val book = SpeakerBook(listOf("Geralt", "Yennefer"))
        val r = DialogueSplitter.split(
            listOf(
                line("Geralt", 100, 700, 220, 730), line("Not again.", 100, 735, 500, 765),
                line("Yennefer", 100, 780, 260, 810), line("Hold still.", 100, 815, 500, 845),
            ), cjk = false, book = book
        )
        assertEquals(2, r.size)
        assertEquals("Geralt", r[0].speaker); assertEquals("Not again.", r[0].text)
        assertEquals("Yennefer", r[1].speaker); assertEquals("Hold still.", r[1].text)
    }

    @Test fun unknownTitleCaseWordIsKeptAsText() {
        // "Hello" đứng trên một câu khác: không được nuốt thành tên người nói
        val r = DialogueSplitter.split(
            listOf(line("Hello", 100, 800, 200, 830), line("How are you today?", 100, 840, 600, 870)), cjk = false
        )
        assertTrue(r.any { it.text.contains("Hello") } || r.joinToString { it.text }.contains("Hello"))
        assertTrue(r.all { it.speaker == null })
    }

    @Test fun unknownNameBecomesSpeakerAfterSeenOnTwoDifferentLines() {
        val book = SpeakerBook()
        fun frame(d: String) = listOf(line("Malek", 100, 800, 220, 830), line(d, 100, 840, 700, 870))
        val a = DialogueSplitter.split(frame("The road is closed."), false, book)
        assertNull(a.firstOrNull { it.text.contains("road") }?.speaker)
        DialogueSplitter.split(frame("We should turn back."), false, book)
        val c = DialogueSplitter.split(frame("Follow me."), false, book)
        assertEquals(1, c.size)
        assertEquals("Malek", c[0].speaker)
        assertEquals("Follow me.", c[0].text)
    }

    @Test fun repeatedScansOfSameFrameDoNotConfirmAName() {
        val book = SpeakerBook()
        val f = listOf(line("Malek", 100, 800, 220, 830), line("The road is closed.", 100, 840, 700, 870))
        repeat(10) { DialogueSplitter.split(f, false, book) }
        assertFalse(book.confirmed("Malek"))
    }

    @Test fun colonLabelInsideBlockIsKeptInlineUntilConfirmed() {
        val r = DialogueSplitter.split(listOf(OcrLine("Objective: Find the key", Box(0, 0, 400, 30))), false)
        assertEquals(1, r.size)
        assertNull(r[0].speaker)
        assertEquals("Objective: Find the key", r[0].text)
    }

    @Test fun clearCharacterLabelGetsVoiceIdentityOnFirstScan() {
        val r = DialogueSplitter.split(
            listOf(OcrLine("Lucia: Let's go, Jason!", Box(0, 0, 420, 35))), false
        )
        assertEquals("Lucia", r.single().speaker)
        assertEquals("Let's go, Jason!", r.single().text)
    }

    @Test fun questAndMissionLabelsAreNotMistakenForSpeakers() {
        for (label in listOf("Objective", "Quest", "Mission", "Nhiệm vụ")) {
            val line = "$label: Find the key"
            val r = DialogueSplitter.split(listOf(OcrLine(line, Box(0, 0, 420, 35))), false)
            assertNull(r.single().speaker)
            assertEquals(line, r.single().text)
        }
    }

    @Test fun storedRecognizedNameCanBeUsedAsKnownSpeaker() {
        val r = DialogueSplitter.split(
            listOf(line("Lucia", 100, 800, 220, 830), line("Follow me.", 100, 840, 700, 870)),
            false, SpeakerBook(listOf("Lucia"))
        )
        assertEquals("Lucia", r.single().speaker)
    }

    @Test fun colonLabelWithCastNameIsExtracted() {
        val r = DialogueSplitter.split(listOf(OcrLine("Geralt: Hmm.", Box(0, 0, 400, 30))), false, SpeakerBook(listOf("Geralt")))
        assertEquals("Geralt", r[0].speaker)
        assertEquals("Hmm.", r[0].text)
    }

    @Test fun cjkBracketNameIsExtracted() {
        val r = DialogueSplitter.split(listOf(OcrLine("【リン】こんにちは", Box(0, 0, 400, 30))), cjk = true)
        assertEquals("リン", r[0].speaker)
        assertEquals("こんにちは", r[0].text)
    }

    @Test fun threeSubtitleLinesInOneOcrBlockAreAllKeptInReadingOrder() {
        val r = DialogueSplitter.split(
            listOf(OcrLine("We have to get out\nbefore they close\nall the exits.", Box(100, 800, 700, 890))),
            cjk = false
        )
        assertEquals(1, r.size)
        assertEquals("We have to get out before they close all the exits.", r.single().text)
    }

    @Test fun wrappedLinesOfOneBoxAreJoined() {
        val r = DialogueSplitter.split(
            listOf(line("I never thought I would see", 100, 800, 700, 830), line("this place again.", 100, 832, 400, 862)),
            cjk = false
        )
        assertEquals(1, r.size)
        assertEquals("I never thought I would see this place again.", r[0].text)
    }

    @Test fun farApartBlocksStaySeparate() {
        val r = DialogueSplitter.split(
            listOf(line("Press X to continue", 100, 100, 600, 130), line("The wind howls across the valley.", 100, 800, 800, 830)),
            cjk = false
        )
        assertEquals(2, r.size)
    }

    @Test fun hyphenatedWrapIsRejoined() {
        assertEquals("the infor" + "mation is here", DialogueSplitter.join(listOf("the infor-", "mation is here"), false))
        assertEquals("今日は天気がいい", DialogueSplitter.join(listOf("今日は", "天気がいい"), true))
    }

    @Test fun blocksWithoutBoxesAreSeparateUtterances() {
        val r = DialogueSplitter.split(listOf(OcrLine("First line here.", null), OcrLine("Second line there.", null)), false)
        assertEquals(2, r.size)
    }

    @Test fun missingGeometryDoesNotGuessThatCastNameBelongsToNextBlock() {
        val r = DialogueSplitter.split(
            listOf(OcrLine("Geralt", null), OcrLine("I hate portals.", null)),
            false, SpeakerBook(listOf("Geralt"))
        )
        assertEquals(2, r.size)
        assertNull(r[0].speaker)
        assertNull(r[1].speaker)
        assertEquals("Geralt", r[0].text)
        assertEquals("I hate portals.", r[1].text)
    }

    @Test fun explicitColonSpeakerLabelCanPairWithoutGeometry() {
        val r = DialogueSplitter.split(
            listOf(OcrLine("Geralt:", null), OcrLine("I hate portals.", null)),
            false, SpeakerBook(listOf("Geralt"))
        )
        assertEquals(1, r.size)
        assertEquals("Geralt", r.single().speaker)
        assertEquals("I hate portals.", r.single().text)
    }

    @Test fun nameThenDialogueInOneBlock() {
        val r = DialogueSplitter.split(listOf(OcrLine("GERALT:\nI hate portals.", Box(0, 0, 400, 60))), false)
        assertEquals("GERALT", r[0].speaker)
        assertEquals("I hate portals.", r[0].text)
    }

    @Test fun geminiVoiceListsSeparateMaleAndFemale() {
        val male = Prefs.MALE_AI_VOICES
        val female = Prefs.FEMALE_AI_VOICES
        assertTrue(male.isNotEmpty())
        assertTrue(female.isNotEmpty())
        assertTrue(male.all { "· nam ·" in it.second })
        assertTrue(female.all { "· nữ ·" in it.second })
        assertEquals(Prefs.AI_VOICES.map { it.first }.toSet(),
            (male + female).map { it.first }.toSet())
        assertTrue(male.none { m -> female.any { it.first == m.first } })
    }

    @Test fun nameShapeRules() {
        assertTrue(DialogueSplitter.nameShape("Geralt of Rivia", false))
        assertTrue(DialogueSplitter.nameShape("ELENA", false))
        assertFalse(DialogueSplitter.nameShape("Where are you?", false))
        assertFalse(DialogueSplitter.nameShape("Yes", false))
        assertFalse(DialogueSplitter.nameShape("This is a very long sentence indeed", false))
        assertTrue(DialogueSplitter.nameShape("リン", true))
        assertFalse(DialogueSplitter.nameShape("はい", true))
    }

    @Test fun signatureIgnoresCaseAndPunctuation() {
        val a = listOf(Utterance("Geralt", "Not again."))
        val b = listOf(Utterance("Geralt", "not again"))
        assertEquals(DialogueSplitter.signature(a), DialogueSplitter.signature(b))
    }
}
