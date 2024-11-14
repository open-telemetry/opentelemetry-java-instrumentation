/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.powerjob.v4_0;

import java.util.List;
import tech.powerjob.worker.core.processor.ProcessResult;
import tech.powerjob.worker.core.processor.TaskContext;
import tech.powerjob.worker.core.processor.TaskResult;
import tech.powerjob.worker.core.processor.sdk.MapReduceProcessor;

class TestMapReduceProcessProcessor implements MapReduceProcessor {

  @Override
  public ProcessResult process(TaskContext context) {
    return new ProcessResult(true, "processSuccess");
  }

  @Override
  public ProcessResult reduce(TaskContext taskContext, List<TaskResult> list) {
    return new ProcessResult(true, "reduceSuccess");
  }
}
