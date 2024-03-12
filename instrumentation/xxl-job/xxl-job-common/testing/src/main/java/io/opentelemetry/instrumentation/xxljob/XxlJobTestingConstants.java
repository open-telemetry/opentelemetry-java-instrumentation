/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.xxljob;

import java.lang.reflect.Method;

public class XxlJobTestingConstants {

  private XxlJobTestingConstants() {}

  public static final String GLUE_JOB_SHELL_SCRIPT = "echo 'hello'";

  public static final long DEFAULT_GLUE_UPDATE_TIME = System.currentTimeMillis();

  public static final Object METHOD_JOB_HANDLER_OBJECT = ReflectiveMethodsFactory.getTarget();

  public static final Method METHOD_JOB_HANDLER_METHOD = ReflectiveMethodsFactory.getMethod();

  public static final Method METHOD_JOB_HANDLER_INIT_METHOD =
      ReflectiveMethodsFactory.getInitMethod();

  public static final Method METHOD_JOB_HANDLER_DESTROY_METHOD =
      ReflectiveMethodsFactory.getDestroyMethod();

  public static final String GLUE_JOB_GROOVY_SOURCE_OLD =
      "import com.xxl.job.core.handler.IJobHandler\n"
          + "import com.xxl.job.core.biz.model.ReturnT\n"
          + "class CustomizedGroovyHandler extends IJobHandler {\n"
          + "  @Override\n"
          + "  public ReturnT<String> execute(String s) throws Exception {\n"
          + "    return new ReturnT<>(\"Hello World\")\n"
          + "  }\n"
          + "}\n";

  public static final String GLUE_JOB_GROOVY_SOURCE =
      "import com.xxl.job.core.handler.IJobHandler\n"
          + "\n"
          + "class CustomizedGroovyHandler extends IJobHandler {\n"
          + "  @Override\n"
          + "  void execute() throws Exception {\n"
          + "  }\n"
          + "}\n";
}
