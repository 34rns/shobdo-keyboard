package app.shobdo.keyboard.ime

import app.shobdo.keyboard.banglish.BanglishEngine

internal object FallbackBanglish {
    val engine by lazy { BanglishEngine.fromJson(FALLBACK_RULES) }
    private const val FALLBACK_RULES =
            """{"behavior":{"hasanta":"্","oAfterConsonant":{"trailing":"ো","medial":""}},
                "rules":{"consonants":[{"roman":"k","bangla":"ক"}],
                "vowelStandalone":[{"roman":"a","bangla":"আ"}],
                "vowelKar":[],"jPhala":[],"signs":[],"digits":[]}}"""
}
