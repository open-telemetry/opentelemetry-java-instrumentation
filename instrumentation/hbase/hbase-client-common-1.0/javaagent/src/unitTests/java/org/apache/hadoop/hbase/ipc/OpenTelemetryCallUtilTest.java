/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.hadoop.hbase.ipc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.v1_0.HbaseRequest;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.v1_0.RequestAndContext;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

class OpenTelemetryCallUtilTest {

  @Test
  @EnabledForJreRange(min = JRE.JAVA_16)
  void preservesUnixPeerUntilCallCompletion() throws ReflectiveOperationException {
    Call call = mock(Call.class);
    RequestAndContext requestAndContext = requestAndContext();
    OpenTelemetryCallUtil.setRequestAndContext(call, requestAndContext);
    SocketAddress peer =
        (SocketAddress)
            Class.forName("java.net.UnixDomainSocketAddress")
                .getMethod("of", String.class)
                .invoke(null, "hbase.sock");

    OpenTelemetryCallUtil.setNetworkPeer(call, peer);

    assertThat(OpenTelemetryCallUtil.getAndClearRequestAndContext(call))
        .isSameAs(requestAndContext);
    assertThat(requestAndContext.getRequest().getNetworkPeerSocketAddress()).isSameAs(peer);
    assertThat(requestAndContext.getRequest().getServerTarget()).isEqualTo("logical-target");
  }

  @Test
  void setsResolvedInetSocketAddress() throws UnknownHostException {
    Call call = mock(Call.class);
    RequestAndContext requestAndContext = requestAndContext();
    OpenTelemetryCallUtil.setRequestAndContext(call, requestAndContext);

    InetAddress address = mock(InetAddress.class);
    InetSocketAddress networkPeer = new InetSocketAddress(address, 1234);
    OpenTelemetryCallUtil.setNetworkPeer(call, networkPeer);

    RequestAndContext updated = OpenTelemetryCallUtil.getAndClearRequestAndContext(call);
    assertThat(updated).isSameAs(requestAndContext);
    assertThat(updated.getRequest()).isSameAs(requestAndContext.getRequest());
    assertThat(updated.getRequest().getNetworkPeerSocketAddress()).isSameAs(networkPeer);
    assertThat(updated.getRequest().getServerTarget()).isEqualTo("logical-target");
    assertThat(updated.getScope()).isSameAs(requestAndContext.getScope());
    assertThat(updated.getContext()).isSameAs(requestAndContext.getContext());
    verify(address, never()).getHostAddress();
  }

  @Test
  void ignoresUnresolvedInetSocketAddress() {
    Call call = mock(Call.class);
    RequestAndContext requestAndContext = requestAndContext();
    OpenTelemetryCallUtil.setRequestAndContext(call, requestAndContext);

    OpenTelemetryCallUtil.setNetworkPeer(
        call, InetSocketAddress.createUnresolved("unresolved.example", 1234));

    assertThat(OpenTelemetryCallUtil.getAndClearRequestAndContext(call))
        .isSameAs(requestAndContext);
    assertThat(requestAndContext.getRequest().getNetworkPeerSocketAddress()).isNull();
  }

  @Test
  void ignoresNonInetSocketAddressAndNonCallMessages() {
    Call call = mock(Call.class);
    RequestAndContext requestAndContext = requestAndContext();
    OpenTelemetryCallUtil.setRequestAndContext(call, requestAndContext);
    SocketAddress nonInetAddress = new SocketAddress() {};

    OpenTelemetryCallUtil.setNetworkPeer(call, nonInetAddress);
    OpenTelemetryCallUtil.setNetworkPeer(
        new Object(), new InetSocketAddress(InetAddress.getLoopbackAddress(), 1234));

    assertThat(OpenTelemetryCallUtil.getAndClearRequestAndContext(call))
        .isSameAs(requestAndContext);
    assertThat(requestAndContext.getRequest().getNetworkPeerSocketAddress()).isNull();
  }

  @Test
  void updatesPeerWhenBufferedCallIsWrittenAgain() throws UnknownHostException {
    Call call = mock(Call.class);
    RequestAndContext requestAndContext = requestAndContext();
    OpenTelemetryCallUtil.setRequestAndContext(call, requestAndContext);

    InetSocketAddress firstPeer =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 20, 30, 40}), 1234);
    InetSocketAddress secondPeer =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 20, 30, 41}), 5678);
    OpenTelemetryCallUtil.setNetworkPeer(call, firstPeer);
    OpenTelemetryCallUtil.setNetworkPeer(call, secondPeer);

    RequestAndContext updated = OpenTelemetryCallUtil.getAndClearRequestAndContext(call);
    assertThat(updated).isSameAs(requestAndContext);
    HbaseRequest request = updated.getRequest();
    assertThat(request).isSameAs(requestAndContext.getRequest());
    assertThat(request.getNetworkPeerSocketAddress()).isSameAs(secondPeer);
  }

  @Test
  void ignoresCallAfterStateIsCleared() {
    Call call = mock(Call.class);
    OpenTelemetryCallUtil.setRequestAndContext(call, requestAndContext());
    OpenTelemetryCallUtil.getAndClearRequestAndContext(call);

    InetAddress address = mock(InetAddress.class);
    OpenTelemetryCallUtil.setNetworkPeer(call, new InetSocketAddress(address, 1234));

    assertThat(OpenTelemetryCallUtil.getAndClearRequestAndContext(call)).isNull();
    verify(address, never()).getHostAddress();
  }

  @Test
  void ignoresPeerUpdatesAfterCompletion() throws UnknownHostException {
    Call call = mock(Call.class);
    RequestAndContext requestAndContext = requestAndContext();
    OpenTelemetryCallUtil.setRequestAndContext(call, requestAndContext);

    InetSocketAddress firstPeer =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 20, 30, 40}), 1234);
    OpenTelemetryCallUtil.setNetworkPeer(call, firstPeer);
    assertThat(OpenTelemetryCallUtil.getAndClearRequestAndContext(call))
        .isSameAs(requestAndContext);

    OpenTelemetryCallUtil.setNetworkPeer(
        call, new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 20, 30, 41}), 5678));

    assertThat(requestAndContext.getRequest().getNetworkPeerSocketAddress()).isSameAs(firstPeer);
    assertThat(OpenTelemetryCallUtil.getAndClearRequestAndContext(call)).isNull();
  }

  private static RequestAndContext requestAndContext() {
    HbaseRequest request = HbaseRequest.create("Get", null, "logical-target", null);
    return RequestAndContext.create(request, mock(Scope.class), mock(Context.class));
  }
}
