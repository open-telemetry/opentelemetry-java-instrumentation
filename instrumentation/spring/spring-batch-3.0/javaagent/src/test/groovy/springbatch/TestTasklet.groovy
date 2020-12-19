/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springbatch

import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus

class TestTasklet implements Tasklet {
  @Override
  RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
    if (chunkContext.stepContext.stepExecution.jobParameters.getLong("fail") == 1) {
      throw new RuntimeException("fail")
    }
    RepeatStatus.FINISHED
  }
}