/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.v3_3_0;

import static io.opentelemetry.instrumentation.test.utils.PortUtils.findOpenPort;
import static io.opentelemetry.instrumentation.xxljob.common.v1_9_2.XxlJobTestingConstants.DEFAULT_GLUE_UPDATE_TIME;
import static io.opentelemetry.instrumentation.xxljob.common.v1_9_2.XxlJobTestingConstants.GLUE_JOB_GROOVY_SOURCE;
import static io.opentelemetry.instrumentation.xxljob.common.v1_9_2.XxlJobTestingConstants.GLUE_JOB_SHELL_SCRIPT;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.sun.net.httpserver.HttpServer;
import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.glue.GlueFactory;
import com.xxl.job.core.glue.GlueTypeEnum;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.handler.impl.GlueJobHandler;
import com.xxl.job.core.handler.impl.MethodJobHandler;
import com.xxl.job.core.handler.impl.ScriptJobHandler;
import com.xxl.job.core.thread.JobThread;
import io.opentelemetry.instrumentation.xxljob.common.v1_9_2.AbstractXxlJobTest;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

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
  private static final XxlJobExecutor xxlJobExecutor = new XxlJobExecutor();
  private static HttpServer adminServer;

  private static IJobHandler createGroovyHandler() {
    try {
      return GlueFactory.getInstance().loadNewInstance(GLUE_JOB_GROOVY_SOURCE);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @BeforeAll
  static void start() throws Exception {
    adminServer = HttpServer.create(new InetSocketAddress("localhost", findOpenPort()), 0);
    adminServer.createContext(
        "/api",
        exchange -> {
          byte[] response = "{\"code\":200}".getBytes(UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          try (OutputStream responseBody = exchange.getResponseBody()) {
            responseBody.write(response);
          }
        });
    adminServer.start();
    xxlJobExecutor.setAdminAddresses("http://localhost:" + adminServer.getAddress().getPort());
    xxlJobExecutor.setLogPath("build/xxljob/log");
    xxlJobExecutor.setAppname("test");
    xxlJobExecutor.setAccessToken("test-token");
    xxlJobExecutor.setIp("127.0.0.1");
    xxlJobExecutor.setPort(findOpenPort());
    xxlJobExecutor.start();
  }

  @AfterAll
  static void stop() {
    try {
      xxlJobExecutor.destroy();
    } finally {
      if (adminServer != null) {
        adminServer.stop(0);
      }
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
    try {
      Class<?> triggerRequestClass;
      try {
        triggerRequestClass = Class.forName("com.xxl.job.core.openapi.executor.dto.TriggerRequest");
      } catch (ClassNotFoundException ignored) {
        // TriggerRequest moved packages in xxl-job 3.5.0.
        triggerRequestClass = Class.forName("com.xxl.job.core.openapi.model.TriggerRequest");
      }
      Object triggerParam = triggerRequestClass.getDeclaredConstructor().newInstance();
      triggerRequestClass.getMethod("setExecutorTimeout", int.class).invoke(triggerParam, 0);
      if (executorParams != null) {
        triggerRequestClass
            .getMethod("setExecutorParams", String.class)
            .invoke(triggerParam, executorParams);
      }
      JobThread.class
          .getMethod("pushTriggerQueue", triggerRequestClass)
          .invoke(jobThread, triggerParam);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    jobThread.start();
  }

  @Override
  protected Class<?> getReflectObjectClass() {
    return ReflectiveMethodsFactory.ReflectObject.class;
  }
}
