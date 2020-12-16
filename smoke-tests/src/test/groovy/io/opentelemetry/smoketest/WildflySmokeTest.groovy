/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import io.opentelemetry.proto.trace.v1.Span
import okhttp3.Request

class WildflySmokeTest extends AppServerTest {

  protected String getTargetImage(int jdk, String serverVersion) {
    "ghcr.io/open-telemetry/java-test-containers:wildfly-${serverVersion}-jdk$jdk-20201207.405832649"
  }

  def "JSP smoke test on WildFly"() {
    setup:
    startTarget(11, "21.0.0.Final")
    String url = "http://localhost:${target.getMappedPort(8080)}/app/jsp"
    def request = new Request.Builder().url(url).get().build()

    when:
    def response = CLIENT.newCall(request).execute()
    TraceInspector traces = new TraceInspector(waitForTraces())
    String responseBody = response.body().string()

    then:
    response.successful
    responseBody.contains("Successful JSP test")

    traces.countSpansByKind(Span.SpanKind.SPAN_KIND_SERVER) == 1

    traces.countSpansByName('/app/jsp') == 1

  }

  @Override
  List<List<Object>> getTestParams() {
    //TODO introduce new configuration parameter to run all permutations of appServer/jdk
    return [
      ["13.0.0.Final", 8],
      ["17.0.1.Final", 11],
      ["21.0.0.Final", 11]
    ]
  }
}
