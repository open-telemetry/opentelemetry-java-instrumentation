package datadog.smoketest

import datadog.trace.agent.test.utils.PortUtils
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractSmokeTest extends Specification {

  public static final API_KEY = "some-api-key"
  public static final PROFILING_START_DELAY_SECONDS = 1
  public static final int PROFILING_RECORDING_UPLOAD_PERIOD_SECONDS = 5

  @Shared
  protected String workingDirectory = System.getProperty("user.dir")
  @Shared
  protected String buildDirectory = System.getProperty("datadog.smoketest.builddir")
  @Shared
  protected String shadowJarPath = System.getProperty("datadog.smoketest.agent.shadowJar.path")
  @Shared
  protected int profilingPort
  @Shared
  protected String profilingUrl
  @Shared
  protected String[] defaultJavaProperties

  @Shared
  protected Process serverProcess

  def setupSpec() {
    if (buildDirectory == null || shadowJarPath == null) {
      throw new AssertionError("Expected system properties not found. Smoke tests have to be run from Gradle. Please make sure that is the case.")
    }

    profilingPort = PortUtils.randomOpenPort()
    profilingUrl = "http://localhost:${profilingPort}/"

    defaultJavaProperties = [
      "-javaagent:${shadowJarPath}",
      "-Ddd.writer.type=LoggingWriter",
      "-Ddd.service.name=smoke-test-java-app",
      "-Ddd.profiling.enabled=true",
      "-Ddd.profiling.start-delay=${PROFILING_START_DELAY_SECONDS}",
      "-Ddd.profiling.upload.period=${PROFILING_RECORDING_UPLOAD_PERIOD_SECONDS}",
      "-Ddd.profiling.url=http://localhost:${profilingPort}",
      "-Ddatadog.slf4j.simpleLogger.defaultLogLevel=debug",
      "-Dorg.slf4j.simpleLogger.defaultLogLevel=debug"
    ]

    ProcessBuilder processBuilder = createProcessBuilder()

    processBuilder.environment().put("JAVA_HOME", System.getProperty("java.home"))
    processBuilder.environment().put("DD_API_KEY", API_KEY)

    processBuilder.redirectErrorStream(true)
    File log = new File("${buildDirectory}/reports/testProcess.${this.getClass().getName()}.log")
    processBuilder.redirectOutput(ProcessBuilder.Redirect.to(log))

    serverProcess = processBuilder.start()
  }

  String javaPath() {
    final String separator = System.getProperty("file.separator")
    return System.getProperty("java.home") + separator + "bin" + separator + "java"
  }

  def cleanupSpec() {
    serverProcess?.waitForOrKill(1)
  }

  abstract ProcessBuilder createProcessBuilder()
}
