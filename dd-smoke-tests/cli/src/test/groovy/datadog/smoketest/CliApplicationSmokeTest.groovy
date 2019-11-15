package datadog.smoketest

import spock.lang.Timeout

import java.util.concurrent.TimeUnit

class CliApplicationSmokeTest extends AbstractSmokeTest {
  // Estimate for the amount of time instrumentation, plus request, plus some extra
  private static final int TIMEOUT_SECS = 30

  @Override
  ProcessBuilder createProcessBuilder() {
    String cliShadowJar = System.getProperty("datadog.smoketest.cli.shadowJar.path")

    List<String> command = new ArrayList<>()
    command.add(javaPath())
    command.addAll(defaultJavaProperties)
    command.addAll((String[]) ["-jar", cliShadowJar])
    ProcessBuilder processBuilder = new ProcessBuilder(command)
    processBuilder.directory(new File(buildDirectory))
  }

  // TODO: once java7 support is dropped use waitFor() with timeout call added in java8
  // instead of timeout on test
  @Timeout(value = TIMEOUT_SECS, unit = TimeUnit.SECONDS)
  def "Cli application process ends before timeout"() {
    expect:
    assert serverProcess.waitFor() == 0
  }
}
