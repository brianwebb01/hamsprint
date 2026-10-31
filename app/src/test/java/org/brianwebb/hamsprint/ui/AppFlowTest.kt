package org.brianwebb.hamsprint.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.brianwebb.hamsprint.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Drives the real app: onboarding → home → first training cards. */
@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `new user picks General, starts training, previews then recalls a question`() {
        rule.onNodeWithText("Get started").performClick()
        rule.onNodeWithText("Continue").performClick()
        rule.onNodeWithText("General").performClick()
        rule.onNodeWithText("Start studying").performClick()

        rule.waitUntil(5_000) { rule.onAllNodesWithTextExists("Let's get you licensed.") }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Start training"))
        rule.onNodeWithText("Start training").performClick()

        rule.waitUntil(5_000) { rule.onAllNodesWithTextExists("Got it") }
        rule.onNodeWithText("New", substring = false, ignoreCase = true).assertIsDisplayed()
        rule.onNodeWithText("Got it").performClick()

        // After one preview the next card is another new question (working memory has room).
        rule.waitUntil(5_000) { rule.onAllNodesWithTextExists("Got it") }
    }

    @Test
    fun `exam can be changed later from settings`() {
        rule.onNodeWithText("Get started").performClick()
        rule.onNodeWithText("Continue").performClick()
        rule.onNodeWithText("Technician").performClick()
        rule.onNodeWithText("Start studying").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTextExists("Let's get you licensed.") }

        rule.onNode(androidx.compose.ui.test.hasContentDescription("Settings")).performClick()
        rule.onNodeWithText("Amateur Extra").performScrollTo().performClick()
        rule.onNode(androidx.compose.ui.test.hasContentDescription("Back")).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTextExists("Amateur Extra") }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("50 questions · pass with 37"))
        rule.onNodeWithText("50 questions · pass with 37").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextExists(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty()
}
