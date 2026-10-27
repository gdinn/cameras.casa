# Development guidelines
This document contains critical information about working with this codebase. Follow these guidelines precisely.

## Architecture and stack
- MVVM
- Jetpack Compose
- Gradle kts with toml

## Code Language
- Use en-us to write and comment code

## Requirements when changing code
- Follow [architecture and stack](#architecture-and-stack)
- Follow [code language](#code-language)
- Document [architecture.md](./docs/architecture.md) to reflect changes being made
## Progress notes
- [progress](./progress/) holds the specifications and AI notes (research, plans, decisions and their outcomes) of the feature in progress on the current branch. See its [README](./progress/README.md)
- Write new research reports and action plans there, named after the feature and what they hold (e.g. `e2e_level_a_plan.md`)
- When a step of a plan is done, record its outcome and any deviation from the plan in the same file
- Whatever must outlive the branch goes to [architecture.md](./docs/architecture.md) or next to the code
- Delete the branch's files from `progress/` before merging. On `main`, the README is the only file there

## Instrumented and E2E tests
- Location: `app/src/androidTest/java/com/gdisys/cameras/`, with packages mirroring `main`. Shared helpers live in `testing/` (`setThemedContent`, `string`)
- Screen tests use Compose UI Test (`createComposeRule`): render the `*Screen` inside `setThemedContent` with a hand-written `uiState`, then assert what it shows and which callback each interaction fires. No Hilt, navigation or fakes at this level
- Find nodes by test tag. Tags are `const val`s in the feature's `*TestTags` object, in `main` next to the components. Any node a test interacts with or asserts on gets a tag there
- Use text only when the test is about the text, and resolve it with `string(R.string.x)`, never a literal: the suite must pass with the device in `en` and in `pt-BR`
- Expose state through semantics (e.g. `stateDescription`) instead of asserting icons or colors
- Name tests `<subject><behavior>` in camelCase. Backtick names with spaces are not allowed in `androidTest`, because `minSdk` is below 30. One behavior per test, and no `Thread.sleep`
- Inside a `LazyColumn`, scroll to the node with `performScrollToNode` before using it
- Text fields under a fixed `uiState`: touch one field per test, since a field that loses focus syncs back to the state and fires an extra `onValueChange`
- When changing a `*Screen` or its components, add or update its tests and run `./gradlew :app:connectedDebugAndroidTest` on an emulator with animations off
- Instrumented tests stay outside JaCoCo, which measures unit tests only
- Never print a real QR payload (WireGuard keys) in logs or assertions. Tests that need the real VPN run only by hand, on the machine of whoever holds the QR, never on hosted CI
