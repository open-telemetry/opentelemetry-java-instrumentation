/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.CouchbaseServerTarget;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0.CouchbaseRequestInfo;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class CouchbaseAttributesGetterTest {

  @Test
  void reportsTheConfiguredTargetRatherThanTheNodeThatAnswered() {
    CouchbaseRequestInfo request =
        CouchbaseRequestInfo.create("bucket", target("cluster.example", 0), getClass(), "get");
    request.setNode(new InetSocketAddress("192.0.2.1", 32768), "node.example:11210");

    CouchbaseAttributesGetter getter = new CouchbaseAttributesGetter();
    assertThat(getter.getServerAddress(request))
        .isEqualTo(emitStableDatabaseSemconv() ? "cluster.example" : null);
    assertThat(getter.getServerPort(request)).isNull();
  }

  @Test
  void reportsThePortOfASingleConfiguredSeed() {
    CouchbaseRequestInfo request =
        CouchbaseRequestInfo.create("bucket", target("node.example", 11210), getClass(), "get");

    CouchbaseAttributesGetter getter = new CouchbaseAttributesGetter();
    assertThat(getter.getServerPort(request)).isEqualTo(emitStableDatabaseSemconv() ? 11210 : null);
  }

  @Test
  void doesNotReportTheNodeThatAnsweredAtStart() {
    CouchbaseRequestInfo request = CouchbaseRequestInfo.create("bucket", null, getClass(), "get");
    request.setNode(new InetSocketAddress("192.0.2.1", 32768), "node.example:11210");

    CouchbaseAttributesGetter getter = new CouchbaseAttributesGetter();
    assertThat(getter.getServerAddress(request)).isNull();
    assertThat(getter.getServerPort(request)).isNull();
  }

  @Test
  void reportsTheNodeThatAnsweredAtEndOnlyInLegacyMode() {
    CouchbaseRequestInfo request = CouchbaseRequestInfo.create("bucket", null, getClass(), "get");
    request.setNode(new InetSocketAddress("192.0.2.1", 32768), "node.example:11210");

    AttributesBuilder attributes = Attributes.builder();
    new CouchbaseAttributesGetter().onEnd(attributes, Context.root(), request, null, null);

    if (emitStableDatabaseSemconv()) {
      assertThat(attributes.build()).isEqualTo(Attributes.empty());
    } else {
      assertThat(attributes.build().get(SERVER_ADDRESS)).isEqualTo("node.example");
      assertThat(attributes.build().get(SERVER_PORT)).isEqualTo(11210L);
    }
  }

  @Test
  void preservesTheConfiguredTargetInStableMode() {
    CouchbaseRequestInfo request =
        CouchbaseRequestInfo.create("bucket", target("cluster.example", 0), getClass(), "get");
    request.setNode(new InetSocketAddress("192.0.2.1", 32768), "node.example:11210");

    AttributesBuilder attributes = Attributes.builder();
    new CouchbaseAttributesGetter().onEnd(attributes, Context.root(), request, null, null);

    if (emitStableDatabaseSemconv()) {
      assertThat(attributes.build()).isEqualTo(Attributes.empty());
    } else {
      assertThat(attributes.build().get(SERVER_ADDRESS)).isEqualTo("node.example");
      assertThat(attributes.build().get(SERVER_PORT)).isEqualTo(11210L);
    }
  }

  @Test
  void carriesNoServerWhenNeitherIsKnown() {
    CouchbaseRequestInfo request = CouchbaseRequestInfo.create("bucket", null, getClass(), "get");

    CouchbaseAttributesGetter getter = new CouchbaseAttributesGetter();
    assertThat(getter.getServerAddress(request)).isNull();
    assertThat(getter.getServerPort(request)).isNull();
    assertThat(getter.getNetworkPeerInetSocketAddress(request, null)).isNull();
  }

  @Test
  void keepsTheSocketAndTheAddressOfTheLastContactedNodeTogether() {
    CouchbaseRequestInfo request = CouchbaseRequestInfo.create("bucket", null, getClass(), "get");
    InetSocketAddress firstPeer = new InetSocketAddress("192.0.2.1", 32768);
    InetSocketAddress secondPeer = new InetSocketAddress("192.0.2.2", 32769);

    CouchbaseAttributesGetter getter = new CouchbaseAttributesGetter();
    request.setNode(firstPeer, "2001:db8::1:11210");
    assertThat(getter.getNetworkPeerInetSocketAddress(request, null)).isEqualTo(firstPeer);
    assertThat(request.getNode().getBackendAddress()).isEqualTo("2001:db8::1");
    assertThat(request.getNode().getBackendPort()).isEqualTo(11210);

    request.setNode(secondPeer, "[2001:db8::2]:11211");
    assertThat(getter.getNetworkPeerInetSocketAddress(request, null)).isEqualTo(secondPeer);
    assertThat(request.getNode().getBackendAddress()).isEqualTo("2001:db8::2");
    assertThat(request.getNode().getBackendPort()).isEqualTo(11211);
  }

  @Test
  void keepsTheLastContactedNodeOfEverySubscriptionApart() {
    CouchbaseRequestInfo request =
        CouchbaseRequestInfo.create("bucket", target("cluster.example", 0), getClass(), "get");
    InetSocketAddress firstPeer = new InetSocketAddress("192.0.2.1", 32768);
    InetSocketAddress secondPeer = new InetSocketAddress("192.0.2.2", 32769);
    request.setNode(secondPeer, "second.example:11211");

    CouchbaseRequestInfo copy = request.copySupplier().get();
    copy.setNode(firstPeer, "first.example:11210");

    CouchbaseAttributesGetter getter = new CouchbaseAttributesGetter();
    assertThat(getter.getNetworkPeerInetSocketAddress(request, null)).isEqualTo(secondPeer);
    assertThat(request.getNode().getBackendAddress()).isEqualTo("second.example");
    assertThat(getter.getNetworkPeerInetSocketAddress(copy, null)).isEqualTo(firstPeer);
    assertThat(copy.getNode().getBackendAddress()).isEqualTo("first.example");
  }

  @Test
  void copyCarriesTheConfiguredTargetOfTheClientThatIssuedIt() {
    CouchbaseRequestInfo request =
        CouchbaseRequestInfo.create("bucket", target("cluster.example", 0), getClass(), "get");

    CouchbaseRequestInfo copy = request.copySupplier().get();
    assertThat(copy.getBucket()).isEqualTo("bucket");
    assertThat(copy.getOperation()).isEqualTo(request.getOperation());
    assertThat(copy.getServerTarget()).isSameAs(request.getServerTarget());
    assertThat(copy.getNode()).isNull();
  }

  private static CouchbaseServerTarget target(String host, int port) {
    CouchbaseServerTarget.Builder builder = CouchbaseServerTarget.builder();
    builder.addSeed(host, port);
    return builder.build();
  }
}
