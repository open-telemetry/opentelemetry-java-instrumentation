# Smoke Tests

Assert that various applications will start up with the JavaAgent without any obvious ill effects.

Each subproject underneath `smoke-tests/images` produces one or more docker images containing some application
under the test. Various tests in the main module then use them to run the appropriate tests.

The `smoke-tests/extensions` folder contains a test application and packaged instrumentation(s) extension to
test compatibility with existing user-built extensions.

## Publishing image candidates

The image publishing workflows run after changes to their image sources on `main`. The servlet
and fake-backend workflows can also be started manually from the Actions tab. After every required
image is published and its registry manifest verified, the run uploads a
`smoke-test-publication-<family>-<attempt>` artifact containing `publication-candidate.json`.
Inspect the artifact on the specific workflow run for its source commit, publication identifier,
image references, and manifest digests. Treat a candidate as eligible only when its verification
job and the required publishing jobs succeeded: an artifact alone is not proof of success if a job
was cancelled during upload. Re-running a workflow can produce a new candidate; use its recorded
run and attempt rather than selecting individual tags from the registry.
To retry a servlet or fake-backend publication, re-run all jobs so that the family receives a
fresh identifier; re-running only a publish job is rejected to avoid overwriting previous images.

The early-JDK8 image is a historical fixture and is not part of this candidate mechanism.
