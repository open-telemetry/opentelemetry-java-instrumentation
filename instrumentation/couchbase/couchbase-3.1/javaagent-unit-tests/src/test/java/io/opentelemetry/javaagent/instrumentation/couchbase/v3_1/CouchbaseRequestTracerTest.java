/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.couchbase.client.core.cnc.RequestSpan;
import com.couchbase.client.core.cnc.RequestTracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.CouchbaseServerTarget;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseRequestPeers;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseRequestPeers.Scope;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class CouchbaseRequestTracerTest {

  @Test
  void capturesPeerForLegacyDispatchSpanName() throws Exception {
    RequestTracer tracer =
        CouchbaseRequestTracer.create(OpenTelemetry.noop().getTracer("test-couchbase"));
    RequestSpan parent = tracer.requestSpan("test", null);
    Scope scope =
        CouchbaseRequestPeers.open(
            parent,
            new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 11210));

    assertThat(scope).isNotNull();
    tracer.requestSpan("cb.dispatch_to_server", parent);
    assertThat(CouchbaseRequestPeers.consume(parent)).isNull();
    scope.close();
  }

  @Test
  void settingConfiguredPortDoesNotThrowWithoutRequestSpanLongOverload() throws Exception {
    RequestTracer tracer =
        CouchbaseRequestTracer.create(OpenTelemetry.noop().getTracer("test-couchbase"));
    RequestSpan span = tracer.requestSpan("test", null);
    CouchbaseServerTarget.Builder builder = CouchbaseServerTarget.builder();
    builder.addSeed("db.example", 11210);
    CouchbaseServerTarget target = builder.build();
    assertThat(target).isNotNull();
    Field delegateField = span.getClass().getDeclaredField("delegate");
    delegateField.setAccessible(true);
    RequestSpan delegate = (RequestSpan) delegateField.get(span);
    Method method =
        span.getClass()
            .getDeclaredMethod(
                "setConfiguredTarget", RequestSpan.class, CouchbaseServerTarget.class);
    method.setAccessible(true);

    assertThatCode(() -> method.invoke(null, delegate, target)).doesNotThrowAnyException();
  }
}
