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


import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractSmokeTest extends Specification {

  @Shared
  protected String workingDirectory = System.getProperty("user.dir")
  @Shared
  protected String buildDirectory = System.getProperty("io.opentelemetry.smoketest.builddir")
  @Shared
  protected String shadowJarPath = System.getProperty("io.opentelemetry.smoketest.agent.shadowJar.path")

  @Shared
  protected String[] defaultJavaProperties
  @Shared
  protected Process testedProcess

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

    defaultJavaProperties = [
      "-javaagent:${shadowJarPath}",
      "-Dio.opentelemetry.auto.slf4j.simpleLogger.defaultLogLevel=debug",
      "-Dorg.slf4j.simpleLogger.defaultLogLevel=debug"
    ]

    ProcessBuilder processBuilder = createProcessBuilder()

    processBuilder.environment().put("JAVA_HOME", System.getProperty("java.home"))

    // Setting configuration variables of batch span processor through env vars
    // This config is to immediately flush a batch of 1 span with delay of 10ms
    processBuilder.environment().put("OTEL_BSP_MAX_EXPORT_BATCH", "1")
    processBuilder.environment().put("OTEL_BSP_SCHEDULE_DELAY", "10")

    processBuilder.redirectErrorStream(true)
    logfile = new File("${buildDirectory}/reports/testProcess.${this.getClass().getName()}.log")
    processBuilder.redirectOutput(ProcessBuilder.Redirect.to(logfile))

    testedProcess = processBuilder.start()
  }

  String javaPath() {
    String separator = System.getProperty("file.separator")
    return System.getProperty("java.home") + separator + "bin" + separator + "java"
  }

  def cleanupSpec() {
    testedProcess?.waitForOrKill(1)
  }

  abstract ProcessBuilder createProcessBuilder()
}
