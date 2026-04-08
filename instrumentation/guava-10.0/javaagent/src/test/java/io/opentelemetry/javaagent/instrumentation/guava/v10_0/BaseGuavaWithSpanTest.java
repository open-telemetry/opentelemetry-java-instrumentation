/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.guava.v10_0;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.opentelemetry.javaagent.instrumentation.otelannotations.AbstractWithSpanTest;

abstract class BaseGuavaWithSpanTest
    extends AbstractWithSpanTest<SettableFuture<String>, ListenableFuture<String>> {

  static final boolean isExperimentalEnabled =
      Boolean.getBoolean("otel.instrumentation.guava.experimental-span-attributes");

  @Override
  protected void complete(SettableFuture<String> future, String value) {
    future.set(value);
  }

  @Override
  protected void fail(SettableFuture<String> future, Throwable error) {
    future.setException(error);
  }

  @Override
  protected void cancel(SettableFuture<String> future) {
    future.cancel(true);
  }

  @Override
  protected String getCompleted(ListenableFuture<String> future) {
    return Futures.getUnchecked(future);
  }

  @Override
  protected Throwable unwrapError(Throwable t) {
    return Throwables.getRootCause(t);
  }

  @Override
  protected String canceledKey() {
    return "guava.canceled";
  }

  @Override
  protected boolean isExperimentalSpanAttributesEnabled() {
    return isExperimentalEnabled;
  }
}
