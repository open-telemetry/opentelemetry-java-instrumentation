/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_4;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.couchbase.client.core.cnc.RequestSpan;
import com.couchbase.client.core.cnc.RequestTracer;
import com.couchbase.client.core.cnc.TracingIdentifiers;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseRequestPeers;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseRequestPeers.Scope;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class CouchbaseRequestTracerTest {

  @Test
  void capturesSocketPeerInStableMode() {
    assumeTrue(emitStableDatabaseSemconv());
    RequestSpan parent = mock(RequestSpan.class);
    RequestSpan delegateSpan = mock(RequestSpan.class);
    InetSocketAddress remoteAddress =
        new InetSocketAddress(InetAddress.getLoopbackAddress(), 11210);
    Scope scope = CouchbaseRequestPeers.open(parent, remoteAddress);

    assertThat(scope).isNotNull();
    RequestSpan span;
    try {
      span = tracer(delegateSpan).requestSpan(TracingIdentifiers.SPAN_DISPATCH, parent);
    } finally {
      scope.close();
    }

    span.attribute(TracingIdentifiers.ATTR_REMOTE_HOSTNAME, "configured.example");
    span.attribute(TracingIdentifiers.ATTR_REMOTE_PORT, 12345);

    verify(delegateSpan)
        .attribute(NETWORK_PEER_ADDRESS.getKey(), remoteAddress.getAddress().getHostAddress());
    verify(delegateSpan).attribute(NETWORK_PEER_PORT.getKey(), 11210);
    verifyNoMoreInteractions(delegateSpan);
  }

  @Test
  void preservesSdkPeerWithoutSocketPeerInStableMode() {
    assumeTrue(emitStableDatabaseSemconv());
    RequestSpan delegateSpan = mock(RequestSpan.class);
    RequestSpan span = tracer(delegateSpan).requestSpan(TracingIdentifiers.SPAN_DISPATCH, null);

    span.attribute(TracingIdentifiers.ATTR_REMOTE_HOSTNAME, "db.example");
    span.attribute(TracingIdentifiers.ATTR_REMOTE_PORT, 11210);

    verify(delegateSpan).attribute(NETWORK_PEER_ADDRESS.getKey(), "db.example");
    verify(delegateSpan).attribute(NETWORK_PEER_PORT.getKey(), 11210);
  }

  @Test
  void preservesSdkPeerInLegacyMode() {
    assumeFalse(emitStableDatabaseSemconv());
    RequestSpan delegateSpan = mock(RequestSpan.class);
    RequestSpan span = tracer(delegateSpan).requestSpan(TracingIdentifiers.SPAN_DISPATCH, null);

    span.attribute(TracingIdentifiers.ATTR_REMOTE_HOSTNAME, "db.example");
    span.attribute(TracingIdentifiers.ATTR_REMOTE_PORT, 11210);

    verify(delegateSpan).attribute(TracingIdentifiers.ATTR_REMOTE_HOSTNAME, "db.example");
    verify(delegateSpan).attribute(TracingIdentifiers.ATTR_REMOTE_PORT, 11210);
  }

  private static RequestTracer tracer(RequestSpan delegateSpan) {
    RequestTracer delegate = mock(RequestTracer.class);
    when(delegate.requestSpan(anyString(), nullable(RequestSpan.class))).thenReturn(delegateSpan);
    return new CouchbaseRequestTracer(delegate);
  }
}
