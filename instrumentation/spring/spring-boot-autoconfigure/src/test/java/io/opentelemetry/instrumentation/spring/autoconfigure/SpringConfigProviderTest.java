/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfiguration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SpringConfigProviderTest {

  @Test
  void wrapsExistingInstrumentationConfig() {
    Map<String, Object> foo = new HashMap<>();
    foo.put("bool_key", "true");
    foo.put("double_key", "4.14");
    foo.put("int_key", "42");

    Map<String, Object> java = new HashMap<>();
    java.put("foo", foo);

    Map<String, Object> instrumentation = new HashMap<>();
    instrumentation.put("java", java);

    Map<String, Object> root = new HashMap<>();
    root.put("instrumentation/development", instrumentation);

    DeclarativeConfigProperties configProperties =
        DeclarativeConfiguration.toConfigProperties(root);

    SpringConfigProvider configProvider =
        SpringConfigProvider.create(configProperties.get("instrumentation/development"));

    DeclarativeConfigProperties fooConfig =
        configProvider.getInstrumentationConfig().get("java").get("foo");
    assertThat(fooConfig.getBoolean("bool_key")).isTrue();
    assertThat(fooConfig.getDouble("double_key")).isEqualTo(4.14);
    assertThat(fooConfig.getLong("int_key")).isEqualTo(42);
  }
}
