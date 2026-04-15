/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.v2_3_0;

import static io.opentelemetry.instrumentation.xxljob.XxlJobTestingConstants.DEFAULT_GLUE_UPDATE_TIME;
import static io.opentelemetry.instrumentation.xxljob.XxlJobTestingConstants.GLUE_JOB_GROOVY_SOURCE;
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

class XxlJobTest extends AbstractXxlJobTest {

  private static final MethodJobHandler methodJobHandler =
      new MethodJobHandler(
          METHOD_JOB_HANDLER_OBJECT,
          METHOD_JOB_HANDLER_METHOD,
          METHOD_JOB_HANDLER_INIT_METHOD,
          METHOD_JOB_HANDLER_DESTROY_METHOD);

  private static final IJobHandler groovyHandler;

  static {
    try {
      groovyHandler = GlueFactory.getInstance().loadNewInstance(GLUE_JOB_GROOVY_SOURCE);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static final GlueJobHandler glueJobHandler =
      new GlueJobHandler(groovyHandler, DEFAULT_GLUE_UPDATE_TIME);

  private static final ScriptJobHandler scriptJobHandler =
      new ScriptJobHandler(
          2, DEFAULT_GLUE_UPDATE_TIME, GLUE_JOB_SHELL_SCRIPT, GlueTypeEnum.GLUE_SHELL);

  @Override
  protected String getPackageName() {
    return "io.opentelemetry.javaagent.instrumentation.xxljob.v2_3_0";
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
}
