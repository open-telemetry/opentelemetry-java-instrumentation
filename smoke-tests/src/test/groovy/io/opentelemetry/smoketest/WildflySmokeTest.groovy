/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import io.opentelemetry.proto.trace.v1.Span
import spock.lang.Unroll

@AppServer(version = "13.0.0.Final", jdk = "8")
@AppServer(version = "13.0.0.Final", jdk = "8-openj9")
@AppServer(version = "17.0.1.Final", jdk = "8")
@AppServer(version = "17.0.1.Final", jdk = "8-openj9")
@AppServer(version = "17.0.1.Final", jdk = "11")
@AppServer(version = "17.0.1.Final", jdk = "11-openj9")
@AppServer(version = "17.0.1.Final", jdk = "17")
@AppServer(version = "17.0.1.Final", jdk = "16-openj9")
@AppServer(version = "21.0.0.Final", jdk = "8")
@AppServer(version = "21.0.0.Final", jdk = "8-openj9")
@AppServer(version = "21.0.0.Final", jdk = "11")
@AppServer(version = "21.0.0.Final", jdk = "11-openj9")
@AppServer(version = "21.0.0.Final", jdk = "17")
@AppServer(version = "21.0.0.Final", jdk = "16-openj9")
class WildflySmokeTest extends AppServerTest {

  protected String getTargetImagePrefix() {
    "ghcr.io/open-telemetry/java-test-containers:wildfly"
  }

  @Unroll
  def "JSP smoke test on WildFly"() {
    when:
    def response = client().get("/app/jsp").aggregate().join()
    TraceInspector traces = new TraceInspector(waitForTraces())
    String responseBody = response.contentUtf8()

    then:
    response.status().isSuccess()
    responseBody.contains("Successful JSP test")

    traces.countSpansByKind(Span.SpanKind.SPAN_KIND_SERVER) == 1

    traces.countSpansByName('/app/jsp') == 1

    where:
    [appServer, jdk] << getTestParams()
  }
}
