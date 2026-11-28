/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

import java.util.IdentityHashMap;
import javax.annotation.Nullable;

public final class StreamStates {
  private final IdentityHashMap<Object, InjectionState> states = new IdentityHashMap<>();

  public void put(Object stream, InjectionState state) {
    states.put(stream, state);
  }

  @Nullable
  public InjectionState get(Object stream) {
    return states.get(stream);
  }

  // only used by tests
  public int size() {
    return states.size();
  }
}
