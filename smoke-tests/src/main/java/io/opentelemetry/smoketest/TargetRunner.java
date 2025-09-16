/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import java.util.function.Consumer;
import org.testcontainers.containers.output.OutputFrame;

@FunctionalInterface
public interface TargetRunner {
  void runInTarget(Consumer<OutputFrame> startTarget) throws Exception;
}
