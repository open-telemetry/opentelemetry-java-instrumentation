/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.powerjob.v4_0;

import java.util.List;
import tech.powerjob.worker.core.processor.ProcessResult;
import tech.powerjob.worker.core.processor.TaskContext;
import tech.powerjob.worker.core.processor.TaskResult;
import tech.powerjob.worker.core.processor.sdk.BroadcastProcessor;

class TestBroadcastProcessor implements BroadcastProcessor {

  @Override
  public ProcessResult preProcess(TaskContext taskContext) {
    return new ProcessResult(true, "preProcess success");
  }

  @Override
  public ProcessResult postProcess(TaskContext taskContext, List<TaskResult> taskResults) {
    return new ProcessResult(true, "postProcess success");
  }

  @Override
  public ProcessResult process(TaskContext context) {
    return new ProcessResult(true, "processSuccess");
  }
}
