/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

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

    assertEquals("myServiceBase", peerServiceResolver.resolveService("example.com", null, null));
    assertEquals("myService", peerServiceResolver.resolveService("example.com", 8080, () -> "/"));
    assertEquals(
        "someOtherService8080", peerServiceResolver.resolveService("1.2.3.4", 8080, () -> "/api"));
    assertEquals(
        "someOtherService", peerServiceResolver.resolveService("1.2.3.4", 9000, () -> "/api"));
    assertEquals(
        "someOtherService", peerServiceResolver.resolveService("1.2.3.4", 8080, () -> null));
    assertEquals(
        "someOtherServiceAPI", peerServiceResolver.resolveService("1.2.3.4", null, () -> "/api"));
  }
}
