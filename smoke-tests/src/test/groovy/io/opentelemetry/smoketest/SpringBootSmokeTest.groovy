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

import static java.util.stream.Collectors.toSet

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import io.opentelemetry.trace.TraceId
import java.util.jar.Attributes
import java.util.jar.JarFile
import okhttp3.Request
import spock.lang.Unroll

class SpringBootSmokeTest extends SmokeTest {

  protected String getTargetImage(int jdk) {
    "open-telemetry-docker-dev.bintray.io/java/smoke-springboot-jdk$jdk:latest"
  }

  @Unroll
  def "spring boot smoke test on JDK #jdk"(int jdk) {
    setup:
    def output = startTarget(jdk)
    String url = "http://localhost:${target.getMappedPort(8080)}/greeting"
    def request = new Request.Builder().url(url).get().build()

    def currentAgentVersion = new JarFile(agentPath).getManifest().getMainAttributes().get(Attributes.Name.IMPLEMENTATION_VERSION)

    when:
    def response = client.newCall(request).execute()
    Collection<ExportTraceServiceRequest> traces = waitForTraces()

    then:
    response.body().string() == "Hi!"
    countSpansByName(traces, '/greeting') == 1
    countSpansByName(traces, 'WebController.greeting') == 1
    countSpansByName(traces, 'WebController.withSpan') == 1

    [currentAgentVersion] as Set == findResourceAttribute(traces, "telemetry.auto.version")
      .map { it.stringValue }
      .collect(toSet())

    then: "correct traceIds are logged via MDC instrumentation"
    getLoggedTraceIds(output) == getSpanStream(traces)
      .map({ TraceId.bytesToHex(it.getTraceId().toByteArray()) })
      .collect(toSet())

    cleanup:
    stopTarget()

    where:
    jdk << [8, 11, 14]
  }
}
