/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpclient.v5_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.net.SocketAddress;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class Vertx5HttpAttributesGetterTest {

  private final Vertx5HttpAttributesGetter getter = new Vertx5HttpAttributesGetter();

  @Test
  void snapshotsIpAddressRatherThanHostName() throws Exception {
    HttpClientResponse response = mock(HttpClientResponse.class);
    HttpConnection connection = mock(HttpConnection.class);
    InetAddress ip = InetAddress.getByAddress("example.test", new byte[] {127, 0, 0, 1});
    SocketAddress address = SocketAddress.inetSocketAddress(new InetSocketAddress(ip, 8080));
    assertThat(address.host()).isEqualTo("example.test");
    when(connection.remoteAddress()).thenReturn(address);

    HttpClientResponseImplInstrumentation.ConstructorAdvice.setPeerAddress(response, connection);
    when(connection.remoteAddress()).thenReturn(null);

    assertThat(getter.getNetworkPeerAddress(null, response)).isEqualTo("127.0.0.1");
    assertThat(getter.getNetworkPeerPort(null, response)).isEqualTo(8080);
  }

  @Test
  void preservesMissingAddressAndDomainSocketPort() {
    HttpClientResponse response = mock(HttpClientResponse.class);
    HttpConnection connection = mock(HttpConnection.class);

    assertThat(getter.getNetworkPeerAddress(null, null)).isNull();
    assertThat(getter.getNetworkPeerPort(null, null)).isNull();
    HttpClientResponseImplInstrumentation.ConstructorAdvice.setPeerAddress(response, null);
    assertThat(getter.getNetworkPeerAddress(null, response)).isNull();
    assertThat(getter.getNetworkPeerPort(null, response)).isNull();

    HttpClientResponseImplInstrumentation.ConstructorAdvice.setPeerAddress(response, connection);
    assertThat(getter.getNetworkPeerAddress(null, response)).isNull();
    assertThat(getter.getNetworkPeerPort(null, response)).isNull();

    when(connection.remoteAddress())
        .thenReturn(SocketAddress.inetSocketAddress(8080, "example.test"));
    HttpClientResponseImplInstrumentation.ConstructorAdvice.setPeerAddress(response, connection);
    assertThat(getter.getNetworkPeerAddress(null, response)).isNull();
    assertThat(getter.getNetworkPeerPort(null, response)).isEqualTo(8080);

    when(connection.remoteAddress()).thenReturn(SocketAddress.domainSocketAddress("/tmp/socket"));
    HttpClientResponseImplInstrumentation.ConstructorAdvice.setPeerAddress(response, connection);
    assertThat(getter.getNetworkPeerAddress(null, response)).isNull();
    assertThat(getter.getNetworkPeerPort(null, response)).isEqualTo(-1);
  }
}
