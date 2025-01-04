/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.client;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import ratpack.exec.Execution;
import ratpack.exec.Operation;
import ratpack.service.StartEvent;

public class BarForkService extends BarService {

  public BarForkService(String url, InstrumentationExtension testing) {
    super(url, testing);
  }

  @Override
  public void onStart(StartEvent event) {
    Execution.fork().start(Operation.of(() -> generateSpan(event)));
  }
}
