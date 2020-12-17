/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import io.opentelemetry.proto.trace.v1.Span
import java.util.jar.Attributes
import java.util.jar.JarFile
import okhttp3.Request
import spock.lang.Unroll

abstract class AppServerTest extends SmokeTest {

  @Unroll
  def "#appServer smoke test on JDK #jdk"(String appServer, String jdk) {
    setup:
    if (!skipStartTarget()) {
      startTarget(jdk, appServer)
    }
    String url = "http://localhost:${target.getMappedPort(8080)}/app/greeting"
    def request = new Request.Builder().url(url).get().build()
    def currentAgentVersion = new JarFile(agentPath).getManifest().getMainAttributes().get(Attributes.Name.IMPLEMENTATION_VERSION)

    when:
    def response = CLIENT.newCall(request).execute()
    TraceInspector traces = new TraceInspector(waitForTraces())
    Set<String> traceIds = traces.traceIds
    String responseBody = response.body().string()

    then: "There is one trace"
    traceIds.size() == 1

    and: "trace id is present in the HTTP headers as reported by the called endpoint"
    responseBody.contains(traceIds.find())

    and: "Server spans in the distributed trace"
    traces.countSpansByKind(Span.SpanKind.SPAN_KIND_SERVER) == 2

    and: "Expected span names"
    traces.countSpansByName('/app/greeting') == 1
    traces.countSpansByName('/app/headers') == 1

    and: "The span for the initial web request"
    traces.countFilteredAttributes("http.url", url) == 1

    and: "Client and server spans for the remote call"
    traces.countFilteredAttributes("http.url", "http://localhost:8080/app/headers") == 2

    and: "Number of spans tagged with current otel library version"
    traces.countFilteredResourceAttributes("telemetry.auto.version", currentAgentVersion) == 3

    and:
    traces.findResourceAttribute("os.name")
      .map { it.stringValue }
      .findAny()
      .isPresent()

    cleanup:
    if (!skipStartTarget()) {
      stopTarget()
    }

    where:
    [appServer, jdk] << getTestParams()
  }

  abstract List<List<Object>> getTestParams()

  // override to start server only once for all tests
  boolean skipStartTarget() {
    false
  }
}