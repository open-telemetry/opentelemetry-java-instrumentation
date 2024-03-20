/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.v2_1_2;

import static io.opentelemetry.instrumentation.xxljob.XxlJobTestingConstants.DEFAULT_GLUE_UPDATE_TIME;
import static io.opentelemetry.instrumentation.xxljob.XxlJobTestingConstants.GLUE_JOB_GROOVY_SOURCE_OLD;
import static io.opentelemetry.instrumentation.xxljob.XxlJobTestingConstants.GLUE_JOB_SHELL_SCRIPT;
import static io.opentelemetry.instrumentation.xxljob.XxlJobTestingConstants.METHOD_JOB_HANDLER_DESTROY_METHOD;
import static io.opentelemetry.instrumentation.xxljob.XxlJobTestingConstants.METHOD_JOB_HANDLER_INIT_METHOD;
import static io.opentelemetry.instrumentation.xxljob.XxlJobTestingConstants.METHOD_JOB_HANDLER_METHOD;
import static io.opentelemetry.instrumentation.xxljob.XxlJobTestingConstants.METHOD_JOB_HANDLER_OBJECT;

import com.xxl.job.core.glue.GlueFactory;
import com.xxl.job.core.glue.GlueTypeEnum;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.handler.impl.GlueJobHandler;
import com.xxl.job.core.handler.impl.MethodJobHandler;
import com.xxl.job.core.handler.impl.ScriptJobHandler;
import io.opentelemetry.instrumentation.xxljob.AbstractXxlJobTest;
import io.opentelemetry.instrumentation.xxljob.CustomizedFailedHandler;
import io.opentelemetry.instrumentation.xxljob.SimpleCustomizedHandler;

class XxlJobTest extends AbstractXxlJobTest {

  private static final MethodJobHandler METHOD_JOB_HANDLER =
      new MethodJobHandler(
          METHOD_JOB_HANDLER_OBJECT,
          METHOD_JOB_HANDLER_METHOD,
          METHOD_JOB_HANDLER_INIT_METHOD,
          METHOD_JOB_HANDLER_DESTROY_METHOD);

  private static final IJobHandler GROOVY_HANDLER;

  static {
    try {
      GROOVY_HANDLER = GlueFactory.getInstance().loadNewInstance(GLUE_JOB_GROOVY_SOURCE_OLD);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static final GlueJobHandler GLUE_JOB_HANDLER =
      new GlueJobHandler(GROOVY_HANDLER, DEFAULT_GLUE_UPDATE_TIME);

  private static final ScriptJobHandler SCRIPT_JOB_HANDLER =
      new ScriptJobHandler(
          2, DEFAULT_GLUE_UPDATE_TIME, GLUE_JOB_SHELL_SCRIPT, GlueTypeEnum.GLUE_SHELL);

  @Override
  protected String getPackageName() {
    return "io.opentelemetry.instrumentation.xxljob";
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
}
