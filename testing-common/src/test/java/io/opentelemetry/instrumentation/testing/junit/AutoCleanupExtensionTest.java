/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class AutoCleanupExtensionTest {

  static final AtomicInteger count = new AtomicInteger(0);

  @RegisterExtension final AutoCleanupExtension autoCleanup = AutoCleanupExtension.create();

  @Test
  void shouldRunCleanupAfterTest() {
    autoCleanup.deferCleanup(count::incrementAndGet);

    assertThat(count.get()).isEqualTo(0);
  }

  @AfterAll
  static void verifyCount() {
    assertThat(count.get()).isEqualTo(1);
  }
}
