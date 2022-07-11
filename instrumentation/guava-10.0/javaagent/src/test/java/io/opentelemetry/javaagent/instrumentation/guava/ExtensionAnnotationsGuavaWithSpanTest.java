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

class ExtensionAnnotationsGuavaWithSpanTest extends BaseGuavaWithSpanTest {

  @Override
  protected AbstractTraced<SettableFuture<String>, ListenableFuture<String>> newTraced() {
    return new Traced();
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
