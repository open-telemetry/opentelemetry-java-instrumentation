/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors;

import java.util.concurrent.Callable;

public interface TestTask extends Runnable, Callable<Object> {

  void unblock();

  void waitForCompletion();
}
