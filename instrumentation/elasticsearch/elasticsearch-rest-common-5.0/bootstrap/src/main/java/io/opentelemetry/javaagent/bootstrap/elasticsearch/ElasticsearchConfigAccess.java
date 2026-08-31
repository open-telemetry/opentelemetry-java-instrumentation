/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.elasticsearch;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ElasticsearchConfigAccess {

  private static final AtomicBoolean captureSearchQueryWarningLogged = new AtomicBoolean();

  public static boolean shouldLogCaptureSearchQueryWarning() {
    return captureSearchQueryWarningLogged.compareAndSet(false, true);
  }

  private ElasticsearchConfigAccess() {}
}
