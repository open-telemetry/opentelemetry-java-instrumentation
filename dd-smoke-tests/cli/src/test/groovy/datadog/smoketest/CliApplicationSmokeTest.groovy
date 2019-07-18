package datadog.smoketest

import spock.util.concurrent.PollingConditions

class CliApplicationSmokeTest extends AbstractSmokeTest {
  // Estimate for the amount of time instrumentation takes plus a little extra
  private static final int INSTRUMENTATION_DELAY = 6 + 5

  private static final int SHUTDOWN_DELAY = 2
  
  @Override
  ProcessBuilder createProcessBuilder() {
    String cliShadowJar = System.getProperty("datadog.smoketest.cli.shadowJar.path")

    List<String> command = new ArrayList<>()
    command.add(javaPath())
    command.addAll(defaultJavaProperties)
    command.addAll((String[]) ["-jar", cliShadowJar, String.valueOf(SHUTDOWN_DELAY)])
    ProcessBuilder processBuilder = new ProcessBuilder(command)
    processBuilder.directory(new File(buildDirectory))
  }

  def "Cli application process ends before timeout"() {
    setup:
    def conditions = new PollingConditions(timeout: INSTRUMENTATION_DELAY, initialDelay: SHUTDOWN_DELAY)

    expect:
    serverProcess.isAlive()

    conditions.eventually {
      assert !serverProcess.isAlive()
    }
  }
}
