# SDK architecture

All seven SDKs have the same small modules, so a change reads the same in every language.

| Module | Job | Touch it when |
|---|---|---|
| `operations.json` (generated) | Method, path and parameters of every operation | Never by hand: run `bash sdk/tools/update_api.sh` |
| Naming | Folds snake_case, camelCase and kebab-case to one key | Almost never |
| Registry (+ Operation) | Loads the JSON, resolves `group.name` in any spelling | The registry format changes |
| RequestBuilder | Puts each parameter in path, query or body, encodes queries | The API changes how parameters are sent |
| RetryPolicy | Which statuses and methods are retried, and the delay | Retry rules change |
| Transport | One HTTP round trip with the language's HTTP library | Switching the HTTP library |
| ResponseParser | Unwraps `data`, maps HTTP statuses to error classes | The envelope or error format changes |
| Group | `client.seo.keyword_metrics(...)` sugar | Never |
| Client | Wires the modules together and runs the retry loop | New client-level option |

## Updating the API

Endpoints added, removed or renamed: `bash sdk/tools/update_api.sh`, run the tests, bump the version. No code changes, because operations are data.

A new response or error field: edit ResponseParser (and the exception class if it exposes the field) in each language.

A new auth scheme or header: edit the headers function in Client.

## Conventions

- One class per file, with imports instead of fully qualified names.
- Comments are one short line. Names carry the meaning.
- The public surface is `Client`, `Group`, the error classes and `Operation`; everything else is internal.
- Every suite runs the same scenarios against `tools/mock_server.py`: add a scenario there once and port the test to each language.
