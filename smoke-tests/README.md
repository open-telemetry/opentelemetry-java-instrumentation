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

## Reviewing image update PRs

Successful maintained-family publications may produce one draft PR that updates the corresponding
image-version constants. Bot-authored update comments record each exact previous and proposed
version and link to the verified publisher run and attempt. Review those updates and let the ordinary
smoke-test checks finish before manually merging; a failed check never changes the pins on `main`.

If publication validation fails, use the exact run and attempt in the diagnostic. Re-run all jobs
for servlet or fake-backend publications to get a fresh family identifier. If an adoption update
is refused because its branch changed, conflicts with a main pin, or contains unexpected edits,
preserve that branch and resolve the open PR manually rather than force-pushing over it. The
**Propose smoke-test image updates** workflow can also be started manually with a publisher run ID
and attempt for read-only validation.

The **Refresh floating smoke-test image bases** workflow runs every Monday at 06:17 UTC. It
refreshes fake-backend, gRPC, Play, Quarkus, Security Manager, and Spring Boot images; servlet
images use digest-pinned bases and are refreshed only after source or base changes. The historical
early-JDK8 fixture is not refreshed. To refresh sooner, start the workflow from `main` and choose
`all` or one family. Each selected family runs through its existing publisher, complete-family
verification, and adoption workflow; successful updates still require the normal smoke-test checks
and a manual merge.

If a family fails, check its publisher run and failure notification, then start a fresh run for
that family (or re-run all publisher jobs) to get a new publication identifier. Failed or
incomplete runs do not produce an eligible candidate, and one family's dispatch failure does not
stop other selected families. To safely validate adoption before enabling writes, start
**Propose smoke-test image updates** with a successful publisher run ID and exact attempt; this
manual path performs read-only validation. After the refresh workflow is active on `main`, a
maintainer should run one family end to end and confirm the publisher candidate, bot draft PR, and
ordinary PR checks. The draft PR and checks depend on the repository's configured bot App
credentials; this unmerged change has not exercised that privileged live flow.
