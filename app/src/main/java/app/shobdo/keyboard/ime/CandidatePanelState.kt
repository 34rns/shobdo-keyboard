package app.shobdo.keyboard.ime

/** The service owns this state; StripModel is only its drawing snapshot. */
class CandidatePanelState {
    var expanded = false
        private set
    fun toggle(hasCandidates: Boolean) { expanded = hasCandidates && !expanded }
    fun reset() { expanded = false }
}
