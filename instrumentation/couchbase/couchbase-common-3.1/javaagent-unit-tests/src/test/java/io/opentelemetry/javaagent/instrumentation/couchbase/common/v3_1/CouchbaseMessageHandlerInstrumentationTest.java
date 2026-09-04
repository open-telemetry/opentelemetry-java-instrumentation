/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.couchbase.client.core.cnc.RequestSpan;
import com.couchbase.client.core.deps.io.netty.channel.Channel;
import com.couchbase.client.core.deps.io.netty.channel.ChannelHandlerContext;
import com.couchbase.client.core.msg.HttpRequest;
import com.couchbase.client.core.msg.NonChunkedHttpRequest;
import com.couchbase.client.core.msg.Request;
import com.couchbase.client.core.msg.kv.KeyValueRequest;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseRequestPeers.Peer;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseRequestPeers.Scope;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CouchbaseMessageHandlerInstrumentationTest {

  @ParameterizedTest
  @MethodSource("requests")
  void capturesRequestPeer(Request<?> request) throws UnknownHostException {
    assumeTrue(emitStableDatabaseSemconv());
    RequestSpan parent = mock(RequestSpan.class);
    when(request.requestSpan()).thenReturn(parent);
    ChannelHandlerContext context = mock(ChannelHandlerContext.class);
    Channel channel = mock(Channel.class);
    when(context.channel()).thenReturn(channel);
    when(channel.remoteAddress())
        .thenReturn(
            new InetSocketAddress(
                InetAddress.getByAddress(new byte[] {(byte) 192, 0, 2, 1}), 11210));

    Scope scope = CouchbaseMessageHandlerInstrumentation.WriteAdvice.onEnter(context, request);
    Peer peer = CouchbaseRequestPeers.consume(parent);
    assertThat(peer.getAddress()).isEqualTo("192.0.2.1");
    assertThat(peer.getPort()).isEqualTo(11210);

    CouchbaseMessageHandlerInstrumentation.WriteAdvice.onExit(scope);
    assertThat(CouchbaseRequestPeers.consume(parent)).isNull();
  }

  private static Stream<Arguments> requests() {
    return Stream.of(
        argumentSet("key-value request", mock(KeyValueRequest.class)),
        argumentSet("non-chunked HTTP request", mock(NonChunkedHttpRequest.class)),
        argumentSet("chunked HTTP request", mock(HttpRequest.class)));
  }
}
