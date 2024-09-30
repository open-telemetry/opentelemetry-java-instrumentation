/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StateMappingTest {

  @Test
  void empty() {
    StateMapping empty = StateMapping.builder().build();
    assertThat(empty).isSameAs(StateMapping.empty());

    assertThat(empty.isEmpty()).isTrue();
    assertThat(empty.getDefaultStateKey()).isNull();
    assertThat(empty.getStateKeys()).isEmpty();
    assertThat(empty.getStateValue("any")).isNull();
  }

  @Test
  void onlyDefault() {
    StateMapping mapping = StateMapping.builder().withDefaultState("default").build();

    assertThat(mapping.getDefaultStateKey()).isEqualTo("default");
    assertThat(mapping.getStateKeys()).containsExactly("default");
    assertThat(mapping.getStateValue("other")).isEqualTo("default");
  }

  @Test
  void tryDuplicateDefault() {
    assertThatThrownBy(
        () ->
            StateMapping.builder().withDefaultState("default").withDefaultState("default").build());
  }

  @Test
  void tryMissingDefault() {
    assertThatThrownBy(() -> StateMapping.builder().withMappedValue("value", "state").build());
  }

  @Test
  void mapValues() {
    StateMapping mapping =
        StateMapping.builder()
            .withDefaultState("unknown")
            .withMappedValue("value1", "ok")
            .withMappedValue("value1bis", "ok")
            .withMappedValue("value2", "ko")
            .build();

    assertThat(mapping.getStateKeys()).hasSize(3).contains("ok", "ko", "unknown");
    assertThat(mapping.getDefaultStateKey()).isEqualTo("unknown");

    assertThat(mapping.getStateValue("value1")).isEqualTo("ok");
    assertThat(mapping.getStateValue("value1bis")).isEqualTo("ok");
    assertThat(mapping.getStateValue("value2")).isEqualTo("ko");
    assertThat(mapping.getStateValue("other")).isEqualTo("unknown");
  }

  @Test
  void tryDuplicateMappings() {
    assertThatThrownBy(
        () ->
            StateMapping.builder()
                .withDefaultState("default")
                .withMappedValue("value", "state1")
                .withMappedValue("value", "state2")
                .build());
  }
}
