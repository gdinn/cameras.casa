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
