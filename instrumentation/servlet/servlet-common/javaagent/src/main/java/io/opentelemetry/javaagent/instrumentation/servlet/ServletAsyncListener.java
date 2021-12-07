/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

public interface ServletAsyncListener<RESPONSE> {
  void onComplete(RESPONSE response);

  void onTimeout(long timeout);

  void onError(Throwable throwable, RESPONSE response);
}
