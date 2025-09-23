# OpenTelemetry Java Instrumentation

OpenTelemetry Java Instrumentation is a Java agent that provides automatic instrumentation for a wide variety of libraries and frameworks. It dynamically injects bytecode to capture telemetry data without requiring code changes.

Always reference these instructions first and fallback to search or bash commands only when you encounter unexpected information that does not match the info here.

## Working Effectively
- Bootstrap, build, and test the repository:
  - Use Java 21 for building (required, see `.java-version`)
  - Configuration phase: Takes 60-90 seconds due to ~500+ modules (normal). NEVER CANCEL.
  - `./gradlew assemble` -- builds the complete Java agent. NEVER CANCEL. Build takes 20-45 minutes. Set timeout to 60+ minutes.
  - `./gradlew check javadoc sourcesJar -x spotlessCheck -PskipTests=true` -- builds with checks but skips tests for faster iteration
  - The Java agent artifact will be at: `javaagent/build/libs/opentelemetry-javaagent-<version>.jar`
- For faster local development, add `removeJarVersionNumbers=true` to `~/.gradle/gradle.properties` to keep consistent jar names
- Run tests:
  - `./gradlew test` -- runs all tests. NEVER CANCEL. Takes 60-120 minutes across multiple Java versions. Set timeout to 180+ minutes.
  - `./gradlew :instrumentation:test` -- runs instrumentation tests only
  - `./gradlew :smoke-tests:test` -- runs smoke tests (separate from main test suite)
  - `./gradlew test -PtestJavaVersion=<version>` -- test on specific Java version (8, 11, 17, 21, 23, 24, 25-ea)
  - `./gradlew test -PtestLatestDeps=true` -- test against latest dependency versions
  - `./gradlew test -Ddev=true -x spotlessCheck -x checkstyleMain` -- ignore warnings during development
- Code quality:
  - `./gradlew spotlessCheck` -- NEVER CANCEL. Takes 10-20 minutes with ~500 modules. Set timeout to 30+ minutes.
  - `./gradlew spotlessApply` -- auto-fix formatting issues
  - `./gradlew generateLicenseReport --no-build-cache` -- update license information

## Validation
- ALWAYS run `./gradlew spotlessCheck` before committing or the CI (.github/workflows/build-common.yml) will fail
- Build validation scenario: After successful build, verify the Java agent jar exists at `javaagent/build/libs/opentelemetry-javaagent-*.jar`
- Test validation scenarios:
  - Run specific instrumentation tests: `./gradlew :instrumentation:<INSTRUMENTATION_NAME>:test --tests <TEST_CLASS>`
  - Always test with `./gradlew test -Ddev=true -x spotlessCheck -x checkstyleMain` to ignore warnings during development
- Use build scans for debugging failures - check `build-scan.txt` after failed builds
- ALWAYS manually test instrumentation changes by running the affected instrumentation's test suite

## Common tasks
The following are critical commands and patterns for working with this repository:

### Required Environment Setup
- Java 21 is REQUIRED for building (see `.java-version`)
- Gradle 9.1.0 (provided via wrapper `./gradlew`)
- Docker is required for some tests (smoke tests, instrumentation tests using testcontainers)
- Node 16 for Vaadin tests (if working on Vaadin instrumentation)

### Repository Structure
Key directories:
- `instrumentation/` - Contains all instrumentation modules (~500+ modules for different libraries)
- `javaagent/` - Core Java agent implementation  
- `docs/` - Documentation including supported libraries list
- `smoke-tests/` - End-to-end integration tests
- `examples/` - Example extensions and distributions
- `gradle-plugins/` - Custom Gradle plugins used by the build
- `.github/workflows/` - CI/CD pipeline definitions
- `settings.gradle.kts` - Contains ~649 lines with all module definitions

### Working with Instrumentations
- Each instrumentation is in `instrumentation/<library-name>/`
- Structure: `<library>/<version>/<javaagent|library>/` (e.g., `instrumentation/spring/spring-boot-autoconfigure/`)
- Test structure: Each instrumentation has separate test modules
- Documentation generator: `./gradlew :instrumentation-docs:runAnalysis` updates `docs/instrumentation-list.yaml`
- Telemetry collection: `./instrumentation-docs/collect.sh` runs tests to generate telemetry metadata

### Build Performance Tips
- Configuration phase takes 60-90 seconds due to large number of modules (~500+) - this is NORMAL
- Gradle daemon startup adds 10-20 seconds on first run
- Use `--no-parallel` for problematic builds
- Use `--continue` to run all tests even if some fail
- Build cache is enabled by default (`--build-cache`) for faster rebuilds
- Use `./gradlew build --scan` for detailed build analysis (may fail due to network restrictions)
- IntelliJ users: Use module unloading to reduce resource consumption with 500+ modules

### Testing Patterns
- Tests use multiple JVM versions: 8, 11, 17, 21, 23, 24, 25-ea
- Tests run on both HotSpot and OpenJ9 VMs
- Matrix testing: 4 test partitions × multiple Java versions × 2 VMs
- Some tests require x86_64 architecture (use colima on ARM: `./instrumentation-docs/collect.sh`)

### Common Issues and Solutions
- Network connectivity issues: Some builds may fail if external repositories are unreachable
- OutOfMemoryError: Increase Gradle daemon heap size: `org.gradle.jvmargs=-Xmx3g` in `gradle.properties`
- ARM compatibility: Some instrumentation tests need x86_64, use colima for containerized testing
- IDE setup: IntelliJ users should unload unused modules to reduce resource consumption

### CI/CD Integration
- Main workflow: `.github/workflows/build.yml`
- Common checks: `.github/workflows/build-common.yml` (spotless, license, build, test matrix)
- Tests run in 4 partitions for parallelization
- Smoke tests run separately for different application servers (jetty, tomcat, wildfly, etc.)

### Key Files to Check After Changes
- Always run `./gradlew spotlessCheck` before committing or the CI will fail
- Always regenerate documentation if adding/modifying instrumentations: `./gradlew :instrumentation-docs:runAnalysis`
- Check license compliance: `./gradlew generateLicenseReport --no-build-cache`
- Verify instrumentation list: `docs/instrumentation-list.yaml`
- Update supported libraries docs: `docs/supported-libraries.md`
- Run muzzle checks for specific instrumentation: `./gradlew :instrumentation:<module>:javaagent:muzzle`

## Instrumentation Development Workflow
1. Create/modify instrumentation in `instrumentation/<library>/<version>/<type>/`
2. Add metadata in `metadata.yaml` if needed (see `instrumentation-docs/readme.md`)
3. Write/update tests following existing patterns (see `docs/contributing/writing-instrumentation.md`)
4. Run specific tests: `./gradlew :instrumentation:<library>:<version>:<type>:test`
5. Run muzzle check: `./gradlew :instrumentation:<library>:<version>:javaagent:muzzle`
6. Update documentation: `./gradlew :instrumentation-docs:runAnalysis`
7. Validate with broader test suite: `./gradlew test -PtestJavaVersion=21` (faster than full matrix)
8. Run formatting: `./gradlew spotlessApply`
9. Final check: `./gradlew spotlessCheck`

### Available Gradle Help Commands
- `./gradlew help --task <task>` -- get detailed info about any task
- `./gradlew tasks` -- list all available tasks (takes 2-3 minutes)
- `./gradlew tasks --group Muzzle` -- list muzzle-specific tasks
- `./gradlew projects` -- list all project modules (~500+)

## Critical Build Time Warnings
- **NEVER CANCEL** builds or tests - they legitimately take 20-180 minutes
- Configuration phase: 1-2 minutes (normal due to 500+ modules in settings.gradle.kts)
- Full build (`./gradlew assemble`): 20-45 minutes
- Test suite (`./gradlew test`): 60-120 minutes (matrix of Java versions × VMs × test partitions)
- Code formatting (`./gradlew spotlessCheck`): 10-20 minutes
- Always set timeouts to 2x expected time to account for CI variability
- Network issues may cause build failures due to unreachable external repositories

## Environment Variables for Testing
- `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` - Docker socket location
- `TESTCONTAINERS_HOST_OVERRIDE` - Container-to-container communication host
- `USE_LINUX_CONTAINERS=1` - Force Linux containers on Windows
- Set `-Ddev=true` for development to ignore warnings in tests