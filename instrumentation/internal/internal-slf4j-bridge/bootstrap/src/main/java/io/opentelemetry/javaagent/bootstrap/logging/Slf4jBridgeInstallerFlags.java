/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.logging;

import java.util.concurrent.atomic.AtomicBoolean;

public final class Slf4jBridgeInstallerFlags {

  private Slf4jBridgeInstallerFlags() {}

  public static final AtomicBoolean IS_INSTALLED = new AtomicBoolean(false);
}
