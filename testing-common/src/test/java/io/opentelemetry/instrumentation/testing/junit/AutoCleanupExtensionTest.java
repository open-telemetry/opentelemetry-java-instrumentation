/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    assertEquals(0, count.get());
  }

  @AfterAll
  static void verifyCount() {
    assertEquals(1, count.get());
  }
}
