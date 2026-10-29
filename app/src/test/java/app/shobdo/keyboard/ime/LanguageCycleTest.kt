package app.shobdo.keyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageCycleTest {

    @Test
    fun `all three enabled cycles english to bangla to banglish and back`() {
        val enabled = LanguageCycle.enabled(english = true, bangla = true, banglish = true)
        assertEquals(LanguageCycle.ORDER, enabled)
        assertEquals(Language.BANGLA, LanguageCycle.next(Language.ENGLISH, enabled))
        assertEquals(Language.BANGLISH, LanguageCycle.next(Language.BANGLA, enabled))
        assertEquals(Language.ENGLISH, LanguageCycle.next(Language.BANGLISH, enabled))
    }

    @Test
    fun `two enabled languages skip the disabled one`() {
        val noBangla = LanguageCycle.enabled(english = true, bangla = false, banglish = true)
        assertEquals(Language.BANGLISH, LanguageCycle.next(Language.ENGLISH, noBangla))
        assertEquals(Language.ENGLISH, LanguageCycle.next(Language.BANGLISH, noBangla))

        val banglaAndBanglish = LanguageCycle.enabled(english = false, bangla = true, banglish = true)
        assertEquals(Language.BANGLISH, LanguageCycle.next(Language.BANGLA, banglaAndBanglish))
        assertEquals(Language.BANGLA, LanguageCycle.next(Language.BANGLISH, banglaAndBanglish))
    }

    @Test
    fun `one enabled language keeps landing on itself`() {
        val onlyBangla = LanguageCycle.enabled(english = false, bangla = true, banglish = false)
        assertEquals(listOf(Language.BANGLA), onlyBangla)
        assertEquals(Language.BANGLA, LanguageCycle.next(Language.BANGLA, onlyBangla))
        assertEquals(Language.BANGLA, LanguageCycle.next(Language.ENGLISH, onlyBangla))
    }

    @Test
    fun `the last language cannot be disabled`() {
        val only = listOf(Language.ENGLISH)
        assertFalse(LanguageCycle.canDisable(only, Language.ENGLISH))

        val two = listOf(Language.ENGLISH, Language.BANGLISH)
        assertTrue("a language that is already off can stay off", LanguageCycle.canDisable(two, Language.BANGLA))
        assertTrue(LanguageCycle.canDisable(two, Language.ENGLISH))
    }

    @Test
    fun `a disabled current language falls forward to the next enabled one`() {
        val enabled = listOf(Language.ENGLISH, Language.BANGLISH)
        assertEquals(Language.BANGLISH, LanguageCycle.next(Language.BANGLA, enabled))
        assertEquals(Language.ENGLISH, LanguageCycle.next(Language.BANGLISH, enabled))
    }

    @Test
    fun `resolve prefers a remembered language that is still enabled`() {
        val enabled = listOf(Language.BANGLA, Language.BANGLISH)
        assertEquals(Language.BANGLISH, LanguageCycle.resolve(Language.BANGLISH, enabled))
        assertEquals(Language.BANGLA, LanguageCycle.resolve(Language.ENGLISH, enabled))
        assertEquals(Language.BANGLA, LanguageCycle.resolve(null, enabled))
        assertEquals(Language.ENGLISH, LanguageCycle.resolve(Language.BANGLA, emptyList()))
    }

    @Test
    fun `language ids round trip for storage`() {
        for (language in Language.entries) {
            assertEquals(language, Language.fromId(language.id))
        }
        assertEquals(null, Language.fromId("klingon"))
    }
}
