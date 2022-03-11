/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.grpc.override;

import io.grpc.Context;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.ContextStorageBridge;

/**
 * Override class for gRPC to pick up this class to replace the default {@link Context.Storage} with
 * an OpenTelemetry bridge.
 */
public final class ContextStorageOverride extends Context.Storage {

  private static final Context.Storage delegate = new ContextStorageBridge(true);

  @Override
  public Context doAttach(Context toAttach) {
    return delegate.doAttach(toAttach);
  }

  @Override
  public void detach(Context toDetach, Context toRestore) {
    delegate.detach(toDetach, toRestore);
  }

  @Override
  public Context current() {
    return delegate.current();
  }
}
