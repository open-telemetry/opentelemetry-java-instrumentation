/*
 * Copyright 2020, OpenTelemetry Authors
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

import spock.lang.Timeout

import java.util.concurrent.TimeUnit

class SecurityManagerSmokeTest extends AbstractSmokeTest {
  // Estimate for the amount of time instrumentation plus some extra
  private static final int TIMEOUT_SECS = 30

  String generatePolicyFile() {
    String policyFile = "${buildDirectory}/security.policy";
    new File(policyFile).text = "grant codeBase \"file:"+shadowJarPath+"\" {\n"+
      "  permission java.security.AllPermission;\n"+
      "};\n";
    return policyFile
  }

  @Override
  ProcessBuilder createProcessBuilder() {

    List<String> command = new ArrayList<>()
    command.add(javaPath())
    command.addAll(defaultJavaProperties)
    command.addAll((String[]) ["-cp", "${buildDirectory}/classes/java/main"])
    command.add("-Djava.security.manager")
    command.add("-Djava.security.policy="+generatePolicyFile())
    command.add("io.opentelemetry.smoketest.securitymanager.Application");

    System.out.println("Running: "+command);

    ProcessBuilder processBuilder = new ProcessBuilder(command)
    processBuilder.directory(new File(buildDirectory))
  }

  // TODO: once java7 support is dropped use waitFor() with timeout call added in java8
  // instead of timeout on test
  @Timeout(value = TIMEOUT_SECS, unit = TimeUnit.SECONDS)
  def "Application runs correctly with java.security.manager"() {
    expect:
    assert serverProcess.waitFor() == 0
  }


}
