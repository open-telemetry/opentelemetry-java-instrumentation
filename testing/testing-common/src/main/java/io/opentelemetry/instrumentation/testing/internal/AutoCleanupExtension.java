/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.internal;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * A small utility extension that allows deferring executing cleanup code in {@code @Test} methods
 * until the test has finished. All cleanup callbacks added during the test will always be executed
 * after it finishes, no matter the outcome. Inspired by Spock's {@code cleanup:} block.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class AutoCleanupExtension
    implements BeforeAllCallback, AfterEachCallback, AfterAllCallback {
  private final Deque<AutoCloseable> thingsToCleanUp = new ConcurrentLinkedDeque<>();
  private final Deque<AutoCloseable> thingsToCleanUpAfterAll = new ConcurrentLinkedDeque<>();
  // Tracks the first (outermost) container the extension is registered in, so that afterAll only
  // runs deferAfterAll cleanups once — not once per @Nested container.
  private volatile ExtensionContext rootContext;

  public static AutoCleanupExtension create() {
    return new AutoCleanupExtension();
  }

  private AutoCleanupExtension() {}

  /** Add a {@code cleanupAction} to execute after the test finishes. */
  public void deferCleanup(AutoCloseable cleanupAction) {
    thingsToCleanUp.push(cleanupAction);
  }

  /** Add a {@code cleanupAction} to execute after the test class finishes. */
  public void deferAfterAll(AutoCloseable cleanupAction) {
    thingsToCleanUpAfterAll.push(cleanupAction);
  }

  @Override
  public void beforeAll(ExtensionContext extensionContext) {
    if (rootContext == null) {
      rootContext = extensionContext;
    }
  }

  @Override
  public void afterEach(ExtensionContext extensionContext) throws Exception {
    List<Exception> exceptions = new ArrayList<>();
    while (!thingsToCleanUp.isEmpty()) {
      try {
        thingsToCleanUp.pop().close();
      } catch (Exception e) {
        exceptions.add(e);
      }
    }
    throwIfAny(exceptions);
  }

  @Override
  public void afterAll(ExtensionContext extensionContext) throws Exception {
    // afterAll fires once per container the extension is registered in, including each @Nested
    // class. Only run deferAfterAll cleanups when the outermost container completes.
    if (extensionContext != rootContext) {
      return;
    }
    List<Exception> exceptions = new ArrayList<>();
    while (!thingsToCleanUpAfterAll.isEmpty()) {
      try {
        thingsToCleanUpAfterAll.pop().close();
      } catch (Exception e) {
        exceptions.add(e);
      }
    }
    throwIfAny(exceptions);
  }

  private static void throwIfAny(List<Exception> exceptions) throws Exception {
    switch (exceptions.size()) {
      case 0:
        return;
      case 1:
        throw exceptions.get(0);
      default:
        AssertionError allFailures = new AssertionError("Multiple cleanup errors occurred");
        exceptions.forEach(allFailures::addSuppressed);
        throw allFailures;
    }
  }
}
