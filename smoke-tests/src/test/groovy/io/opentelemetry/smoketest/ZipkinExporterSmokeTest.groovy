/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import spock.lang.IgnoreIf

@IgnoreIf({ os.windows })
class ZipkinExporterSmokeTest extends SmokeTest {

  protected String getTargetImage(String jdk) {
    "ghcr.io/open-telemetry/java-test-containers:smoke-springboot-jdk$jdk-20210218.577304949"
  }

  @Override
  protected Map<String, String> getExtraEnv() {
    return [
      "OTEL_TRACES_EXPORTER"         : "zipkin",
      "OTEL_EXPORTER_ZIPKIN_ENDPOINT": "http://collector:9411/api/v2/spans"
    ]
  }

  def "spring boot smoke test with Zipkin"() {
    setup:
    startTarget(11)

    //    def currentAgentVersion = new JarFile(agentPath).getManifest().getMainAttributes().get(Attributes.Name.IMPLEMENTATION_VERSION)

    when:
    def response = client().get("/greeting").aggregate().join()
    Collection<ExportTraceServiceRequest> traces = waitForTraces()

    then:
    response.contentUtf8() == "Hi!"
    countSpansByName(traces, '/greeting') == 1
    countSpansByName(traces, 'webcontroller.greeting') == 1
    countSpansByName(traces, 'webcontroller.withspan') == 1

    //This is currently broken, see https://github.com/open-telemetry/opentelemetry-java/issues/1970
//    [currentAgentVersion] as Set == findResourceAttribute(traces, "telemetry.auto.version")
//      .map { it.stringValue }
//      .collect(toSet())

    cleanup:
    stopTarget()

  }

}
