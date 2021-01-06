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

@AppServer(version = "9.4.35", jdk = "8")
@AppServer(version = "9.4.35", jdk = "11")
@AppServer(version = "10.0.0", jdk = "11")
@AppServer(version = "10.0.0", jdk = "15")
class JettySmokeTest extends AppServerTest {

  protected String getTargetImage(String jdk, String serverVersion) {
    "ghcr.io/open-telemetry/java-test-containers:jetty-${serverVersion}-jdk$jdk-20201215.422527843"
  }

  def getJettySpanName() {
    return serverVersion.startsWith("10.") ? "HandlerList.handle" : "HandlerCollection.handle"
  }

  @Override
  protected String getSpanName(String path) {
    return getJettySpanName()
  }

  @Override
  boolean testSmoke() {
    false
  }

  @Override
  boolean testAsyncSmoke() {
    false
  }

  @Override
  boolean testException() {
    false
  }

  @Unroll
  def "#appServer smoke test on JDK #jdk"(String appServer, String jdk) {
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
    traces.countSpansByName(getJettySpanName()) == 2

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

    where:
    [appServer, jdk] << getTestParams()
  }

  @Unroll
  def "#appServer async smoke test on JDK #jdk"(String appServer, String jdk) {
    String url = "http://localhost:${target.getMappedPort(8080)}/app/asyncgreeting"
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
    traces.countSpansByName(getJettySpanName()) == 2

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

    where:
    [appServer, jdk] << getTestParams()
  }

  @Unroll
  def "#appServer test request with error JDK #jdk"(String appServer, String jdk) {
    String url = "http://localhost:${target.getMappedPort(8080)}/app/exception"
    def request = new Request.Builder().url(url).get().build()
    def currentAgentVersion = new JarFile(agentPath).getManifest().getMainAttributes().get(Attributes.Name.IMPLEMENTATION_VERSION)

    when:
    def response = CLIENT.newCall(request).execute()
    TraceInspector traces = new TraceInspector(waitForTraces())
    Set<String> traceIds = traces.traceIds

    then: "There is one trace"
    traceIds.size() == 1

    and: "Response code is 500"
    response.code() == 500

    and: "There is one server span"
    traces.countSpansByKind(Span.SpanKind.SPAN_KIND_SERVER) == 1

    and: "Expected span names"
    traces.countSpansByName(getJettySpanName()) == 1

    //FIXME: exception not reported on jetty
//    and: "There is one exception"
//    traces.countFilteredEventAttributes('exception.message', 'This is expected') == 1

    and: "The span for the initial web request"
    traces.countFilteredAttributes("http.url", url) == 1

    and: "Number of spans tagged with current otel library version"
    traces.countFilteredResourceAttributes("telemetry.auto.version", currentAgentVersion) == 1

    and:
    traces.findResourceAttribute("os.name")
      .map { it.stringValue }
      .findAny()
      .isPresent()

    where:
    [appServer, jdk] << getTestParams()
  }

}
