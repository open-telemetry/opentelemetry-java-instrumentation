/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import org.springframework.batch.core.JobParameter

import javax.batch.operations.JobOperator
import javax.batch.runtime.BatchRuntime
import java.util.concurrent.atomic.AtomicInteger

trait JavaxBatchConfigTrait {
  static JobOperator jobOperator
  static AtomicInteger counter = new AtomicInteger()

  def setupSpec() {
    jobOperator = BatchRuntime.jobOperator
  }

  // just for consistency with ApplicationConfigTrait
  def cleanupSpec() {
    additionalCleanup()
  }

  def additionalCleanup() {}

  def runJob(String jobName, Map<String, JobParameter> params) {
    def jobParams = new Properties()
    params.forEach({ k, v ->
      jobParams.setProperty(k, v.toString())
    })
    // each job instance with the same name needs to be unique
    jobParams.setProperty("uniqueJobIdCounter", counter.getAndIncrement().toString())
    jobOperator.start(jobName, jobParams)
  }
}