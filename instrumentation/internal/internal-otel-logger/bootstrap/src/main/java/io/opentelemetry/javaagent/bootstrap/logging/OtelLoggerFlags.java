/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.logging;

import java.util.concurrent.atomic.AtomicBoolean;

public final class OtelLoggerFlags {

  private OtelLoggerFlags() {}

  public static final AtomicBoolean IS_INSTALLED = new AtomicBoolean(false);
}
