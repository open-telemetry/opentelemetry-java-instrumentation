/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.job;

import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.spring.batch.ContextAndScope;
import net.bytebuddy.asm.Advice;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

public class GetObjectAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.FieldValue(value = "jobExecutionListeners", readOnly = false)
          JobExecutionListener[] jobExecutionListeners) {
    ContextStore<JobExecution, ContextAndScope> executionContextStore =
        InstrumentationContext.get(JobExecution.class, ContextAndScope.class);
    JobExecutionListener tracingListener = new TracingJobExecutionListener(executionContextStore);
    if (jobExecutionListeners == null) {
      jobExecutionListeners = new JobExecutionListener[] {tracingListener};
    } else {
      JobExecutionListener[] newListeners =
          new JobExecutionListener[jobExecutionListeners.length + 1];
      newListeners[0] = tracingListener;
      System.arraycopy(jobExecutionListeners, 0, newListeners, 1, jobExecutionListeners.length);
      jobExecutionListeners = newListeners;
    }
  }
}
