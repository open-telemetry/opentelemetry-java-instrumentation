/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.guava;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.opentelemetry.extension.annotations.WithSpan;
import io.opentelemetry.javaagent.instrumentation.otelannotations.AbstractTraced;
import io.opentelemetry.javaagent.instrumentation.otelannotations.AbstractWithSpanTest;
import org.testcontainers.shaded.com.google.common.base.Throwables;

class GuavaWithSpanTest
    extends AbstractWithSpanTest<SettableFuture<String>, ListenableFuture<String>> {

  @Override
  protected AbstractTraced<SettableFuture<String>, ListenableFuture<String>> newTraced() {
    return new Traced();
  }

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

  static final class Traced
      extends AbstractTraced<SettableFuture<String>, ListenableFuture<String>> {

    @Override
    @WithSpan
    protected SettableFuture<String> completable() {
      return SettableFuture.create();
    }

    @Override
    @WithSpan
    protected ListenableFuture<String> alreadySucceeded() {
      return Futures.immediateFuture("Value");
    }

    @Override
    @WithSpan
    protected ListenableFuture<String> alreadyFailed() {
      return Futures.immediateFailedFuture(FAILURE);
    }
  }
}
