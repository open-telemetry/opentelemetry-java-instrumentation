/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.api.concurrent;

import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is used to wrap lambda callables since currently we cannot instrument them
 *
 * <p>FIXME: We should remove this once https://github.com/raphw/byte-buddy/issues/558 is fixed
 */
public final class CallableWrapper implements Callable {

  private static final Logger log = LoggerFactory.getLogger(CallableWrapper.class);

  private final Callable callable;

  public CallableWrapper(Callable callable) {
    this.callable = callable;
  }

  @Override
  public Object call() throws Exception {
    return callable.call();
  }

  public static Callable<?> wrapIfNeeded(Callable<?> task) {
    // We wrap only lambdas' anonymous classes and if given object has not already been wrapped.
    // Anonymous classes have '/' in class name which is not allowed in 'normal' classes.
    if (task.getClass().getName().contains("/") && (!(task instanceof CallableWrapper))) {
      log.debug("Wrapping callable task {}", task);
      return new CallableWrapper(task);
    }
    return task;
  }
}
