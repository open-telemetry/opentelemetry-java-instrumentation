/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.hadoop.hbase.ipc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseRequest;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.RequestAndContext;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.junit.jupiter.api.Test;

class OpenTelemetryCallUtilTest {

  @Test
  void setsResolvedInetSocketAddress() throws Exception {
    Call call = mock(Call.class);
    RequestAndContext requestAndContext = requestAndContext();
    OpenTelemetryCallUtil.setRequestAndContext(call, requestAndContext);

    OpenTelemetryCallUtil.setNetworkPeer(
        call, new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 20, 30, 40}), 1234));

    RequestAndContext updated = OpenTelemetryCallUtil.getAndClearRequestAndContext(call);
    assertThat(updated).isNotNull();
    assertThat(updated.getRequest().getNetworkPeerAddress()).isEqualTo("10.20.30.40");
    assertThat(updated.getRequest().getNetworkPeerPort()).isEqualTo(1234);
    assertThat(updated.getRequest().getServerTarget()).isEqualTo("logical-target");
    assertThat(updated.getScope()).isSameAs(requestAndContext.getScope());
    assertThat(updated.getContext()).isSameAs(requestAndContext.getContext());
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
  }

  @Test
  void updatesPeerWhenBufferedCallIsWrittenAgain() throws Exception {
    Call call = mock(Call.class);
    OpenTelemetryCallUtil.setRequestAndContext(call, requestAndContext());

    OpenTelemetryCallUtil.setNetworkPeer(
        call, new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 20, 30, 40}), 1234));
    OpenTelemetryCallUtil.setNetworkPeer(
        call, new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 20, 30, 41}), 5678));

    HbaseRequest request = OpenTelemetryCallUtil.getAndClearRequestAndContext(call).getRequest();
    assertThat(request.getNetworkPeerAddress()).isEqualTo("10.20.30.41");
    assertThat(request.getNetworkPeerPort()).isEqualTo(5678);
  }

  @Test
  void ignoresCallAfterStateIsCleared() {
    Call call = mock(Call.class);
    OpenTelemetryCallUtil.setRequestAndContext(call, requestAndContext());
    OpenTelemetryCallUtil.getAndClearRequestAndContext(call);

    OpenTelemetryCallUtil.setNetworkPeer(
        call, new InetSocketAddress(InetAddress.getLoopbackAddress(), 1234));

    assertThat(OpenTelemetryCallUtil.getAndClearRequestAndContext(call)).isNull();
  }

  private static RequestAndContext requestAndContext() {
    HbaseRequest request =
        HbaseRequest.create(
            "Get", null, "user", "logical-host", 4321, "logical-target", null);
    return RequestAndContext.create(request, mock(Scope.class), mock(Context.class));
  }
}
