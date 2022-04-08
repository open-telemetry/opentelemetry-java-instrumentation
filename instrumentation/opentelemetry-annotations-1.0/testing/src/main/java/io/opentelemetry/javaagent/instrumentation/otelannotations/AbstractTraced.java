/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.otelannotations;

public abstract class AbstractTraced<T extends U, U> {

  protected static final String SUCCESS_VALUE = "Value";

  protected static final IllegalArgumentException FAILURE = new IllegalArgumentException("Boom");

  protected AbstractTraced() {
    if (!getClass().getSimpleName().equals("Traced")) {
      throw new IllegalStateException("Subclasses of AbstractTraced must be named Traced");
    }
  }

  protected abstract T completable();

  protected abstract U alreadySucceeded();

  protected abstract U alreadyFailed();
}
