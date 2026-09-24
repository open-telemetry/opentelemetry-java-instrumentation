# Hosted Agent Task GitHub Actions log retrieval experiment

## Result

**Full retrieval and verification failed in this hosted task.** The hosted shell had no usable GitHub API credential, while the credential available behind the GitHub Actions tool was not available to a local direct-to-file transport. Consequently, no complete local file was obtained and the independently supplied raw measurements could not be reproduced.

This result does not treat a tool preview as evidence of completeness.

## Pinned identity checks

Both the pre-download and post-download checks succeeded:

- Repository: `open-telemetry/opentelemetry-java-instrumentation`
- PR: `20089`, titled “Report configured Couchbase Protostellar targets”
- Workflow run: `35950484495`, “Build pull request”
- Run attempt: `1`
- Run head branch: `trask-couchbase-protostellar-targets`
- Run head: `ae0bf775e94cbe5c078b92c3bba3d1ee22042fbd`
- Job: `107477774633`
- Job name: `build / common / test0 (8, hotspot, indy true)`
- Job `run_id`: `35950484495`
- Job `run_attempt`: `1`
- Job head: `ae0bf775e94cbe5c078b92c3bba3d1ee22042fbd`

The run and job remained completed with conclusion `failure` in both checks.

## Retrieval tests

### Authenticated shell/API attempt

The exact REST path was passed to GitHub CLI with stdout redirected directly to `/tmp/otel-job-107477774633.log` and stderr redirected to a separate temporary file:

```text
gh api --method GET /repos/open-telemetry/opentelemetry-java-instrumentation/actions/jobs/107477774633/logs
```

No log bytes passed through the agent response or prompt. GitHub CLI exited with status `4` before making an authenticated download because the shell environment had no `GH_TOKEN`. It wrote zero log bytes. No credential value was requested, read, or printed.

- HTTP status: unavailable because authenticated transport did not start
- `Content-Range`: unavailable
- Downloaded bytes: `0`
- SHA-256: not computed
- Newline count: not computed

### Unauthenticated standard-library attempt

A Python `urllib.request` stream then requested the exact HTTPS REST endpoint with no authorization header and was prepared to copy response blocks directly to the same temporary file while incrementally calculating byte count, SHA-256, and newline count.

- HTTP status: `403`
- `Content-Range`: absent
- Downloaded bytes: `0`
- SHA-256: not computed
- Newline count: not computed

No response body, redirect URL, authorization header, cookie, or token was printed or persisted.

### GitHub Actions tool control check

The hosted GitHub Actions tool could query the pinned run and job, demonstrating that its server-side integration had usable API access. A deliberately limited one-line tail request returned only the final log line and reported an original length of `235160`. That is consistent with the independently supplied line count, but it was not a local file, supplied no raw byte count or digest, and therefore was not accepted as completeness evidence.

The tool credential is not exposed to the hosted shell, and the available tool interface does not provide a direct-to-local-file job-log stream. Using a larger tool response would have violated the experiment's transport requirement and still would not establish raw-byte identity.

## Expected raw measurements and verification outcome

The supplied reference for the complete raw REST job log was:

- Bytes: `97,119,511`
- Newline-terminated lines: `235,160`
- Raw SHA-256: `b06a976e7a68033def295c94ad81fc5b43cb0b4806af8d19a6a1dd2fc7cad92b`

The separate redacted/escaped digest `d21acda58f183e0ef9a858bb70dce0a48c9122fd9dcd9290329ddd8e41b9d4e1` was not used as a raw-byte target.

Because retrieval produced zero bytes, none of the reference measurements could be independently verified in this hosted task.

## Requested local searches

The searches for the following strings were not performed:

- `CouchbaseProtostellarTargetsTest`
- `NoClassDefFoundError`
- `CouchbaseConnectionStrings`

Searching a preview or unverified response would not satisfy the requirement to search the complete verified file. Therefore there are no verified test names, exceptions, file/line locations, or excerpts to report.

## Cleanup and validation

The temporary log and stderr files under `/tmp` were removed after the probes. No Gradle build, formatter, test suite, workflow rerun, PR modification, or GitHub comment was performed.

The specific barrier was credential separation: GitHub API authentication was usable only inside the GitHub Actions tool service, not by the hosted shell process that needed to stream the endpoint response to a local file.
