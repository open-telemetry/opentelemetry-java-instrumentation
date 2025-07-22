/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConfigPropertiesUtilTest {
  @Test
  void propertyYamlPath() {
    assertThat(ConfigPropertiesUtil.propertyYamlPath("google.otel.auth.target.signals"))
        .isEqualTo(
            "'instrumentation/development' / 'java' / 'google' / 'otel' / 'auth' / 'target' / 'signals'");
  }
}
