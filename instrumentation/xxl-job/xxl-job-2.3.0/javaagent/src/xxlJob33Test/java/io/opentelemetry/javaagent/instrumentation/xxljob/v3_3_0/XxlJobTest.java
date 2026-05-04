/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.v3_3_0;

import static io.opentelemetry.instrumentation.xxljob.common.v1_9_2.XxlJobTestingConstants.DEFAULT_GLUE_UPDATE_TIME;
import static io.opentelemetry.instrumentation.xxljob.common.v1_9_2.XxlJobTestingConstants.GLUE_JOB_GROOVY_SOURCE;
import static io.opentelemetry.instrumentation.xxljob.common.v1_9_2.XxlJobTestingConstants.GLUE_JOB_SHELL_SCRIPT;

import com.xxl.job.core.glue.GlueFactory;
import com.xxl.job.core.glue.GlueTypeEnum;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.handler.impl.GlueJobHandler;
import com.xxl.job.core.handler.impl.MethodJobHandler;
import com.xxl.job.core.handler.impl.ScriptJobHandler;
import com.xxl.job.core.openapi.model.TriggerRequest;
import com.xxl.job.core.thread.JobThread;
import io.opentelemetry.instrumentation.xxljob.common.v1_9_2.AbstractXxlJobTest;

class XxlJobTest extends AbstractXxlJobTest {

  private static final MethodJobHandler methodJobHandler =
      new MethodJobHandler(
          ReflectiveMethodsFactory.getTarget(),
          ReflectiveMethodsFactory.getMethod(),
          ReflectiveMethodsFactory.getInitMethod(),
          ReflectiveMethodsFactory.getDestroyMethod());

  private static final IJobHandler groovyHandler = createGroovyHandler();
  private static final GlueJobHandler glueJobHandler =
      new GlueJobHandler(groovyHandler, DEFAULT_GLUE_UPDATE_TIME);
  private static final ScriptJobHandler scriptJobHandler =
      new ScriptJobHandler(
          2, DEFAULT_GLUE_UPDATE_TIME, GLUE_JOB_SHELL_SCRIPT, GlueTypeEnum.GLUE_SHELL);

  private static IJobHandler createGroovyHandler() {
    try {
      return GlueFactory.getInstance().loadNewInstance(GLUE_JOB_GROOVY_SOURCE);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  protected String getPackageName() {
    return "io.opentelemetry.javaagent.instrumentation.xxljob.v3_3_0";
  }

  @Override
  protected IJobHandler getGlueJobHandler() {
    return glueJobHandler;
  }

  @Override
  protected IJobHandler getScriptJobHandler() {
    return scriptJobHandler;
  }

  @Override
  protected IJobHandler getCustomizeHandler() {
    return new SimpleCustomizedHandler();
  }

  @Override
  protected IJobHandler getCustomizeFailedHandler() {
    return new CustomizedFailedHandler();
  }

  @Override
  protected IJobHandler getMethodHandler() {
    return methodJobHandler;
  }

  @Override
  protected void trigger(JobThread jobThread, String executorParams) {
    TriggerRequest triggerParam = new TriggerRequest();
    triggerParam.setExecutorTimeout(0);
    if (executorParams != null) {
      triggerParam.setExecutorParams(executorParams);
    }
    jobThread.pushTriggerQueue(triggerParam);
    jobThread.start();
  }

  @Override
  protected Class<?> getReflectObjectClass() {
    return ReflectiveMethodsFactory.ReflectObject.class;
  }
}
