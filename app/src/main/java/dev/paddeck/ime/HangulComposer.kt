package dev.paddeck.ime

/**
 * 두벌식 한글 조합기입니다.
 * 자모를 받아 완성형 음절로 합치고, 조합 중인 글자와 확정된 글자를 나눠 돌려줍니다.
 */
class HangulComposer {

    data class Edit(val commit: String, val composing: String)

    private val committed = StringBuilder()
    private var choseong = -1
    private var jungseong = -1
    private var jongseong = 0

    val composing: String get() = render()

    fun type(ch: Char): Edit {
        committed.setLength(0)
        val consonant = CHOSEONG.indexOf(ch)
        if (consonant >= 0) {
            typeConsonant(consonant)
            return Edit(committed.toString(), render())
        }
        val vowel = JUNGSEONG.indexOf(ch)
        if (vowel >= 0) {
            typeVowel(vowel)
            return Edit(committed.toString(), render())
        }
        flush()
        committed.append(ch)
        return Edit(committed.toString(), "")
    }

    fun commitPlain(text: String): Edit {
        committed.setLength(0)
        flush()
        committed.append(text)
        return Edit(committed.toString(), "")
    }

    fun backspace(): Edit? {
        committed.setLength(0)
        when {
            jongseong != 0 -> {
                jongseong = splitJongseong(jongseong)
                return Edit("", render())
            }

            jungseong >= 0 -> {
                jungseong = splitJungseong(jungseong)
                return Edit("", render())
            }

            choseong >= 0 -> {
                choseong = -1
                return Edit("", render())
            }
        }
        return null
    }

    fun reset(): Edit {
        committed.setLength(0)
        flush()
        return Edit(committed.toString(), "")
    }

    private fun typeConsonant(index: Int) {
        if (choseong >= 0 && jungseong >= 0) {
            val asJong = JONGSEONG.indexOf(CHOSEONG[index])
            if (jongseong == 0 && asJong > 0) {
                jongseong = asJong
                return
            }
            if (jongseong > 0) {
                val combined = combineJongseong(jongseong, asJong)
                if (combined > 0) {
                    jongseong = combined
                    return
                }
            }
            flush()
            choseong = index
            return
        }
        flush()
        choseong = index
    }

    private fun typeVowel(index: Int) {
        if (choseong >= 0 && jungseong >= 0) {
            if (jongseong == 0) {
                val combined = combineJungseong(jungseong, index)
                if (combined >= 0) {
                    jungseong = combined
                    return
                }
                flush()
                jungseong = index
                return
            }
            val nextChoseong = jongseongToChoseong(jongseong)
            flushKeepingJong(nextChoseong, index)
            return
        }
        if (choseong >= 0 && jungseong < 0) {
            jungseong = index
            return
        }
        if (choseong < 0 && jungseong >= 0) {
            val combined = combineJungseong(jungseong, index)
            if (combined >= 0) {
                jungseong = combined
                return
            }
        }
        flush()
        jungseong = index
    }

    private fun flushKeepingJong(nextChoseong: Int, nextVowel: Int) {
        val keepJong = jongseong
        jongseong = 0
        val syllable = render()
        if (syllable.isNotEmpty()) committed.append(syllable)
        jongseong = keepJong
        if (nextChoseong >= 0) {
            choseong = nextChoseong
            jungseong = nextVowel
            jongseong = 0
        } else {
            choseong = -1
            jungseong = nextVowel
            jongseong = 0
        }
    }

    private fun flush() {
        val syllable = render()
        if (syllable.isNotEmpty()) committed.append(syllable)
        choseong = -1
        jungseong = -1
        jongseong = 0
    }

    private fun render(): String {
        if (choseong >= 0 && jungseong >= 0) {
            val code = 0xAC00 + (choseong * 21 + jungseong) * 28 + jongseong
            return code.toChar().toString()
        }
        if (choseong >= 0) return CHOSEONG[choseong].toString()
        if (jungseong >= 0) return JUNGSEONG[jungseong].toString()
        return ""
    }

    private fun combineJungseong(a: Int, b: Int): Int {
        return VOWEL_COMBINE[a * 21 + b]
    }

    private fun combineJongseong(a: Int, b: Int): Int {
        if (b <= 0) return -1
        return JONG_COMBINE[a * 28 + b]
    }

    private fun splitJongseong(value: Int): Int = JONG_SPLIT[value]

    private fun splitJungseong(value: Int): Int {
        return VOWEL_SPLIT[value]
    }

    companion object {
        private const val CHOSEONG_TEXT = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
        private const val JUNGSEONG_TEXT = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
        private const val JONGSEONG_TEXT = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"

        private val CHOSEONG = CHOSEONG_TEXT.toList()
        private val JUNGSEONG = JUNGSEONG_TEXT.toList()
        private val JONGSEONG = JONGSEONG_TEXT.toList()

        private val VOWEL_COMBINE = IntArray(21 * 21) { -1 }.apply {
            fun set(a: Char, b: Char, result: Char) {
                this[JUNGSEONG.indexOf(a) * 21 + JUNGSEONG.indexOf(b)] = JUNGSEONG.indexOf(result)
            }
            set('ㅗ', 'ㅏ', 'ㅘ')
            set('ㅗ', 'ㅐ', 'ㅙ')
            set('ㅗ', 'ㅣ', 'ㅚ')
            set('ㅜ', 'ㅓ', 'ㅝ')
            set('ㅜ', 'ㅔ', 'ㅞ')
            set('ㅜ', 'ㅣ', 'ㅟ')
            set('ㅡ', 'ㅣ', 'ㅢ')
        }

        private val VOWEL_SPLIT = intArrayOf(
            -1, -1, -1, -1, -1, -1, -1, -1,
            -1, JUNGSEONG.indexOf('ㅗ'), JUNGSEONG.indexOf('ㅗ'), JUNGSEONG.indexOf('ㅗ'), -1,
            -1, JUNGSEONG.indexOf('ㅜ'), JUNGSEONG.indexOf('ㅜ'), JUNGSEONG.indexOf('ㅜ'), -1,
            -1, JUNGSEONG.indexOf('ㅡ'), -1,
        )

        private val JONG_COMBINE = IntArray(28 * 28).apply {
            fun set(a: Char, b: Char, result: Char) {
                this[JONGSEONG.indexOf(a) * 28 + JONGSEONG.indexOf(b)] = JONGSEONG.indexOf(result)
            }
            set('ㄱ', 'ㅅ', 'ㄳ')
            set('ㄴ', 'ㅈ', 'ㄵ')
            set('ㄴ', 'ㅎ', 'ㄶ')
            set('ㄹ', 'ㄱ', 'ㄺ')
            set('ㄹ', 'ㅁ', 'ㄻ')
            set('ㄹ', 'ㅂ', 'ㄼ')
            set('ㄹ', 'ㅅ', 'ㄽ')
            set('ㄹ', 'ㅌ', 'ㄾ')
            set('ㄹ', 'ㅍ', 'ㄿ')
            set('ㄹ', 'ㅎ', 'ㅀ')
            set('ㅂ', 'ㅅ', 'ㅄ')
        }

        private val JONG_SPLIT = IntArray(28).apply {
            fun set(a: Char, result: Char) {
                this[JONGSEONG.indexOf(a)] = JONGSEONG.indexOf(result)
            }
            set('ㄳ', 'ㄱ')
            set('ㄵ', 'ㄴ')
            set('ㄶ', 'ㄴ')
            set('ㄺ', 'ㄹ')
            set('ㄻ', 'ㄹ')
            set('ㄼ', 'ㄹ')
            set('ㄽ', 'ㄹ')
            set('ㄾ', 'ㄹ')
            set('ㄿ', 'ㄹ')
            set('ㅀ', 'ㄹ')
            set('ㅄ', 'ㅂ')
        }

        private fun jongseongToChoseong(value: Int): Int {
            if (value <= 0) return -1
            val jamo = JONGSEONG[value]
            val split = JONG_SPLIT[value]
            return if (split > 0) split else CHOSEONG.indexOf(jamo)
        }
    }
}
