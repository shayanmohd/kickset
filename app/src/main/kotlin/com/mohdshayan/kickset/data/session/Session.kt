package com.mohdshayan.kickset.data.session

import com.mohdshayan.kickset.core.jobs.CutInputs
import com.mohdshayan.kickset.core.jobs.OffsetInputs
import com.mohdshayan.kickset.core.jobs.TemplateInputs
import com.mohdshayan.kickset.core.template.WrapCurve
import kotlinx.coroutines.flow.MutableStateFlow

/** Hand-offs between screens that do not belong in navigation arguments. */
class Session {
    /** A saved calculation the user asked to reopen; the calculator applies it once and clears it. */
    val reopenOffsets = MutableStateFlow<OffsetInputs?>(null)
    val reopenCut = MutableStateFlow<CutInputs?>(null)
    val reopenTemplate = MutableStateFlow<TemplateInputs?>(null)

    /** The solved template the sheet preview prints. */
    val template = MutableStateFlow<SolvedTemplate?>(null)
}

data class SolvedTemplate(val title: String, val fileStem: String, val curve: WrapCurve, val hole: List<Pair<Double, Double>>?)
