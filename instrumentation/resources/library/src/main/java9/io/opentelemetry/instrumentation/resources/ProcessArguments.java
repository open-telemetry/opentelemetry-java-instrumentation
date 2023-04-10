/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import javax.annotation.Nonnull;

final class ProcessArguments {

  @Nonnull
  static String[] getProcessArguments() {
    return ProcessHandle.current().info().arguments().orElseGet(() -> new String[0]);
  }

  private ProcessArguments() {}
}
