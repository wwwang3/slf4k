## Working Agreements

### Design principles

- Avoid excessive defensive programming and design

### Secrets and safety

- Never read any credential files
- Disable the output of environment variable values via `echo` or other similar commands

### Git operations

- `git push` are prohibited unless the user explicitly agrees

### Sub-agents

- **When using sub-agents, the number of subagents should not exceed 3**
- **Sub-agents are prohibited from creating sub-agents**

### Build commands

- In Linux platform, Run the Gradle command using bash, such as `bash gradlew build`

### Comment and document style

- Use English half-width punctuation marks throughout the document or comments
- Use American English when writing English comments and documents
- Use the multiline block style (`/** ... */` with **line breaks** and **leading asterisks**) on class/interface/method/field comment
    - **not allowed**: `/** ... */`
    - **allowed**:

      ```kotlin
      /**
      * ...
      */
      ```

    - Scope: every KDoc block attached to a class, interface, object, method, field, or property must use the multiline form, including one-sentence comments
    - A single-line `/** ... */` is only acceptable when it is not the whole comment, e.g. an `@param` / `@return` clause inside an otherwise multiline block
    - This rule is enforced by the Gradle task `checkKdocFormat` (`buildSrc/`), which `check` depends on and which reports each violation as `path:line:column: message`; the gate also requires a leading `*` on every continuation line of a block, and there is no autocorrect, so rewrite the block by hand
- Do not reference review documents or their item numbers (such as `review-1`, `P2-1`, or `fix P3-11`) in core code comments; a comment must stay meaningful without them

### Rule gates

Which rules a task enforces, and which ones only a reviewer can:

- `checkKdocFormat` enforces the **form** of a KDoc block; whether a public declaration needs one at all is not automated
- `explicitApiWarning()` reports a missing explicit visibility as a warning, not an error, so "every public declaration states its visibility" is a review rule
- Message ownership (the delivery point on `Connection`, `MessageContext`, and `MessageHandler`), the handler lifecycle pairing (`onActive` / `onInactive`), and the CI-versus-repository synchronization have no gate either: review them by hand

## Test Case Writing Constraints

when need to write test, load: [test-write-constraints](.agents/rules/test-write-constraints.md)

## CI and Pipeline Query

when need to query pipeline status, build logs, PR check results, load: [cnb-tools-constraints](.agents/rules/cnb-tools-constraints.md)
