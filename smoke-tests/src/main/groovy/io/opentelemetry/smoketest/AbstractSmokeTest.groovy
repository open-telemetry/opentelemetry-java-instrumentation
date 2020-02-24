package io.opentelemetry.smoketest

import io.opentelemetry.auto.test.utils.PortUtils
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractSmokeTest extends Specification {

  public static final PROFILING_API_KEY = "org2_api_key"
  public static final PROFILING_START_DELAY_SECONDS = 1
  public static final int PROFILING_RECORDING_UPLOAD_PERIOD_SECONDS = 5

  @Shared
  protected String workingDirectory = System.getProperty("user.dir")
  @Shared
  protected String buildDirectory = System.getProperty("io.opentelemetry.smoketest.builddir")
  @Shared
  protected String shadowJarPath = System.getProperty("io.opentelemetry.smoketest.agent.shadowJar.path")
  @Shared
  protected int profilingPort
  @Shared
  protected String profilingUrl
  @Shared
  protected String[] defaultJavaProperties
  @Shared
  protected Process serverProcess
  @Shared
  protected String exporterPath = System.getProperty("ota.exporter.jar")

  @Shared
  protected File logfile

  def countSpans(prefix) {
    return logfile.text.tokenize('\n').count {
      it.startsWith prefix
    }
  }

  def setupSpec() {
    if (buildDirectory == null || shadowJarPath == null) {
      throw new AssertionError("Expected system properties not found. Smoke tests have to be run from Gradle. Please make sure that is the case.")
    }

    profilingPort = PortUtils.randomOpenPort()
    profilingUrl = "http://localhost:${profilingPort}/"

    defaultJavaProperties = [
      "-javaagent:${shadowJarPath}",
      "-Dio.opentelemetry.auto.slf4j.simpleLogger.defaultLogLevel=debug",
      "-Dorg.slf4j.simpleLogger.defaultLogLevel=debug"
    ]

    ProcessBuilder processBuilder = createProcessBuilder()

    processBuilder.environment().put("JAVA_HOME", System.getProperty("java.home"))
    processBuilder.environment().put("DD_PROFILING_APIKEY", PROFILING_API_KEY)

    processBuilder.redirectErrorStream(true)
    logfile = new File("${buildDirectory}/reports/testProcess.${this.getClass().getName()}.log")
    processBuilder.redirectOutput(ProcessBuilder.Redirect.to(logfile))

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
