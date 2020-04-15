/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.smoketest

import io.opentelemetry.auto.test.utils.PortUtils
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractSmokeTest extends Specification {

  public static final API_KEY = "some-api-key"
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
    processBuilder.environment().put("DD_API_KEY", API_KEY)

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
