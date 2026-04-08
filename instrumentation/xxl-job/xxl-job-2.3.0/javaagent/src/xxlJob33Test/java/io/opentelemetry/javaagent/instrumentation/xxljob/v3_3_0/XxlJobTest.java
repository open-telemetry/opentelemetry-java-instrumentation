/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.v3_3_0;

import static io.opentelemetry.instrumentation.xxljob.XxlJobTestingConstants.DEFAULT_GLUE_UPDATE_TIME;
import static io.opentelemetry.instrumentation.xxljob.XxlJobTestingConstants.GLUE_JOB_GROOVY_SOURCE;
import static io.opentelemetry.instrumentation.xxljob.XxlJobTestingConstants.GLUE_JOB_SHELL_SCRIPT;

import com.xxl.job.core.glue.GlueFactory;
import com.xxl.job.core.glue.GlueTypeEnum;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.handler.impl.GlueJobHandler;
import com.xxl.job.core.handler.impl.MethodJobHandler;
import com.xxl.job.core.handler.impl.ScriptJobHandler;
import com.xxl.job.core.openapi.model.TriggerRequest;
import com.xxl.job.core.thread.JobThread;
import io.opentelemetry.instrumentation.xxljob.AbstractXxlJobTest;

class XxlJobTest extends AbstractXxlJobTest {

  private static final MethodJobHandler METHOD_JOB_HANDLER =
      new MethodJobHandler(
          ReflectiveMethodsFactory.getTarget(),
          ReflectiveMethodsFactory.getMethod(),
          ReflectiveMethodsFactory.getInitMethod(),
          ReflectiveMethodsFactory.getDestroyMethod());

  private static final IJobHandler GROOVY_HANDLER;

  static {
    try {
      GROOVY_HANDLER = GlueFactory.getInstance().loadNewInstance(GLUE_JOB_GROOVY_SOURCE);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static final GlueJobHandler GLUE_JOB_HANDLER =
      new GlueJobHandler(GROOVY_HANDLER, DEFAULT_GLUE_UPDATE_TIME);

  private static final ScriptJobHandler SCRIPT_JOB_HANDLER =
      new ScriptJobHandler(
          2, DEFAULT_GLUE_UPDATE_TIME, GLUE_JOB_SHELL_SCRIPT, GlueTypeEnum.GLUE_SHELL);

  @Override
  protected String getPackageName() {
    return "io.opentelemetry.javaagent.instrumentation.xxljob.v3_3_0";
  }

  @Override
  protected IJobHandler getGlueJobHandler() {
    return GLUE_JOB_HANDLER;
  }

  @Override
  protected IJobHandler getScriptJobHandler() {
    return SCRIPT_JOB_HANDLER;
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
    return METHOD_JOB_HANDLER;
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
