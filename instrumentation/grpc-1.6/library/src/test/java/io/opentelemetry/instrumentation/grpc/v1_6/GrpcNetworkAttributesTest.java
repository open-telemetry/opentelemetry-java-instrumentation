/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_LOCAL_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_LOCAL_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TRANSPORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.Mockito.mock;

import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.GrpcClientNetworkAttributesGetter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GrpcNetworkAttributesTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void inetAddresses(boolean client) {
    GrpcRequest request =
        request(
            client, new InetSocketAddress("1.2.3.4", 8080), new InetSocketAddress("4.3.2.1", 9090));

    assertThat(extract(client, request))
        .containsOnly(
            entry(SERVER_ADDRESS, "logical.example"),
            entry(SERVER_PORT, 443L),
            entry(NETWORK_LOCAL_ADDRESS, "1.2.3.4"),
            entry(NETWORK_LOCAL_PORT, 8080L),
            entry(NETWORK_PEER_ADDRESS, "4.3.2.1"),
            entry(NETWORK_PEER_PORT, 9090L),
            entry(NETWORK_TYPE, "ipv4"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @EnabledForJreRange(min = JRE.JAVA_16)
  void unixAddresses(boolean client) throws Exception {
    Class<?> addressClass = Class.forName("java.net.UnixDomainSocketAddress");
    SocketAddress local =
        (SocketAddress) addressClass.getMethod("of", String.class).invoke(null, "/tmp/local.sock");
    SocketAddress peer =
        (SocketAddress) addressClass.getMethod("of", String.class).invoke(null, "/tmp/peer.sock");

    assertThat(extract(client, request(client, local, peer)))
        .containsOnly(
            entry(SERVER_ADDRESS, "logical.example"),
            entry(SERVER_PORT, 443L),
            entry(NETWORK_LOCAL_ADDRESS, "/tmp/local.sock"),
            entry(NETWORK_PEER_ADDRESS, "/tmp/peer.sock"),
            entry(NETWORK_TRANSPORT, "unix"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void unsupportedAddresses(boolean client) {
    SocketAddress address = new SocketAddress() {};

    assertThat(extract(client, request(client, address, address)))
        .containsOnly(entry(SERVER_ADDRESS, "logical.example"), entry(SERVER_PORT, 443L));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void inProcessAddresses(boolean client) throws Exception {
    // Newer gRPC versions move this type out of grpc-core into the optional grpc-inprocess
    // artifact.
    assumeFalse(testLatestDeps());
    SocketAddress address =
        Class.forName("io.grpc.inprocess.InProcessSocketAddress")
            .asSubclass(SocketAddress.class)
            .getConstructor(String.class)
            .newInstance("in-process");

    assertThat(extract(client, request(client, address, address)))
        .containsOnly(entry(SERVER_ADDRESS, "logical.example"), entry(SERVER_PORT, 443L));
  }

  private static GrpcRequest request(boolean client, SocketAddress local, SocketAddress peer) {
    if (client) {
      GrpcRequest request =
          GrpcRequest.createClientRequest(
              mock(MethodDescriptor.class), "logical.example:443", null);
      request.setLocalSocketAddress(local);
      request.setPeerSocketAddress(peer);
      return request;
    }
    return GrpcRequest.createServerRequest(
        mock(MethodDescriptor.class), null, peer, local, "logical.example:443");
  }

  private static Attributes extract(boolean client, GrpcRequest request) {
    NetworkAttributesGetter<GrpcRequest, Status> networkGetter;
    ServerAttributesGetter<GrpcRequest> serverGetter;
    if (client) {
      GrpcClientNetworkAttributesGetter getter = new GrpcClientNetworkAttributesGetter();
      networkGetter = getter;
      serverGetter = getter;
    } else {
      GrpcServerNetworkAttributesGetter getter = new GrpcServerNetworkAttributesGetter();
      networkGetter = getter;
      serverGetter = getter;
    }
    AttributesBuilder attributes = Attributes.builder();
    ServerAttributesExtractor.create(serverGetter).onStart(attributes, Context.root(), request);
    NetworkAttributesExtractor.create(networkGetter)
        .onEnd(attributes, Context.root(), request, Status.OK, null);
    return attributes.build();
  }
}
