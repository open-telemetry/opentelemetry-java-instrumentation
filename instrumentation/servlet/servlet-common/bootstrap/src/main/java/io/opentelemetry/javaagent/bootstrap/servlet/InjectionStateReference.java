/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

import java.lang.ref.WeakReference;

/**
 * The weak reference is only needed when VirtualField uses its fallback storage. With a normal
 * injected field, an unreachable stream/state cycle is collectible. The fallback uses a global map
 * with weak keys and strong values, so a strong state value could reach the stream key through its
 * response wrapper, keeping the key alive and preventing entry cleanup.
 *
 * <p>The request keeps the state strongly reachable for its lifetime, including across async
 * writes.
 */
public final class InjectionStateReference extends WeakReference<InjectionState> {

  public InjectionStateReference(InjectionState state) {
    super(state);
  }
}
