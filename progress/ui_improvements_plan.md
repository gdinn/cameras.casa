# UI improvements — plan

## 1. Scrollable Config screen

**Problem.** On short screens the four steps plus the "Back to Home" button do not fit, and the
button is pushed off screen with no way to reach it.

**Plan.** In `ConfigScreen`, wrap the content in a `BoxWithConstraints` and give the column
`verticalScroll` plus `heightIn(min = maxHeight)`. On tall screens `Arrangement.SpaceBetween` still
pins the button to the bottom; on short ones the column grows past the viewport and scrolls.
Tests that click the Home button scroll to it first (`performScrollTo`), since a click on an
off-screen node would miss.

**Outcome.** Done as planned in `ConfigScreen.kt`; `ConfigScreenTest` and `ConfigRobot.tapHome`
scroll to the button before clicking. Documented in `docs/architecture.md` §5.2. App and instrumented test sources
compile. Instrumented tests (`ConfigScreenTest`) still to be run on a device.

## 2. Spacing before the step's status icon

**Problem.** In `ConfigButton`, the text column touches the round status icon when the subtitle
fills the line (`ConfigButtonPreviewDoneFullText`).

**Plan.** Add an 8 dp `Spacer` between the text column and the status icon, mirroring the one
between the leading icon and the text.

**Outcome.** Done as planned in `ConfigurationStepButton.kt`. Check visually with the
`ConfigButtonPreviewDoneFullText` preview.
