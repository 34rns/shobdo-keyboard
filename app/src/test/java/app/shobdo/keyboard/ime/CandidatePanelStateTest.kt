package app.shobdo.keyboard.ime

import org.junit.Assert.*
import org.junit.Test

class CandidatePanelStateTest {
    @Test fun `open close open and candidate commit reset`() {
        val panel = CandidatePanelState()
        repeat(3) { panel.toggle(true); assertEquals(it % 2 == 0, panel.expanded) }
        panel.reset()
        assertFalse(panel.expanded)
    }
    @Test fun `language change and empty buffer reset`() {
        val panel = CandidatePanelState()
        panel.toggle(true)
        panel.reset()
        assertFalse(panel.expanded)
        panel.toggle(false)
        assertFalse(panel.expanded)
    }
}
