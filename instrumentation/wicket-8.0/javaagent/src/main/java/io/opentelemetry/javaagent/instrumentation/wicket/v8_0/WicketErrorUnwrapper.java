/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.wicket.v8_0;

import io.opentelemetry.instrumentation.api.internal.CauseUnwrapper;
import java.lang.reflect.InvocationTargetException;
import org.apache.wicket.WicketRuntimeException;

public class WicketErrorUnwrapper {

  /**
   * Unwraps the wicket-specific wrapper exceptions from {@code error} to find the error that
   * actually caused the failure.
   */
  public static Throwable unwrap(Throwable error) {
    return CauseUnwrapper.unwrap(error, WicketErrorUnwrapper::isWrapperException);
  }

  private static boolean isWrapperException(Throwable error) {
    return error instanceof WicketRuntimeException || error instanceof InvocationTargetException;
  }

  private WicketErrorUnwrapper() {}
}
