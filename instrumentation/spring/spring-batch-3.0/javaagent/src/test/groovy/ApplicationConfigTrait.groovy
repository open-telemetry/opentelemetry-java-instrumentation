/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParameter
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.context.ConfigurableApplicationContext

trait ApplicationConfigTrait {
  static ConfigurableApplicationContext applicationContext
  static JobLauncher jobLauncher

  abstract ConfigurableApplicationContext createApplicationContext()

  def setupSpec() {
    applicationContext = createApplicationContext()
    applicationContext.start()

    jobLauncher = applicationContext.getBean(JobLauncher)
  }

  def cleanupSpec() {
    applicationContext.stop()
    applicationContext.close()

    additionalCleanup()
  }

  def additionalCleanup() {}

  def runJob(String jobName, Map<String, JobParameter> params) {
    def job = applicationContext.getBean(jobName, Job)
    postProcessJob(jobName, job)
    jobLauncher.run(job, new JobParameters(params))
  }

  def postProcessJob(String jobName, Job job) {
  }
}