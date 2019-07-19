package datadog.smoketest

import spock.lang.Timeout

import java.util.concurrent.TimeUnit

class CliApplicationSmokeTest extends AbstractSmokeTest {
  // Estimate for the amount of time instrumentation, plus request, plus some extra
  private static final int TIMEOUT_SECS = 15

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

  @Timeout(value = TIMEOUT_SECS, unit = TimeUnit.SECONDS)
  def "Cli application process ends before timeout"() {
    expect:
    assert serverProcess.waitFor() == 0
  }
}
