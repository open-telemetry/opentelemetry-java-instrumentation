/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.powerjob.v4_0;

import tech.powerjob.worker.core.processor.ProcessResult;
import tech.powerjob.worker.core.processor.TaskContext;
import tech.powerjob.worker.core.processor.sdk.MapProcessor;

class TestMapProcessProcessor implements MapProcessor {

  @Override
  public ProcessResult process(TaskContext context) {
    return new ProcessResult(true, "processSuccess");
  }
}
