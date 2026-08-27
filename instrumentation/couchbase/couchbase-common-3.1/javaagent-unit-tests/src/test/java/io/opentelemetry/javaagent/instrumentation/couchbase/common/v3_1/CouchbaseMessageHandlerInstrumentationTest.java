/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.junit.jupiter.api.Test;

class CouchbaseMessageHandlerInstrumentationTest {

  @Test
  void capturesKeyValueRequestPeer() throws UnknownHostException {
    capturesRequestPeer(mock(KeyValueRequest.class));
  }

  @Test
  void capturesHttpRequestPeers() throws UnknownHostException {
    capturesRequestPeer(mock(NonChunkedHttpRequest.class));
    capturesRequestPeer(mock(HttpRequest.class));
  }

  private static void capturesRequestPeer(Request<?> request) throws UnknownHostException {
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
}
