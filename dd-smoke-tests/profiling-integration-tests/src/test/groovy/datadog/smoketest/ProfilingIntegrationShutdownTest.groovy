package datadog.smoketest


import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

import java.util.concurrent.TimeUnit

class ProfilingIntegrationShutdownTest extends AbstractSmokeTest {

  // This needs to give enough time for test app to start up and recording to happen
  private static final int REQUEST_WAIT_TIMEOUT = 40

  // Run app enough time to get profiles
  private static final int RUN_APP_FOR = PROFILING_START_DELAY_SECONDS + PROFILING_RECORDING_UPLOAD_PERIOD_SECONDS * 2 + 1

  private final MockWebServer server = new MockWebServer()

  @Override
  ProcessBuilder createProcessBuilder() {
    String profilingShadowJar = System.getProperty("datadog.smoketest.profiling.shadowJar.path")

    List<String> command = new ArrayList<>()
    command.add(javaPath())
    command.addAll(defaultJavaProperties)
    command.addAll((String[]) ["-jar", profilingShadowJar])
    command.add(Integer.toString(RUN_APP_FOR))
    ProcessBuilder processBuilder = new ProcessBuilder(command)
    processBuilder.directory(new File(buildDirectory))
    return processBuilder
  }

  def setup() {
    server.start(profilingPort)
  }

  def cleanup() {
    try {
      server.shutdown()
    } catch (final IOException e) {
      // Looks like this happens for some unclear reason, but should not affect tests
    }
  }

  def "test that profiling agent doesn't prevent app from exiting"() {
    setup:
    server.enqueue(new MockResponse().setResponseCode(200))

    when:
    RecordedRequest request = server.takeRequest(REQUEST_WAIT_TIMEOUT, TimeUnit.SECONDS)

    then:
    request.bodySize > 0

    then:
    // Wait for the app exit with some extra time.
    // The expectation is that agent doesn't prevent app from exiting.
    serverProcess.waitFor(RUN_APP_FOR + 10, TimeUnit.SECONDS) == true
  }

}
