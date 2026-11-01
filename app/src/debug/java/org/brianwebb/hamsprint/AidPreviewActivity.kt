package org.brianwebb.hamsprint

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import org.brianwebb.hamsprint.data.AidLoader
import org.brianwebb.hamsprint.data.PoolLoader
import org.brianwebb.hamsprint.data.ThemeMode
import org.brianwebb.hamsprint.domain.Card
import org.brianwebb.hamsprint.domain.CardKind
import org.brianwebb.hamsprint.domain.LicenseClass
import org.brianwebb.hamsprint.ui.aids.VisualAidCard
import org.brianwebb.hamsprint.ui.components.AnswerState
import org.brianwebb.hamsprint.ui.components.QuestionBody
import org.brianwebb.hamsprint.ui.theme.HamSprintTheme
import org.brianwebb.hamsprint.ui.theme.HamTheme
import org.brianwebb.hamsprint.ui.theme.Space

/** adb shell am start -n org.brianwebb.hamsprint.debug/org.brianwebb.hamsprint.AidPreviewActivity --es q G1A05 [--ez dark true] */
class AidPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val id = intent.getStringExtra("q") ?: "G1A05"
        val license = when (id.first()) { 'T' -> LicenseClass.TECHNICIAN; 'G' -> LicenseClass.GENERAL; else -> LicenseClass.EXTRA }
        val question = PoolLoader { assets.open(it) }.load(license).question(id) ?: error("no question $id")
        val aid = AidLoader { assets.open(it) }.load(license)[id]
        val card = Card(question, CardKind.PREVIEW, question.answers.indices.toList())
        val mode = if (intent.getBooleanExtra("dark", false)) ThemeMode.DARK else ThemeMode.LIGHT
        setContent {
            HamSprintTheme(mode) {
                Column(
                    Modifier.fillMaxSize().background(HamTheme.colors.background).statusBarsPadding()
                        .verticalScroll(rememberScrollState()).padding(Space.gutter),
                ) {
                    QuestionBody(
                        card,
                        stateFor = { if (it == card.correctPosition) AnswerState.REVEAL else AnswerState.DIMMED },
                        onSelect = null,
                        compactDimmed = aid != null,
                    )
                    aid?.let { VisualAidCard(it, Modifier.padding(top = Space.lg)) }
                }
            }
        }
    }
}
