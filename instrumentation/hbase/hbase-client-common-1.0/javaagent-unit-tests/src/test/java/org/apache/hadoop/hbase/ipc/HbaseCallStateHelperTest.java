/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.hadoop.hbase.ipc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseCallStateHelper;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseRequest;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.RequestAndContext;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class HbaseCallStateHelperTest {

  @Test
  void setsResolvedInetSocketAddress() throws UnknownHostException {
    Call call = mock(Call.class);
    RequestAndContext requestAndContext = requestAndContext();
    HbaseCallStateHelper.set(call, requestAndContext);

    InetSocketAddress networkPeer =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 20, 30, 40}), 1234);
    HbaseCallStateHelper.updateNetworkPeer(call, networkPeer);

    RequestAndContext updated = HbaseCallStateHelper.getAndClear(call);
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
    HbaseCallStateHelper.set(call, requestAndContext);

    HbaseCallStateHelper.updateNetworkPeer(
        call, InetSocketAddress.createUnresolved("unresolved.example", 1234));

    assertThat(HbaseCallStateHelper.getAndClear(call)).isSameAs(requestAndContext);
    assertThat(requestAndContext.getRequest().getNetworkPeerInetSocketAddress()).isNull();
  }

  @Test
  void ignoresNonInetSocketAddress() {
    Call call = mock(Call.class);
    RequestAndContext requestAndContext = requestAndContext();
    HbaseCallStateHelper.set(call, requestAndContext);
    SocketAddress nonInetAddress = new SocketAddress() {};

    HbaseCallStateHelper.updateNetworkPeer(call, nonInetAddress);

    assertThat(HbaseCallStateHelper.getAndClear(call)).isSameAs(requestAndContext);
    assertThat(requestAndContext.getRequest().getNetworkPeerInetSocketAddress()).isNull();
  }

  @Test
  void updatesPeerWhenBufferedCallIsWrittenAgain() throws UnknownHostException {
    Call call = mock(Call.class);
    RequestAndContext requestAndContext = requestAndContext();
    HbaseCallStateHelper.set(call, requestAndContext);

    InetSocketAddress firstPeer =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 20, 30, 40}), 1234);
    InetSocketAddress secondPeer =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 20, 30, 41}), 5678);
    HbaseCallStateHelper.updateNetworkPeer(call, firstPeer);
    HbaseCallStateHelper.updateNetworkPeer(call, secondPeer);

    RequestAndContext updated = HbaseCallStateHelper.getAndClear(call);
    assertThat(updated).isSameAs(requestAndContext);
    HbaseRequest request = updated.getRequest();
    assertThat(request).isSameAs(requestAndContext.getRequest());
    assertThat(request.getNetworkPeerInetSocketAddress()).isSameAs(secondPeer);
  }

  @Test
  void ignoresCallAfterStateIsCleared() {
    Call call = mock(Call.class);
    HbaseCallStateHelper.set(call, requestAndContext());
    HbaseCallStateHelper.getAndClear(call);

    HbaseCallStateHelper.updateNetworkPeer(
        call, new InetSocketAddress(InetAddress.getLoopbackAddress(), 1234));

    assertThat(HbaseCallStateHelper.getAndClear(call)).isNull();
  }

  @Test
  void ignoresPeerUpdatesAfterCompletion() throws UnknownHostException {
    Call call = mock(Call.class);
    RequestAndContext requestAndContext = requestAndContext();
    HbaseCallStateHelper.set(call, requestAndContext);

    InetSocketAddress firstPeer =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 20, 30, 40}), 1234);
    HbaseCallStateHelper.updateNetworkPeer(call, firstPeer);
    assertThat(HbaseCallStateHelper.getAndClear(call)).isSameAs(requestAndContext);

    HbaseCallStateHelper.updateNetworkPeer(
        call, new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 20, 30, 41}), 5678));

    assertThat(requestAndContext.getRequest().getNetworkPeerInetSocketAddress())
        .isSameAs(firstPeer);
    assertThat(HbaseCallStateHelper.getAndClear(call)).isNull();
  }

  private static RequestAndContext requestAndContext() {
    HbaseRequest request =
        HbaseRequest.create("Get", null, "user", "logical-host", 4321, "logical-target", null);
    return RequestAndContext.create(request, mock(Scope.class), mock(Context.class));
  }
}
