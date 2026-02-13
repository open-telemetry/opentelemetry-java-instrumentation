/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // testing deprecated PeerServiceResolver
class PeerServiceResolverTest {

  @Test
  void test() {
    Map<String, String> peerServiceMapping = new HashMap<>();
    peerServiceMapping.put("example.com:8080", "myService");
    peerServiceMapping.put("example.com", "myServiceBase");
    peerServiceMapping.put("1.2.3.4", "someOtherService");
    peerServiceMapping.put("1.2.3.4:8080/api", "someOtherService8080");
    peerServiceMapping.put("1.2.3.4/api", "someOtherServiceAPI");

    PeerServiceResolver peerServiceResolver = PeerServiceResolver.create(peerServiceMapping);

    assertThat(peerServiceResolver.resolveService("example.com", null, null))
        .isEqualTo("myServiceBase");
    assertThat(peerServiceResolver.resolveService("example.com", 8080, () -> "/"))
        .isEqualTo("myService");
    assertThat(peerServiceResolver.resolveService("1.2.3.4", 8080, () -> "/api"))
        .isEqualTo("someOtherService8080");
    assertThat(peerServiceResolver.resolveService("1.2.3.4", 9000, () -> "/api"))
        .isEqualTo("someOtherService");
    assertThat(peerServiceResolver.resolveService("1.2.3.4", 8080, () -> null))
        .isEqualTo("someOtherService");
    assertThat(peerServiceResolver.resolveService("1.2.3.4", null, () -> "/api"))
        .isEqualTo("someOtherServiceAPI");
  }
}
