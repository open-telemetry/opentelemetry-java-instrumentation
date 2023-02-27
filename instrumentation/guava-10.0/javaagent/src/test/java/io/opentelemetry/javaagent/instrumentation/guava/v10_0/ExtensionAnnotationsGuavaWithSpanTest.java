/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.guava.v10_0;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.opentelemetry.javaagent.instrumentation.otelannotations.AbstractTraced;

@SuppressWarnings("deprecation") // testing instrumentation of deprecated class
class ExtensionAnnotationsGuavaWithSpanTest extends BaseGuavaWithSpanTest {

  @Override
  protected AbstractTraced<SettableFuture<String>, ListenableFuture<String>> newTraced() {
    return new Traced();
  }

  static final class Traced
      extends AbstractTraced<SettableFuture<String>, ListenableFuture<String>> {

    @Override
    @io.opentelemetry.extension.annotations.WithSpan
    protected SettableFuture<String> completable() {
      return SettableFuture.create();
    }

    @Override
    @io.opentelemetry.extension.annotations.WithSpan
    protected ListenableFuture<String> alreadySucceeded() {
      return Futures.immediateFuture("Value");
    }

    @Override
    @io.opentelemetry.extension.annotations.WithSpan
    protected ListenableFuture<String> alreadyFailed() {
      return Futures.immediateFailedFuture(FAILURE);
    }
  }
}
