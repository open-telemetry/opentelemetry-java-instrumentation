/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Exception identities for a single framework request or hook lifecycle. Callers create this only
 * when an exception is reported and clear it when that lifecycle finishes.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class RecordedExceptions {
  @Nullable private Set<Throwable> exceptions = Collections.newSetFromMap(new IdentityHashMap<>());

  /** Returns whether the already-extracted exception has not been reported in this lifecycle. */
  public synchronized boolean add(Throwable error) {
    return exceptions == null || exceptions.add(error);
  }

  public synchronized void clear() {
    if (exceptions != null) {
      exceptions.clear();
    }
  }

  /** Releases identities even if a completion callback races with a late exception report. */
  public synchronized void close() {
    exceptions = null;
  }
}
