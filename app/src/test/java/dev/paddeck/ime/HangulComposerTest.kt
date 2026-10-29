package dev.paddeck.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class HangulComposerTest {

    private val composer = HangulComposer()

    @Test
    fun combinesSimpleSyllable() {
        composer.type('ㅎ')
        composer.type('ㅏ')
        val edit = composer.type('ㄴ')
        assertEquals("한", edit.composing)
        assertEquals("", edit.commit)
    }

    @Test
    fun movesTrailingConsonantToNextSyllable() {
        val first = composer.type('ㄱ')
        assertEquals("ㄱ", first.composing)
        composer.type('ㅏ')
        composer.type('ㄴ')
        val next = composer.type('ㅏ')
        assertEquals("가", next.commit)
        assertEquals("나", next.composing)
    }

    @Test
    fun combinesCompoundVowel() {
        composer.type('ㄱ')
        composer.type('ㅗ')
        val edit = composer.type('ㅏ')
        assertEquals("과", edit.composing)
    }

    @Test
    fun combinesCompoundFinal() {
        composer.type('ㄷ')
        composer.type('ㅏ')
        composer.type('ㄹ')
        val edit = composer.type('ㄱ')
        assertEquals("닭", edit.composing)
    }

    @Test
    fun backspacePeelsFinalConsonant() {
        composer.type('ㄷ')
        composer.type('ㅏ')
        composer.type('ㄹ')
        val edit = composer.backspace()
        assertEquals("다", edit?.composing)
    }

    @Test
    fun backspaceReturnsNullWhenNothingComposed() {
        assertEquals(null, composer.backspace())
    }

    @Test
    fun nonHangulFlushesComposition() {
        composer.type('ㄱ')
        composer.type('ㅏ')
        val edit = composer.type(' ')
        assertEquals("가 ", edit.commit)
        assertEquals("", edit.composing)
    }

    @Test
    fun typesFullSentence() {
        val text = StringBuilder()
        listOf('ㅎ', 'ㅏ', 'ㄴ', 'ㄱ', 'ㅡ', 'ㄹ').forEach { ch ->
            val edit = composer.type(ch)
            text.append(edit.commit)
        }
        text.append(composer.composing)
        assertEquals("한글", text.toString())
    }
}
