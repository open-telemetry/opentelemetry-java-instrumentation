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
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0.CouchbaseRequestInfo;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class CouchbaseAttributesGetterTest {

  @Test
  void omitsServerWhenOperationTargetsMultipleServers() {
    CouchbaseRequestInfo request = CouchbaseRequestInfo.create("bucket", getClass(), "operation");
    InetSocketAddress first = InetSocketAddress.createUnresolved("first.example", 11210);
    InetSocketAddress second = InetSocketAddress.createUnresolved("second.example", 11210);

    request.setPeerAddress(first);
    request.setPeerAddress(first);
    assertThat(request.getPeerAddress()).isEqualTo(first);

    request.setPeerAddress(second);
    request.setPeerAddress(first);
    assertThat(request.getPeerAddress()).isNull();

    CouchbaseAttributesGetter getter = new CouchbaseAttributesGetter();
    assertThat(getter.getNetworkPeerInetSocketAddress(request, null)).isNull();

    AttributesBuilder attributes = Attributes.builder();
    getter.onEnd(attributes, Context.root(), request, null, null);

    assertThat(attributes.build().asMap()).doesNotContainKeys(SERVER_ADDRESS, SERVER_PORT);
  }

  @Test
  void copyResetsPerSubscriptionState() {
    CouchbaseRequestInfo request = CouchbaseRequestInfo.create("bucket", getClass(), "operation");
    InetSocketAddress first = InetSocketAddress.createUnresolved("first.example", 11210);
    InetSocketAddress second = InetSocketAddress.createUnresolved("second.example", 11210);

    // Drive the original into an ambiguous state
    request.setPeerAddress(first);
    request.setPeerAddress(second);
    assertThat(request.getPeerAddress()).isNull();

    // A copy should start with clean mutable state
    CouchbaseRequestInfo copy = request.copySupplier().get();
    copy.setPeerAddress(first);
    assertThat(copy.getPeerAddress()).isEqualTo(first);

    // Original remains ambiguous and is unaffected by the copy
    assertThat(request.getPeerAddress()).isNull();
  }
}
