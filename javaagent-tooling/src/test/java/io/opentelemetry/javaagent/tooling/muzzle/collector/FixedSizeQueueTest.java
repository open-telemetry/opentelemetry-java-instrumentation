/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle.collector;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FixedSizeQueueTest {
  @Test
  void shouldRemoveOldestItems() {
    // given
    FixedSizeQueue<String> underTest = new FixedSizeQueue<>(2);

    // when
    underTest.add("class 1");
    underTest.add("class 2");

    // then
    assertThat(underTest).containsExactly("class 1", "class 2");
    assertThat(underTest.hasMaxSize()).isTrue();

    // when
    underTest.add("class 3");

    // then
    assertThat(underTest).containsExactly("class 2", "class 3");
    assertThat(underTest.hasMaxSize()).isTrue();
  }

  @Test
  void shouldNotThrowOnPopIfEmpty() {
    // given
    FixedSizeQueue<String> underTest = new FixedSizeQueue<>(2);

    // when
    underTest.pop();
    underTest.pop();

    // then no exception is thrown
  }

  @Test
  void shouldClearQueue() {
    // given
    FixedSizeQueue<String> underTest = new FixedSizeQueue<>(2);
    underTest.add("class 1");
    underTest.add("class 2");

    // when
    underTest.clear();

    // then
    assertThat(underTest).isEmpty();
  }
}
