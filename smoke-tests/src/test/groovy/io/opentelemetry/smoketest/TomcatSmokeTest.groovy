/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import io.opentelemetry.proto.trace.v1.Span
import okhttp3.Request

class TomcatSmokeTest extends AppServerTest {

  protected String getTargetImage(String jdk, String serverVersion) {
    "ghcr.io/open-telemetry/java-test-containers:tomcat-${serverVersion}-jdk$jdk-20201207.405832649"
  }

  def "Server Handler test"() {
    setup:
    startTarget(8, "7.0.107")
    String url = "http://localhost:${target.getMappedPort(8080)}/this_is_not_mapped_to_any_servlet"
    def request = new Request.Builder().url(url).get().build()

    when:
    def response = CLIENT.newCall(request).execute()
    TraceInspector traces = new TraceInspector(waitForTraces())

    then:
    !response.successful
    response.code() == 404

    traces.countSpansByKind(Span.SpanKind.SPAN_KIND_SERVER) == 1

    traces.countSpansByName('CoyoteAdapter.service') == 1
  }

  @Override
  List<List<Object>> getTestParams() {
    return [
      ["7.0.107", 8],
      ["8.5.60", 8],
      ["8.5.60", 11],
      ["9.0.40", 8],
      ["9.0.40", 11]
    ]
  }
}
