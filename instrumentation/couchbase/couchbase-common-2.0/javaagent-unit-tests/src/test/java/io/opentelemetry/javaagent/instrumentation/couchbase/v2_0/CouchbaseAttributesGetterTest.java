/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0.CouchbaseRequestInfo;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class CouchbaseAttributesGetterTest {

  @Test
  void reportsTheConfiguredTargetRatherThanTheNodeThatAnswered() {
    CouchbaseRequestInfo request =
        CouchbaseRequestInfo.create(
            "bucket",
            DbServerTarget.builder(11210).addEndpoint("cluster.example", -1).build(),
            getClass(),
            "get");
    request.setNode(new InetSocketAddress("192.0.2.1", 32768));

    CouchbaseAttributesGetter getter = new CouchbaseAttributesGetter();
    assertThat(getter.getServerAddress(request)).isEqualTo("cluster.example");
    assertThat(getter.getServerPort(request)).isNull();
  }

  @Test
  void omitsTheDefaultPortOfASingleConfiguredSeed() {
    CouchbaseRequestInfo request =
        CouchbaseRequestInfo.create(
            "bucket",
            DbServerTarget.builder(11210).addEndpoint("node.example", 11210).build(),
            getClass(),
            "get");

    CouchbaseAttributesGetter getter = new CouchbaseAttributesGetter();
    assertThat(getter.getServerPort(request)).isNull();
  }

  @Test
  void reportsANonDefaultPort() {
    CouchbaseRequestInfo request =
        CouchbaseRequestInfo.create(
            "bucket",
            DbServerTarget.builder(11210).addEndpoint("node.example", 11211).build(),
            getClass(),
            "get");

    CouchbaseAttributesGetter getter = new CouchbaseAttributesGetter();
    assertThat(getter.getServerPort(request)).isEqualTo(11211);
  }

  @Test
  void doesNotReportTheNodeThatAnsweredAtStart() {
    CouchbaseRequestInfo request = CouchbaseRequestInfo.create("bucket", null, getClass(), "get");
    request.setNode(new InetSocketAddress("192.0.2.1", 32768));

    CouchbaseAttributesGetter getter = new CouchbaseAttributesGetter();
    assertThat(getter.getServerAddress(request)).isNull();
    assertThat(getter.getServerPort(request)).isNull();
  }

  @Test
  void doesNotReportTheNodeThatAnsweredAsServerAtEnd() {
    CouchbaseRequestInfo request = CouchbaseRequestInfo.create("bucket", null, getClass(), "get");
    request.setNode(new InetSocketAddress("192.0.2.1", 32768));

    AttributesBuilder attributes = Attributes.builder();
    DbClientAttributesExtractor.create(new CouchbaseAttributesGetter())
        .onEnd(attributes, Context.root(), request, null, null);

    assertThat(attributes.build().get(SERVER_ADDRESS)).isNull();
    assertThat(attributes.build().get(SERVER_PORT)).isNull();
  }

  @Test
  void preservesTheConfiguredTarget() {
    CouchbaseRequestInfo request =
        CouchbaseRequestInfo.create(
            "bucket",
            DbServerTarget.builder(11210).addEndpoint("cluster.example", -1).build(),
            getClass(),
            "get");
    request.setNode(new InetSocketAddress("192.0.2.1", 32768));

    AttributesBuilder attributes = Attributes.builder();
    AttributesExtractor<CouchbaseRequestInfo, Void> extractor =
        DbClientAttributesExtractor.create(new CouchbaseAttributesGetter());
    extractor.onStart(attributes, Context.root(), request);
    extractor.onEnd(attributes, Context.root(), request, null, null);

    assertThat(attributes.build().get(SERVER_ADDRESS)).isEqualTo("cluster.example");
    assertThat(attributes.build().get(SERVER_PORT)).isNull();
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
  void reportsTheLastContactedPeer() {
    CouchbaseRequestInfo request = CouchbaseRequestInfo.create("bucket", null, getClass(), "get");
    InetSocketAddress firstPeer = new InetSocketAddress("192.0.2.1", 32768);
    InetSocketAddress secondPeer = new InetSocketAddress("192.0.2.2", 32769);

    CouchbaseAttributesGetter getter = new CouchbaseAttributesGetter();
    request.setNode(firstPeer);
    assertThat(getter.getNetworkPeerInetSocketAddress(request, null)).isEqualTo(firstPeer);

    request.setNode(secondPeer);
    assertThat(getter.getNetworkPeerInetSocketAddress(request, null)).isEqualTo(secondPeer);
  }

  @Test
  void keepsTheLastContactedNodeOfEverySubscriptionApart() {
    CouchbaseRequestInfo request =
        CouchbaseRequestInfo.create(
            "bucket",
            DbServerTarget.builder(11210).addEndpoint("cluster.example", -1).build(),
            getClass(),
            "get");
    InetSocketAddress firstPeer = new InetSocketAddress("192.0.2.1", 32768);
    InetSocketAddress secondPeer = new InetSocketAddress("192.0.2.2", 32769);
    request.setNode(secondPeer);

    CouchbaseRequestInfo copy = request.copySupplier().get();
    copy.setNode(firstPeer);

    CouchbaseAttributesGetter getter = new CouchbaseAttributesGetter();
    assertThat(getter.getNetworkPeerInetSocketAddress(request, null)).isEqualTo(secondPeer);
    assertThat(getter.getNetworkPeerInetSocketAddress(copy, null)).isEqualTo(firstPeer);
  }

  @Test
  void copyCarriesTheConfiguredTargetOfTheClientThatIssuedIt() {
    CouchbaseRequestInfo request =
        CouchbaseRequestInfo.create(
            "bucket",
            DbServerTarget.builder(11210).addEndpoint("cluster.example", -1).build(),
            getClass(),
            "get");

    CouchbaseRequestInfo copy = request.copySupplier().get();
    assertThat(copy.getBucket()).isEqualTo("bucket");
    assertThat(copy.getOperation()).isEqualTo(request.getOperation());
    assertThat(copy.getServerTarget()).isSameAs(request.getServerTarget());
    assertThat(copy.getNode()).isNull();
  }
}
