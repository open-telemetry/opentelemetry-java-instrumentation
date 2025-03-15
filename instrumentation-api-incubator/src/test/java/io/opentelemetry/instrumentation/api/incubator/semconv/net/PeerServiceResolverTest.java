/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PeerServiceResolverTest {

  @Test
  void testResolveService() {
    Map<String, String> peerServiceMapping = new HashMap<>();
    peerServiceMapping.put("example.com:8080", "myService");
    peerServiceMapping.put("example.com", "myServiceBase");
    peerServiceMapping.put("1.2.3.4", "someOtherService");
    peerServiceMapping.put("1.2.3.4:8080/api", "someOtherService8080");
    peerServiceMapping.put("1.2.3.4/api", "someOtherServiceAPI");

    peerServiceMapping.put("*.example.com:8080", "myServiceGlob");
    peerServiceMapping.put("*.example.com", "myServiceBaseGlob");
    peerServiceMapping.put("1.2.3.*?", "someOtherServiceGlob");
    peerServiceMapping.put("1.2.3.4?:8080/api", "someOtherService8080Glob");
    peerServiceMapping.put("1.2.3.4?/api", "someOtherServiceAPIGlob");

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

    assertThat(peerServiceResolver.resolveService("any.example.com", null, null))
        .isEqualTo("myServiceBaseGlob");
    assertThat(peerServiceResolver.resolveService("any.example.com", 8080, () -> "/"))
        .isEqualTo("myServiceGlob");
    assertThat(peerServiceResolver.resolveService("1.2.3.44", 8080, () -> "/api"))
        .isEqualTo("someOtherService8080Glob");
    assertThat(peerServiceResolver.resolveService("1.2.3.44", 9000, () -> "/api"))
        .isEqualTo("someOtherServiceGlob");
    assertThat(peerServiceResolver.resolveService("1.2.3.44", 8080, () -> null))
        .isEqualTo("someOtherServiceGlob");
    assertThat(peerServiceResolver.resolveService("1.2.3.44", null, () -> "/api"))
        .isEqualTo("someOtherServiceAPIGlob");
  }
}
