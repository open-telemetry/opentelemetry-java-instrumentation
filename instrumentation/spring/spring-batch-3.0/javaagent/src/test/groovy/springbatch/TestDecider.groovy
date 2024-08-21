/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springbatch

import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.job.flow.FlowExecutionStatus
import org.springframework.batch.core.job.flow.JobExecutionDecider

class TestDecider implements JobExecutionDecider {
  @Override
  FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {
    new FlowExecutionStatus("LEFT")
  }
}
