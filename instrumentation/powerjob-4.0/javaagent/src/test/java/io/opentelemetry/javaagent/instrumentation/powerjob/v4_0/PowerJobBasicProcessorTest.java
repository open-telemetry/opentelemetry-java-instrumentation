/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.powerjob.v4_0;

import static io.opentelemetry.javaagent.instrumentation.powerjob.v4_0.PowerJobConstants.BASIC_PROCESSOR;
import static io.opentelemetry.javaagent.instrumentation.powerjob.v4_0.PowerJobConstants.BROADCAST_PROCESSOR;
import static io.opentelemetry.javaagent.instrumentation.powerjob.v4_0.PowerJobConstants.DYNAMIC_DATASOURCE_SQL_PROCESSOR;
import static io.opentelemetry.javaagent.instrumentation.powerjob.v4_0.PowerJobConstants.FILE_CLEANUP_PROCESSOR;
import static io.opentelemetry.javaagent.instrumentation.powerjob.v4_0.PowerJobConstants.HTTP_PROCESSOR;
import static io.opentelemetry.javaagent.instrumentation.powerjob.v4_0.PowerJobConstants.MAP_PROCESSOR;
import static io.opentelemetry.javaagent.instrumentation.powerjob.v4_0.PowerJobConstants.MAP_REDUCE_PROCESSOR;
import static io.opentelemetry.javaagent.instrumentation.powerjob.v4_0.PowerJobConstants.PYTHON_PROCESSOR;
import static io.opentelemetry.javaagent.instrumentation.powerjob.v4_0.PowerJobConstants.SHELL_PROCESSOR;
import static io.opentelemetry.javaagent.instrumentation.powerjob.v4_0.PowerJobConstants.SPRING_DATASOURCE_SQL_PROCESSOR;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Arrays.asList;

import akka.actor.ActorSystem;
import com.google.common.collect.Maps;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.zaxxer.hikari.HikariDataSource;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.internal.StringUtils;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tech.powerjob.common.RemoteConstant;
import tech.powerjob.common.enums.ExecuteType;
import tech.powerjob.common.enums.ProcessorType;
import tech.powerjob.common.utils.NetUtils;
import tech.powerjob.official.processors.impl.FileCleanupProcessor;
import tech.powerjob.official.processors.impl.HttpProcessor;
import tech.powerjob.official.processors.impl.script.PythonProcessor;
import tech.powerjob.official.processors.impl.script.ShellProcessor;
import tech.powerjob.official.processors.impl.sql.DynamicDatasourceSqlProcessor;
import tech.powerjob.official.processors.impl.sql.SpringDatasourceSqlProcessor;
import tech.powerjob.worker.background.OmsLogHandler;
import tech.powerjob.worker.common.PowerJobWorkerConfig;
import tech.powerjob.worker.common.WorkerRuntime;
import tech.powerjob.worker.common.utils.SpringUtils;
import tech.powerjob.worker.core.tracker.processor.ProcessorTracker;
import tech.powerjob.worker.persistence.TaskDO;
import tech.powerjob.worker.pojo.model.InstanceInfo;
import tech.powerjob.worker.pojo.request.TaskTrackerStartTaskReq;

class PowerJobBasicProcessorTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void testBasicProcessor() {
    WorkerRuntime workerRuntime = genWorkerRuntime();

    long jobId = 1;
    String jobParam = "abc";
    TaskTrackerStartTaskReq req =
        genTaskTrackerStartTaskReq(
            TestBasicProcessor.class.getName(), jobId, jobParam, ExecuteType.STANDALONE);
    TaskDO task = genTask(req);
    ProcessorTracker pt = new ProcessorTracker(req, workerRuntime);
    pt.submitTask(task);
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(String.format("%s.process", TestBasicProcessor.class.getSimpleName()));
                span.hasKind(SpanKind.INTERNAL);
                span.hasStatus(StatusData.unset());
                span.hasAttributesSatisfying(
                    attributeAssertions(
                        TestBasicProcessor.class.getName(), jobId, jobParam, BASIC_PROCESSOR));
              });
        });
  }

  @Test
  void testBasicFailProcessor() {
    WorkerRuntime workerRuntime = genWorkerRuntime();

    long jobId = 1;
    String jobParam = "abc";
    TaskTrackerStartTaskReq req =
        genTaskTrackerStartTaskReq(
            TestBasicFailProcessor.class.getName(), jobId, jobParam, ExecuteType.STANDALONE);
    TaskDO task = genTask(req);
    ProcessorTracker pt = new ProcessorTracker(req, workerRuntime);
    pt.submitTask(task);
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(
                    String.format("%s.process", TestBasicFailProcessor.class.getSimpleName()));
                span.hasKind(SpanKind.INTERNAL);
                span.hasStatus(StatusData.error());
                span.hasAttributesSatisfying(
                    attributeAssertions(
                        TestBasicFailProcessor.class.getName(), jobId, jobParam, BASIC_PROCESSOR));
              });
        });
  }

  @Test
  void testBroadcastProcessor() {
    WorkerRuntime workerRuntime = genWorkerRuntime();

    long jobId = 1;
    String jobParam = "abc";
    TaskTrackerStartTaskReq req =
        genTaskTrackerStartTaskReq(
            TestBroadcastProcessor.class.getName(), jobId, jobParam, ExecuteType.BROADCAST);
    TaskDO task = genTask(req);
    ProcessorTracker pt = new ProcessorTracker(req, workerRuntime);
    pt.submitTask(task);
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(
                    String.format("%s.process", TestBroadcastProcessor.class.getSimpleName()));
                span.hasKind(SpanKind.INTERNAL);
                span.hasStatus(StatusData.unset());
                span.hasAttributesSatisfying(
                    attributeAssertions(
                        TestBroadcastProcessor.class.getName(),
                        jobId,
                        jobParam,
                        BROADCAST_PROCESSOR));
              });
        });
  }

  @Test
  void testMapProcessor() {
    WorkerRuntime workerRuntime = genWorkerRuntime();

    long jobId = 1;
    String jobParam = "abc";
    TaskTrackerStartTaskReq req =
        genTaskTrackerStartTaskReq(
            TestMapProcessProcessor.class.getName(), jobId, jobParam, ExecuteType.MAP);
    TaskDO task = genTask(req);
    ProcessorTracker pt = new ProcessorTracker(req, workerRuntime);
    pt.submitTask(task);
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(
                    String.format("%s.process", TestMapProcessProcessor.class.getSimpleName()));
                span.hasKind(SpanKind.INTERNAL);
                span.hasStatus(StatusData.unset());
                span.hasAttributesSatisfying(
                    attributeAssertions(
                        TestMapProcessProcessor.class.getName(), jobId, jobParam, MAP_PROCESSOR));
              });
        });
  }

  @Test
  void testMapReduceProcessor() {
    WorkerRuntime workerRuntime = genWorkerRuntime();

    long jobId = 1;
    String jobParam = "abc";
    TaskTrackerStartTaskReq req =
        genTaskTrackerStartTaskReq(
            TestMapReduceProcessProcessor.class.getName(), jobId, jobParam, ExecuteType.MAP_REDUCE);
    TaskDO task = genTask(req);
    ProcessorTracker pt = new ProcessorTracker(req, workerRuntime);
    pt.submitTask(task);
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(
                    String.format(
                        "%s.process", TestMapReduceProcessProcessor.class.getSimpleName()));
                span.hasKind(SpanKind.INTERNAL);
                span.hasStatus(StatusData.unset());
                span.hasAttributesSatisfying(
                    attributeAssertions(
                        TestMapReduceProcessProcessor.class.getName(),
                        jobId,
                        jobParam,
                        MAP_REDUCE_PROCESSOR));
              });
        });
  }

  @Test
  void testShellProcessor() {
    WorkerRuntime workerRuntime = genWorkerRuntime();
    workerRuntime.setOmsLogHandler(
        new OmsLogHandler(
            "", workerRuntime.getActorSystem(), workerRuntime.getServerDiscoveryService()));

    long jobId = 1;
    String jobParam = "ls";
    TaskTrackerStartTaskReq req =
        genTaskTrackerStartTaskReq(
            ShellProcessor.class.getName(), jobId, jobParam, ExecuteType.STANDALONE);
    TaskDO task = genTask(req);
    ProcessorTracker pt = new ProcessorTracker(req, workerRuntime);
    pt.submitTask(task);
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(String.format("%s.process", ShellProcessor.class.getSimpleName()));
                span.hasKind(SpanKind.INTERNAL);
                span.hasStatus(StatusData.unset());
                span.hasAttributesSatisfying(
                    attributeAssertions(
                        ShellProcessor.class.getName(), jobId, jobParam, SHELL_PROCESSOR));
              });
        });
  }

  @Test
  void testPythonProcessor() {
    WorkerRuntime workerRuntime = genWorkerRuntime();
    workerRuntime.setOmsLogHandler(
        new OmsLogHandler(
            "", workerRuntime.getActorSystem(), workerRuntime.getServerDiscoveryService()));

    long jobId = 1;
    String jobParam = "1+1";
    TaskTrackerStartTaskReq req =
        genTaskTrackerStartTaskReq(
            PythonProcessor.class.getName(), jobId, jobParam, ExecuteType.STANDALONE);
    TaskDO task = genTask(req);
    ProcessorTracker pt = new ProcessorTracker(req, workerRuntime);
    pt.submitTask(task);
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(String.format("%s.process", PythonProcessor.class.getSimpleName()));
                span.hasKind(SpanKind.INTERNAL);
                span.hasStatus(StatusData.unset());
                span.hasAttributesSatisfying(
                    attributeAssertions(
                        PythonProcessor.class.getName(), jobId, jobParam, PYTHON_PROCESSOR));
              });
        });
  }

  @Test
  void testHttpProcessor() {
    WorkerRuntime workerRuntime = genWorkerRuntime();
    workerRuntime.setOmsLogHandler(
        new OmsLogHandler(
            "", workerRuntime.getActorSystem(), workerRuntime.getServerDiscoveryService()));

    long jobId = 1;
    String jobParam = "{\"method\":\"GET\"}";
    TaskTrackerStartTaskReq req =
        genTaskTrackerStartTaskReq(
            HttpProcessor.class.getName(), jobId, jobParam, ExecuteType.STANDALONE);
    TaskDO task = genTask(req);
    ProcessorTracker pt = new ProcessorTracker(req, workerRuntime);
    pt.submitTask(task);
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(String.format("%s.process", HttpProcessor.class.getSimpleName()));
                span.hasKind(SpanKind.INTERNAL);
                span.hasStatus(StatusData.error());
                span.hasAttributesSatisfying(
                    attributeAssertions(
                        HttpProcessor.class.getName(), jobId, jobParam, HTTP_PROCESSOR));
              });
        });
  }

  @Test
  void testFileCleanerProcessor() {
    WorkerRuntime workerRuntime = genWorkerRuntime();
    workerRuntime.setOmsLogHandler(
        new OmsLogHandler(
            "", workerRuntime.getActorSystem(), workerRuntime.getServerDiscoveryService()));

    long jobId = 1;
    String jobParam = "{\"dirPath\":\"/abc\"}";
    TaskTrackerStartTaskReq req =
        genTaskTrackerStartTaskReq(
            FileCleanupProcessor.class.getName(), jobId, jobParam, ExecuteType.STANDALONE);
    TaskDO task = genTask(req);
    ProcessorTracker pt = new ProcessorTracker(req, workerRuntime);
    pt.submitTask(task);
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(
                    String.format("%s.process", FileCleanupProcessor.class.getSimpleName()));
                span.hasKind(SpanKind.INTERNAL);
                span.hasStatus(StatusData.error());
                span.hasAttributesSatisfying(
                    attributeAssertions(
                        FileCleanupProcessor.class.getName(),
                        jobId,
                        jobParam,
                        FILE_CLEANUP_PROCESSOR));
              });
        });
  }

  @Test
  void testSpringDataSourceProcessor() {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    DataSource dataSource = new HikariDataSource();
    context.registerBean(
        SpringDatasourceSqlProcessor.class, () -> new SpringDatasourceSqlProcessor(dataSource));
    SpringUtils.inject(context);
    context.refresh();
    WorkerRuntime workerRuntime = genWorkerRuntime();
    workerRuntime.setOmsLogHandler(
        new OmsLogHandler(
            "", workerRuntime.getActorSystem(), workerRuntime.getServerDiscoveryService()));

    long jobId = 1;
    String jobParam = "{\"dirPath\":\"/abc\"}";
    TaskTrackerStartTaskReq req =
        genTaskTrackerStartTaskReq(
            SpringDatasourceSqlProcessor.class.getName(), jobId, jobParam, ExecuteType.STANDALONE);
    TaskDO task = genTask(req);
    ProcessorTracker pt = new ProcessorTracker(req, workerRuntime);
    pt.submitTask(task);
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(
                    String.format(
                        "%s.process", SpringDatasourceSqlProcessor.class.getSimpleName()));
                span.hasKind(SpanKind.INTERNAL);
                span.hasStatus(StatusData.error());
                span.hasAttributesSatisfying(
                    attributeAssertions(
                        SpringDatasourceSqlProcessor.class.getName(),
                        jobId,
                        jobParam,
                        SPRING_DATASOURCE_SQL_PROCESSOR));
              });
        });
  }

  @Test
  void testDynamicDataSourceProcessor() {
    WorkerRuntime workerRuntime = genWorkerRuntime();
    workerRuntime.setOmsLogHandler(
        new OmsLogHandler(
            "", workerRuntime.getActorSystem(), workerRuntime.getServerDiscoveryService()));

    long jobId = 1;
    String jobParam = "{\"dirPath\":\"/abc\"}";
    TaskTrackerStartTaskReq req =
        genTaskTrackerStartTaskReq(
            DynamicDatasourceSqlProcessor.class.getName(), jobId, jobParam, ExecuteType.STANDALONE);
    TaskDO task = genTask(req);
    ProcessorTracker pt = new ProcessorTracker(req, workerRuntime);
    pt.submitTask(task);
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(
                    String.format(
                        "%s.process", DynamicDatasourceSqlProcessor.class.getSimpleName()));
                span.hasKind(SpanKind.INTERNAL);
                span.hasStatus(StatusData.error());
                span.hasAttributesSatisfying(
                    attributeAssertions(
                        DynamicDatasourceSqlProcessor.class.getName(),
                        jobId,
                        jobParam,
                        DYNAMIC_DATASOURCE_SQL_PROCESSOR));
              });
        });
  }

  private static WorkerRuntime genWorkerRuntime() {
    Map<String, Object> overrideConfig = Maps.newHashMap();
    Config akkaFinalConfig = ConfigFactory.parseMap(overrideConfig);
    ActorSystem actorSystem =
        ActorSystem.create(RemoteConstant.WORKER_ACTOR_SYSTEM_NAME, akkaFinalConfig);
    WorkerRuntime workerRuntime = new WorkerRuntime();
    workerRuntime.setAppId(1L);
    workerRuntime.setActorSystem(actorSystem);
    workerRuntime.setWorkerConfig(new PowerJobWorkerConfig());
    return workerRuntime;
  }

  private static TaskDO genTask(TaskTrackerStartTaskReq req) {
    TaskDO task = new TaskDO();
    task.setTaskId(req.getTaskId());
    task.setTaskName(req.getTaskName());
    task.setTaskContent(req.getTaskContent());
    task.setFailedCnt(req.getTaskCurrentRetryNums());
    task.setSubInstanceId(req.getSubInstanceId());
    return task;
  }

  private static TaskTrackerStartTaskReq genTaskTrackerStartTaskReq(
      String processor, long jobId, String jobParam, ExecuteType executeType) {
    InstanceInfo instanceInfo = new InstanceInfo();
    instanceInfo.setJobId(jobId);
    instanceInfo.setInstanceId(10086L);
    instanceInfo.setJobParams(jobParam);
    instanceInfo.setExecuteType(executeType.name());
    instanceInfo.setProcessorType(ProcessorType.BUILT_IN.name());
    instanceInfo.setProcessorInfo(processor);
    instanceInfo.setInstanceTimeoutMS(500000);
    instanceInfo.setThreadConcurrency(5);
    instanceInfo.setTaskRetryNum(3);

    TaskTrackerStartTaskReq req = new TaskTrackerStartTaskReq();
    req.setTaskTrackerAddress(NetUtils.getLocalHost() + ":27777");
    req.setInstanceInfo(instanceInfo);
    req.setTaskId("0");
    req.setTaskName("ROOT_TASK");
    req.setTaskCurrentRetryNums(0);
    return req;
  }

  private static List<AttributeAssertion> attributeAssertions(
      String codeNamespace, long jobId, String jobParam, String jobType) {
    List<AttributeAssertion> attributeAssertions =
        new ArrayList<>(
            asList(
                equalTo(AttributeKey.stringKey("code.namespace"), codeNamespace),
                equalTo(AttributeKey.stringKey("code.function"), "process"),
                equalTo(AttributeKey.stringKey("job.system"), "powerjob"),
                equalTo(AttributeKey.longKey("scheduling.powerjob.job.id"), jobId),
                equalTo(AttributeKey.stringKey("scheduling.powerjob.job.type"), jobType)));
    if (!StringUtils.isNullOrEmpty(jobParam)) {
      attributeAssertions.add(
          equalTo(AttributeKey.stringKey("scheduling.powerjob.job.param"), jobParam));
    }
    return attributeAssertions;
  }
}
