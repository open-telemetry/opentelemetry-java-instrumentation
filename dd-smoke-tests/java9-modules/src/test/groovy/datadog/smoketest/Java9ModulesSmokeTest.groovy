package datadog.smoketest

import spock.lang.Timeout

import java.util.concurrent.TimeUnit

abstract class Java9ModulesSmokeTest extends AbstractSmokeTest {
  // Estimate for the amount of time instrumentation plus some extra
  private static final int TIMEOUT_SECS = 30

  @Override
  ProcessBuilder createProcessBuilder() {
    println System.getProperties()
    String imageDir = System.getProperty("datadog.smoketest.${moduleName}.image")

    assert imageDir != null

    List<String> command = new ArrayList<>()
    command.add(imageDir + "/bin/java")
    command.addAll(defaultJavaProperties)
    command.addAll((String[]) ["-m", "${moduleName}/${moduleName}.ModuleApplication"])
    ProcessBuilder processBuilder = new ProcessBuilder(command)
    processBuilder.directory(new File(buildDirectory))
  }

  abstract getModuleName()

  // TODO: once java7 support is dropped use waitFor() with timeout call added in java8
  // instead of timeout on test
  @Timeout(value = TIMEOUT_SECS, unit = TimeUnit.SECONDS)
  def "Module application runs correctly"() {
    expect:
    assert serverProcess.waitFor() == 0
  }
}

class InstrumentableModuleTest extends Java9ModulesSmokeTest {

  @Override
  def getModuleName() {
    return "datadog.smoketest.moduleapp.instrumentable"
  }
}

class UninstrumentableModuleTest extends Java9ModulesSmokeTest {

  @Override
  def getModuleName() {
    return "datadog.smoketest.moduleapp.uninstrumentable"
  }
}
