/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.Scope;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class ContextStorageCloserTest {

  @Test
  void restoresPendingScopesBeforeRetrying() throws Exception {
    TestStrictContextStorage storage = new TestStrictContextStorage(1);
    ContextStorageCloser.close(storage);
    assertThat(storage.attempts).isEqualTo(2);
    assertThat(storage.pendingScopes.map).isEmpty();
  }

  @Test
  void retriesWithUnderlyingStorageAcrossBridge() throws Exception {
    TestStrictContextStorage storage = new TestStrictContextStorage(1);
    BridgeStorage bridge = new BridgeStorage(storage);
    ContextStorageCloser.close(bridge, storage);
    assertThat(storage.attempts).isEqualTo(2);
    assertThat(storage.pendingScopes.map).isEmpty();
  }

  @Test
  void doesNotHidePersistentLeaks() {
    TestStrictContextStorage storage = new TestStrictContextStorage(Integer.MAX_VALUE);
    assertThatThrownBy(() -> ContextStorageCloser.close(storage))
        .isInstanceOf(org.awaitility.core.ConditionTimeoutException.class)
        .hasCauseInstanceOf(AssertionError.class);
    assertThat(storage.pendingScopes.map).containsEntry("scope", "unclosed");
  }

  @Test
  void closesRealStrictStorage() throws Exception {
    ContextStorage storage = ContextStorage.get();
    assertThat(storage.getClass().getName()).contains("StrictContextStorage");
    Context context = Context.root().with(ContextKey.named("test"), "value");
    try (Scope ignored = storage.attach(context)) {
      assertThat(storage.current()).isEqualTo(context);
    }
    ContextStorageCloser.close(storage);
  }

  private static final class TestStrictContextStorage implements ContextStorage, AutoCloseable {
    private final PendingScopes pendingScopes = new PendingScopes();
    private final int failures;
    private int attempts;

    TestStrictContextStorage(int failures) {
      this.failures = failures;
      pendingScopes.map.put("scope", "unclosed");
    }

    @Override
    public Scope attach(Context context) {
      return Scope.noop();
    }

    @Override
    public Context current() {
      return Context.root();
    }

    @Override
    public void close() {
      assertThat(pendingScopes.map.remove("scope")).isEqualTo("unclosed");
      if (++attempts <= failures) {
        throw new AssertionError("unclosed scope");
      }
    }
  }

  private static final class PendingScopes {
    private final ConcurrentHashMap<Object, Object> map = new ConcurrentHashMap<>();
  }

  private static final class BridgeStorage implements ContextStorage, AutoCloseable {
    private final TestStrictContextStorage delegate;

    BridgeStorage(TestStrictContextStorage delegate) {
      this.delegate = delegate;
    }

    @Override
    public Scope attach(Context context) {
      return delegate.attach(context);
    }

    @Override
    public Context current() {
      return delegate.current();
    }

    @Override
    public void close() {
      delegate.close();
    }
  }
}
