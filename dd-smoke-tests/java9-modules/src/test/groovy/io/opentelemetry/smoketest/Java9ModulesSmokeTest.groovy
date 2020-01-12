package io.opentelemetry.smoketest

import spock.lang.Timeout

import java.util.concurrent.TimeUnit

class Java9ModulesSmokeTest extends AbstractSmokeTest {
  // Estimate for the amount of time instrumentation plus some extra
  private static final int TIMEOUT_SECS = 30

  @Override
  ProcessBuilder createProcessBuilder() {
    String imageDir = System.getProperty("io.opentelemetry.smoketest.module.image")

    assert imageDir != null

    List<String> command = new ArrayList<>()
    command.add(imageDir + "/bin/java")
    command.addAll(defaultJavaProperties)
    command.addAll((String[]) ["-m", "io.opentelemetry.smoketest.moduleapp/io.opentelemetry.smoketest.moduleapp.ModuleApplication"])
    ProcessBuilder processBuilder = new ProcessBuilder(command)
    processBuilder.directory(new File(buildDirectory))
  }

  // TODO: once java7 support is dropped use waitFor() with timeout call added in java8
  // instead of timeout on test
  @Timeout(value = TIMEOUT_SECS, unit = TimeUnit.SECONDS)
  def "Module application runs correctly"() {
    expect:
    assert serverProcess.waitFor() == 0
  }
}
