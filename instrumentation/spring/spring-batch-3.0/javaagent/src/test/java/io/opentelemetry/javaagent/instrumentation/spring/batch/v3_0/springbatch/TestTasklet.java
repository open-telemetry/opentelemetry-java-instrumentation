/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch;

import java.util.Objects;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

public class TestTasklet implements Tasklet {
  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    if (Objects.equals(
        chunkContext.getStepContext().getStepExecution().getJobParameters().getLong("fail"), 1L)) {
      throw new IllegalStateException("fail");
    }
    return RepeatStatus.FINISHED;
  }
}
