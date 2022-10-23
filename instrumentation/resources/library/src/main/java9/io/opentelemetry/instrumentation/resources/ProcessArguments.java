/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

final class ProcessArguments {

  static String[] getProcessArguments() {
    return ProcessHandle.current().info().arguments().orElseGet(() -> new String[0]);
  }

  private ProcessArguments() {}
}
