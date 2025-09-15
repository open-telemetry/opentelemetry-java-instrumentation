/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

@FunctionalInterface
public interface TargetRunner {
  void runInTarget() throws Exception;
}
