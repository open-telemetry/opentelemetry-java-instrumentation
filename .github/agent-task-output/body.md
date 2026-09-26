Consolidates the Mongo 3.1 and 3.7 instrumentation modules under the 3.1 Gradle project, with separate Muzzle selection for each compatibility range and artifact. Existing enablement aliases, runtime matchers, and telemetry scope names and versions are preserved.

The 3.1 and 3.7 integration suites keep separate driver classpaths, including latest-3.x coverage. The 3.7 helper unit-test project and the 4.x, async, and common modules remain separate.

Part of #20189.
