/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.hadoop.hbase.ipc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseCallUtil;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseRequest;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.RequestAndContext;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class HbaseCallUtilTest {

  @Test
  void setsResolvedInetSocketAddress() throws UnknownHostException {
    Call call = mock(Call.class);
    RequestAndContext requestAndContext = requestAndContext();
    HbaseCallUtil.setRequestAndContext(call, requestAndContext);

    InetSocketAddress networkPeer =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 20, 30, 40}), 1234);
    HbaseCallUtil.setNetworkPeer(call, networkPeer);

    RequestAndContext updated = HbaseCallUtil.getAndClearRequestAndContext(call);
    assertThat(updated).isSameAs(requestAndContext);
    assertThat(updated.getRequest()).isSameAs(requestAndContext.getRequest());
    assertThat(updated.getRequest().getNetworkPeerInetSocketAddress()).isSameAs(networkPeer);
    assertThat(updated.getRequest().getServerTarget()).isEqualTo("logical-target");
    assertThat(updated.getScope()).isSameAs(requestAndContext.getScope());
    assertThat(updated.getContext()).isSameAs(requestAndContext.getContext());
  }

  @Test
  void ignoresUnresolvedInetSocketAddress() {
    Call call = mock(Call.class);
    RequestAndContext requestAndContext = requestAndContext();
    HbaseCallUtil.setRequestAndContext(call, requestAndContext);

    HbaseCallUtil.setNetworkPeer(
        call, InetSocketAddress.createUnresolved("unresolved.example", 1234));

    assertThat(HbaseCallUtil.getAndClearRequestAndContext(call)).isSameAs(requestAndContext);
    assertThat(requestAndContext.getRequest().getNetworkPeerInetSocketAddress()).isNull();
  }

  @Test
  void ignoresNonInetSocketAddressAndNonCallMessages() {
    Call call = mock(Call.class);
    RequestAndContext requestAndContext = requestAndContext();
    HbaseCallUtil.setRequestAndContext(call, requestAndContext);
    SocketAddress nonInetAddress = new SocketAddress() {};

    HbaseCallUtil.setNetworkPeer(call, nonInetAddress);
    HbaseCallUtil.setNetworkPeer(
        new Object(), new InetSocketAddress(InetAddress.getLoopbackAddress(), 1234));

    assertThat(HbaseCallUtil.getAndClearRequestAndContext(call)).isSameAs(requestAndContext);
    assertThat(requestAndContext.getRequest().getNetworkPeerInetSocketAddress()).isNull();
  }

  @Test
  void updatesPeerWhenBufferedCallIsWrittenAgain() throws UnknownHostException {
    Call call = mock(Call.class);
    RequestAndContext requestAndContext = requestAndContext();
    HbaseCallUtil.setRequestAndContext(call, requestAndContext);

    InetSocketAddress firstPeer =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 20, 30, 40}), 1234);
    InetSocketAddress secondPeer =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 20, 30, 41}), 5678);
    HbaseCallUtil.setNetworkPeer(call, firstPeer);
    HbaseCallUtil.setNetworkPeer(call, secondPeer);

    RequestAndContext updated = HbaseCallUtil.getAndClearRequestAndContext(call);
    assertThat(updated).isSameAs(requestAndContext);
    HbaseRequest request = updated.getRequest();
    assertThat(request).isSameAs(requestAndContext.getRequest());
    assertThat(request.getNetworkPeerInetSocketAddress()).isSameAs(secondPeer);
  }

  @Test
  void ignoresCallAfterStateIsCleared() {
    Call call = mock(Call.class);
    HbaseCallUtil.setRequestAndContext(call, requestAndContext());
    HbaseCallUtil.getAndClearRequestAndContext(call);

    HbaseCallUtil.setNetworkPeer(
        call, new InetSocketAddress(InetAddress.getLoopbackAddress(), 1234));

    assertThat(HbaseCallUtil.getAndClearRequestAndContext(call)).isNull();
  }

  @Test
  void ignoresPeerUpdatesAfterCompletion() throws UnknownHostException {
    Call call = mock(Call.class);
    RequestAndContext requestAndContext = requestAndContext();
    HbaseCallUtil.setRequestAndContext(call, requestAndContext);

    InetSocketAddress firstPeer =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 20, 30, 40}), 1234);
    HbaseCallUtil.setNetworkPeer(call, firstPeer);
    assertThat(HbaseCallUtil.getAndClearRequestAndContext(call)).isSameAs(requestAndContext);

    HbaseCallUtil.setNetworkPeer(
        call, new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 20, 30, 41}), 5678));

    assertThat(requestAndContext.getRequest().getNetworkPeerInetSocketAddress())
        .isSameAs(firstPeer);
    assertThat(HbaseCallUtil.getAndClearRequestAndContext(call)).isNull();
  }

  private static RequestAndContext requestAndContext() {
    HbaseRequest request = HbaseRequest.create("Get", null, "logical-target", null);
    return RequestAndContext.create(request, mock(Scope.class), mock(Context.class));
  }
}
