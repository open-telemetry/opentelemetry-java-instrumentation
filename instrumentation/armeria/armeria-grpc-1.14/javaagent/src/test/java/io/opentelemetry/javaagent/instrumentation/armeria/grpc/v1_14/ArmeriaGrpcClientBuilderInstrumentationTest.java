/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.grpc.v1_14;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class ArmeriaGrpcClientBuilderInstrumentationTest {

  @Test
  void createsTargetFromRawIpv6Authority() {
    URI uri = URI.create("http://user@[fe80::1%25eth0]:8080");

    assertThat(ArmeriaGrpcClientBuilderInstrumentation.BuildAdvice.toGrpcTarget(uri))
        .isEqualTo("dns:///%5Bfe80::1%25eth0%5D:8080");
  }
}
