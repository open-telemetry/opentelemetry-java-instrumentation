/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher;

import java.util.Iterator;

public class ThrowOnFirstElement implements Iterator<Object> {
  private int current = 0;

  @Override
  public boolean hasNext() {
    return current++ < 1;
  }

  @Override
  public Object next() {
    throw new RuntimeException("iteration exception");
  }
}
