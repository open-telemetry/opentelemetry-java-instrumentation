/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import org.junit.jupiter.api.Test;

public class OkHttp3StableSemconvTest extends OkHttp3Test {

  @Test
  void stableSemconvEnabled() {
    assertThat(SemconvStability.emitStableHttpSemconv()).isTrue();
  }
}
