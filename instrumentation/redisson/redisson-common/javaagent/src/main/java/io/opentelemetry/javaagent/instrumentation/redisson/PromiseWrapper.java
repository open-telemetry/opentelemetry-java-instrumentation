/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

public interface PromiseWrapper<T> {

  void setEndOperationListener(EndOperationListener<T> endOperationListener);
}
