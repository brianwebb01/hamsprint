package org.brianwebb.hamsprint.ui.aids

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import org.brianwebb.hamsprint.domain.AntennaAid
import org.brianwebb.hamsprint.domain.AntennaType
import org.brianwebb.hamsprint.domain.BandPlanAid
import org.brianwebb.hamsprint.domain.CallSignAid
import org.brianwebb.hamsprint.domain.CodesAid
import org.brianwebb.hamsprint.domain.CombineAid
import org.brianwebb.hamsprint.domain.ComponentAid
import org.brianwebb.hamsprint.domain.DecibelAid
import org.brianwebb.hamsprint.domain.FactAid
import org.brianwebb.hamsprint.domain.FilterAid
import org.brianwebb.hamsprint.domain.IonosphereAid
import org.brianwebb.hamsprint.domain.LogicAid
import org.brianwebb.hamsprint.domain.MeterAid
import org.brianwebb.hamsprint.domain.ModesAid
import org.brianwebb.hamsprint.domain.OhmsAid
import org.brianwebb.hamsprint.domain.PhoneticAid
import org.brianwebb.hamsprint.domain.PowerAid
import org.brianwebb.hamsprint.domain.PrefixAid
import org.brianwebb.hamsprint.domain.ReactanceAid
import org.brianwebb.hamsprint.domain.ReactanceSolve
import org.brianwebb.hamsprint.domain.RepeaterAid
import org.brianwebb.hamsprint.domain.SpectrumAid
import org.brianwebb.hamsprint.domain.SwrAid
import org.brianwebb.hamsprint.domain.TimeConstantAid
import org.brianwebb.hamsprint.domain.TransformerAid
import org.brianwebb.hamsprint.domain.VisualAid
import org.brianwebb.hamsprint.domain.WaveformAid
import org.brianwebb.hamsprint.domain.WavelengthAid
import org.brianwebb.hamsprint.ui.components.HamIcons
import org.brianwebb.hamsprint.ui.theme.HamTheme
import org.brianwebb.hamsprint.ui.theme.Radius
import org.brianwebb.hamsprint.ui.theme.Space

/**
 * One question's visual aid: a titled card with a native diagram and an
 * optional memory hook. Screen readers get a text summary of the picture.
 */
@Composable
fun VisualAidCard(aid: VisualAid, modifier: Modifier = Modifier) {
    val c = HamTheme.colors
    val shape = RoundedCornerShape(Radius.md)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.separator, shape)
            .padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { heading() }) {
            Icon(HamIcons.Sparkle, null, tint = c.accent, modifier = Modifier.size(14.dp))
            Text(
                "VISUAL",
                style = MaterialTheme.typography.labelSmall,
                color = c.accent,
                modifier = Modifier.padding(start = Space.xs, end = Space.sm),
            )
            Text(title(aid), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
        }
        Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = describe(aid) }) {
            AidContent(aid)
        }
        aid.caption?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
    }
}

@Composable
private fun AidContent(aid: VisualAid) = when (aid) {
    is BandPlanAid -> BandPlanView(aid)
    is SpectrumAid -> SpectrumView(aid)
    is WavelengthAid -> WavelengthView(aid)
    is RepeaterAid -> RepeaterView(aid)
    is ModesAid -> ModesView(aid)
    is IonosphereAid -> IonosphereView(aid)
    is AntennaAid -> AntennaView(aid)
    is CallSignAid -> CallSignView(aid)
    is PhoneticAid -> PhoneticView(aid)
    is CodesAid -> CodesView(aid)
    is OhmsAid -> OhmsView(aid)
    is PowerAid -> PowerView(aid)
    is CombineAid -> CombineView(aid)
    is DecibelAid -> DecibelView(aid)
    is PrefixAid -> PrefixView(aid)
    is WaveformAid -> WaveformView(aid)
    is SwrAid -> SwrView(aid)
    is ReactanceAid -> ReactanceView(aid)
    is TimeConstantAid -> TimeConstantView(aid)
    is TransformerAid -> TransformerView(aid)
    is ComponentAid -> ComponentView(aid)
    is MeterAid -> MeterView(aid)
    is FilterAid -> FilterView(aid)
    is LogicAid -> LogicView(aid)
    is FactAid -> FactView(aid)
}

internal fun title(aid: VisualAid): String = when (aid) {
    is BandPlanAid -> "${aid.band.label} privileges"
    is SpectrumAid -> "Where the bands sit"
    is WavelengthAid -> "Frequency and wavelength"
    is RepeaterAid -> "${aid.repeater.band.label} repeaters"
    is ModesAid -> "Signal bandwidth"
    is IonosphereAid -> if (aid.night) "Ionosphere at night" else "Ionosphere by day"
    is AntennaAid -> when (aid.type) {
        AntennaType.DIPOLE -> "Half wave dipole"
        AntennaType.VERTICAL -> "Quarter wave vertical"
        AntennaType.YAGI -> "Yagi beam"
        AntennaType.LOOP -> "Small loop antenna"
    }
    is CallSignAid -> "Call sign anatomy"
    is PhoneticAid -> "Phonetic alphabet"
    is CodesAid -> aid.set.title
    is OhmsAid -> "Ohm's law"
    is PowerAid -> "Electrical power"
    is CombineAid -> "${aid.part.name.lowercase().replaceFirstChar { it.uppercase() }}s in ${if (aid.parallel) "parallel" else "series"}"
    is DecibelAid -> "Decibels"
    is PrefixAid -> "Metric prefixes"
    is WaveformAid -> "Sine wave values"
    is SwrAid -> "Standing wave ratio"
    is ReactanceAid -> when (aid.solve) {
        ReactanceSolve.XL -> "Inductive reactance"
        ReactanceSolve.XC -> "Capacitive reactance"
        ReactanceSolve.F0 -> "Resonant frequency"
    }
    is TimeConstantAid -> "RC time constant"
    is TransformerAid -> "Turns ratio"
    is ComponentAid -> "Schematic symbol"
    is MeterAid -> if (aid.ammeter) "Measuring current" else "Measuring voltage"
    is FilterAid -> "${aid.type.label} filter"
    is LogicAid -> "${aid.gate} gate"
    is FactAid -> "Key number"
}

/** Draws [text] with its top-left at [at], or centered on it when [center] is true. */
internal fun DrawScope.label(
    measurer: TextMeasurer,
    text: String,
    at: Offset,
    style: TextStyle,
    color: Color,
    center: Boolean = false,
) {
    val layout = measurer.measure(text, style)
    val origin = if (center) Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f) else at
    drawText(layout, color = color, topLeft = origin)
}
