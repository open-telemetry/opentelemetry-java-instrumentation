/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class EnumerationUtil {

  public static <E> Iterator<E> asIterator(@Nullable Enumeration<E> enumeration) {
    if (enumeration == null) {
      return Collections.emptyIterator();
    }
    return new Iterator<E>() {
      @Override
      public boolean hasNext() {
        return enumeration.hasMoreElements();
      }

      @Override
      public E next() {
        return enumeration.nextElement();
      }
    };
  }

  private EnumerationUtil() {}
}
