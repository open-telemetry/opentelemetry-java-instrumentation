/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_0;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.v3Preview;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.couchbase.client.core.cnc.InternalSpan;
import com.couchbase.client.core.cnc.RequestSpan;
import com.couchbase.client.core.cnc.RequestTracer;
import com.couchbase.client.core.env.CoreEnvironment;
import com.couchbase.client.core.msg.RequestContext;
import com.couchbase.client.core.msg.kv.BaseKeyValueRequest;
import com.couchbase.client.core.service.ServiceType;
import com.couchbase.client.java.env.ClusterEnvironment;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CouchbaseRequestTracerTest {

  private static final boolean EXPERIMENTAL_TELEMETRY =
      Boolean.getBoolean("otel.instrumentation.couchbase.emit-experimental-telemetry");

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  @SuppressWarnings({"DeduplicateConstants", "rawtypes", "unchecked"})
  void recordsRequestLifecycleAttributesAndChildSpans() {
    RequestTracer requestTracer = createRequestTracer();
    RequestSpan parent = requestTracer.requestSpan("parent", null);
    InternalSpan requestSpan = requestTracer.internalSpan("get", parent);

    BaseKeyValueRequest request = mock(BaseKeyValueRequest.class);
    when(request.serviceType()).thenReturn(ServiceType.KV);
    when(request.operationId()).thenReturn("0x17");
    when(request.key()).thenReturn("document".getBytes(UTF_8));

    RequestContext requestContext = mock(RequestContext.class);
    when(requestContext.request()).thenReturn(request);
    Map<String, Object> clientContext = new HashMap<>();
    clientContext.put("request_id", "abc");
    clientContext.put("ignored", null);
    when(requestContext.clientContext()).thenReturn(clientContext);
    when(requestContext.serverLatency()).thenReturn(42L);
    requestSpan.requestContext(requestContext);

    requestSpan.startPayloadEncoding();
    requestSpan.stopPayloadEncoding();
    requestSpan.startDispatch();
    requestSpan.stopDispatch();
    requestSpan.finish();
    parent.finish();

    testing.waitAndAssertTracesWithoutScopeVersionVerification(
        trace -> {
          if (emitSdkDetailSpans()) {
            trace.hasSpansSatisfyingExactly(
                CouchbaseRequestTracerTest::assertParentSpan,
                span -> assertRequestSpan(span, trace.getSpan(0)),
                span ->
                    span.hasName("request_encoding").hasKind(INTERNAL).hasParent(trace.getSpan(1)),
                span ->
                    span.hasName("dispatch_to_server")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(equalTo(longKey("peer.latency"), 42L)));
          } else {
            trace.hasSpansSatisfyingExactly(
                CouchbaseRequestTracerTest::assertParentSpan,
                span -> assertRequestSpan(span, trace.getSpan(0)));
          }
        });
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void endsChildSpansWhenReplacedOrRequestFinishes() {
    RequestTracer requestTracer = createRequestTracer();
    RequestSpan parent = requestTracer.requestSpan("parent", null);
    InternalSpan requestSpan = requestTracer.internalSpan("get", parent);

    BaseKeyValueRequest request = mock(BaseKeyValueRequest.class);
    when(request.serviceType()).thenReturn(ServiceType.KV);
    when(request.key()).thenReturn("document".getBytes(UTF_8));

    RequestContext requestContext = mock(RequestContext.class);
    when(requestContext.request()).thenReturn(request);
    requestSpan.requestContext(requestContext);

    requestSpan.startPayloadEncoding();
    requestSpan.startPayloadEncoding();
    requestSpan.startDispatch();
    requestSpan.startDispatch();
    requestSpan.finish();
    parent.finish();

    testing.waitAndAssertTracesWithoutScopeVersionVerification(
        trace -> {
          if (emitSdkDetailSpans()) {
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span -> span.hasName("get").hasParent(trace.getSpan(0)),
                span -> span.hasName("request_encoding").hasParent(trace.getSpan(1)),
                span -> span.hasName("request_encoding").hasParent(trace.getSpan(1)),
                span -> span.hasName("dispatch_to_server").hasParent(trace.getSpan(1)),
                span -> span.hasName("dispatch_to_server").hasParent(trace.getSpan(1)));
          } else {
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span -> span.hasName("get").hasParent(trace.getSpan(0)));
          }
        });
  }

  private static void assertParentSpan(SpanDataAssert span) {
    span.hasName("parent").hasKind(v3Preview() ? CLIENT : INTERNAL).hasNoParent();
  }

  private static void assertRequestSpan(SpanDataAssert span, SpanData parent) {
    span.hasKind(v3Preview() ? CLIENT : INTERNAL)
        .hasName("get")
        .hasParent(parent)
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("peer.service"), experimental("kv")),
            equalTo(stringKey("couchbase.operation_id"), experimental("0x17")),
            equalTo(stringKey("couchbase.document_id"), experimental("document")),
            equalTo(stringKey("couchbase.client_context.request_id"), experimental("abc")));
  }

  private static boolean emitSdkDetailSpans() {
    return !v3Preview() || EXPERIMENTAL_TELEMETRY;
  }

  private static <T> T experimental(T value) {
    return !v3Preview() || EXPERIMENTAL_TELEMETRY ? value : null;
  }

  private static RequestTracer createRequestTracer() {
    ClusterEnvironment environment = ClusterEnvironment.builder().build();
    cleanup.deferAfterAll(environment::shutdown);
    return ((CoreEnvironment) environment).requestTracer();
  }
}
