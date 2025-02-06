/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.powerjob.v4_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Arrays.asList;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.zaxxer.hikari.HikariDataSource;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.internal.StringUtils;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import tech.powerjob.official.processors.impl.FileCleanupProcessor;
import tech.powerjob.official.processors.impl.HttpProcessor;
import tech.powerjob.official.processors.impl.script.PythonProcessor;
import tech.powerjob.official.processors.impl.script.ShellProcessor;
import tech.powerjob.official.processors.impl.sql.DynamicDatasourceSqlProcessor;
import tech.powerjob.official.processors.impl.sql.SpringDatasourceSqlProcessor;
import tech.powerjob.worker.core.processor.TaskContext;
import tech.powerjob.worker.core.processor.WorkflowContext;
import tech.powerjob.worker.core.processor.sdk.BasicProcessor;
import tech.powerjob.worker.log.OmsLogger;

class PowerJobBasicProcessorTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final String BASIC_PROCESSOR = "BasicProcessor";
  private static final String BROADCAST_PROCESSOR = "BroadcastProcessor";
  private static final String MAP_PROCESSOR = "MapProcessor";
  private static final String MAP_REDUCE_PROCESSOR = "MapReduceProcessor";
  private static final String SHELL_PROCESSOR = "ShellProcessor";
  private static final String PYTHON_PROCESSOR = "PythonProcessor";
  private static final String HTTP_PROCESSOR = "HttpProcessor";
  private static final String FILE_CLEANUP_PROCESSOR = "FileCleanupProcessor";
  private static final String SPRING_DATASOURCE_SQL_PROCESSOR = "SpringDatasourceSqlProcessor";
  private static final String DYNAMIC_DATASOURCE_SQL_PROCESSOR = "DynamicDatasourceSqlProcessor";

  @Test
  void testBasicProcessor() throws Exception {
    long jobId = 1;
    String jobParam = "abc";
    TaskContext taskContext = genTaskContext(jobId, jobParam);
    BasicProcessor testBasicProcessor = new TestBasicProcessor();
    testBasicProcessor.process(taskContext);
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(String.format("%s.process", TestBasicProcessor.class.getSimpleName()))
                    .hasKind(SpanKind.INTERNAL)
                    .hasStatus(StatusData.unset())
                    .hasAttributesSatisfyingExactly(
                        attributeAssertions(
                            TestBasicProcessor.class.getName(), jobId, jobParam, BASIC_PROCESSOR));
              });
        });
  }

  @Test
  void testBasicFailProcessor() throws Exception {
    long jobId = 1;
    String jobParam = "abc";
    TaskContext taskContext = genTaskContext(jobId, jobParam);
    BasicProcessor testBasicFailProcessor = new TestBasicFailProcessor();
    testBasicFailProcessor.process(taskContext);
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(
                        String.format("%s.process", TestBasicFailProcessor.class.getSimpleName()))
                    .hasKind(SpanKind.INTERNAL)
                    .hasStatus(StatusData.error())
                    .hasAttributesSatisfyingExactly(
                        attributeAssertions(
                            TestBasicFailProcessor.class.getName(),
                            jobId,
                            jobParam,
                            BASIC_PROCESSOR));
              });
        });
  }

  @Test
  void testBroadcastProcessor() throws Exception {
    long jobId = 1;
    String jobParam = "abc";
    TaskContext taskContext = genTaskContext(jobId, jobParam);
    BasicProcessor testBroadcastProcessor = new TestBroadcastProcessor();
    testBroadcastProcessor.process(taskContext);
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(
                        String.format("%s.process", TestBroadcastProcessor.class.getSimpleName()))
                    .hasKind(SpanKind.INTERNAL)
                    .hasStatus(StatusData.unset())
                    .hasAttributesSatisfyingExactly(
                        attributeAssertions(
                            TestBroadcastProcessor.class.getName(),
                            jobId,
                            jobParam,
                            BROADCAST_PROCESSOR));
              });
        });
  }

  @Test
  void testMapProcessor() throws Exception {
    long jobId = 1;
    String jobParam = "abc";
    TaskContext taskContext = genTaskContext(jobId, jobParam);
    BasicProcessor testMapProcessProcessor = new TestMapProcessProcessor();
    testMapProcessProcessor.process(taskContext);
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(
                        String.format("%s.process", TestMapProcessProcessor.class.getSimpleName()))
                    .hasKind(SpanKind.INTERNAL)
                    .hasStatus(StatusData.unset())
                    .hasAttributesSatisfyingExactly(
                        attributeAssertions(
                            TestMapProcessProcessor.class.getName(),
                            jobId,
                            jobParam,
                            MAP_PROCESSOR));
              });
        });
  }

  @Test
  void testMapReduceProcessor() throws Exception {
    long jobId = 1;
    String jobParam = "abc";
    TaskContext taskContext = genTaskContext(jobId, jobParam);
    BasicProcessor testMapReduceProcessProcessor = new TestMapReduceProcessProcessor();
    testMapReduceProcessProcessor.process(taskContext);
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(
                        String.format(
                            "%s.process", TestMapReduceProcessProcessor.class.getSimpleName()))
                    .hasKind(SpanKind.INTERNAL)
                    .hasStatus(StatusData.unset())
                    .hasAttributesSatisfyingExactly(
                        attributeAssertions(
                            TestMapReduceProcessProcessor.class.getName(),
                            jobId,
                            jobParam,
                            MAP_REDUCE_PROCESSOR));
              });
        });
  }

  @Test
  void testShellProcessor() throws Exception {
    long jobId = 1;
    String jobParam = "ls";
    TaskContext taskContext = genTaskContext(jobId, jobParam);
    taskContext.setWorkflowContext(new WorkflowContext(jobId, ""));
    taskContext.setOmsLogger(new TestOmsLogger());
    BasicProcessor shellProcessor = new ShellProcessor();
    shellProcessor.process(taskContext);
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(String.format("%s.process", ShellProcessor.class.getSimpleName()))
                    .hasKind(SpanKind.INTERNAL)
                    .hasStatus(StatusData.unset())
                    .hasAttributesSatisfyingExactly(
                        attributeAssertions(
                            ShellProcessor.class.getName(), jobId, jobParam, SHELL_PROCESSOR));
              });
        });
  }

  @Test
  void testPythonProcessor() throws Exception {
    long jobId = 1;
    String jobParam = "1+1";
    TaskContext taskContext = genTaskContext(jobId, jobParam);
    taskContext.setWorkflowContext(new WorkflowContext(jobId, ""));
    taskContext.setOmsLogger(new TestOmsLogger());
    BasicProcessor pythonProcessor = new PythonProcessor();
    pythonProcessor.process(taskContext);
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                // not asserting status as it is either unset or error depending on whether python
                // is available or not
                span.hasName(String.format("%s.process", PythonProcessor.class.getSimpleName()))
                    .hasKind(SpanKind.INTERNAL)
                    .hasAttributesSatisfyingExactly(
                        attributeAssertions(
                            PythonProcessor.class.getName(), jobId, jobParam, PYTHON_PROCESSOR));
              });
        });
  }

  @Test
  void testHttpProcessor() throws Exception {
    long jobId = 1;
    String jobParam = "{\"method\":\"GET\"}";
    TaskContext taskContext = genTaskContext(jobId, jobParam);
    taskContext.setWorkflowContext(new WorkflowContext(jobId, ""));
    taskContext.setOmsLogger(new TestOmsLogger());
    BasicProcessor httpProcessor = new HttpProcessor();
    httpProcessor.process(taskContext);
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(String.format("%s.process", HttpProcessor.class.getSimpleName()))
                    .hasKind(SpanKind.INTERNAL)
                    .hasStatus(StatusData.error())
                    .hasAttributesSatisfyingExactly(
                        attributeAssertions(
                            HttpProcessor.class.getName(), jobId, jobParam, HTTP_PROCESSOR));
              });
        });
  }

  @Test
  void testFileCleanerProcessor() throws Exception {
    long jobId = 1;
    JSONObject params = new JSONObject();
    params.put("dirPath", "/abc");
    params.put("filePattern", "[\\s\\S]*log");
    params.put("retentionTime", 0);
    JSONArray array = new JSONArray();
    array.add(params);
    String jobParam = array.toJSONString();
    TaskContext taskContext = genTaskContext(jobId, jobParam);
    taskContext.setOmsLogger(new TestOmsLogger());
    BasicProcessor fileCleanupProcessor = new FileCleanupProcessor();
    fileCleanupProcessor.process(taskContext);
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(
                        String.format("%s.process", FileCleanupProcessor.class.getSimpleName()))
                    .hasKind(SpanKind.INTERNAL)
                    .hasStatus(StatusData.unset())
                    .hasAttributesSatisfyingExactly(
                        attributeAssertions(
                            FileCleanupProcessor.class.getName(),
                            jobId,
                            jobParam,
                            FILE_CLEANUP_PROCESSOR));
              });
        });
  }

  @Test
  void testSpringDataSourceProcessor() throws Exception {
    DataSource dataSource = new HikariDataSource();
    long jobId = 1;
    String jobParam = "{\"dirPath\":\"/abc\"}";
    TaskContext taskContext = genTaskContext(jobId, jobParam);
    taskContext.setWorkflowContext(new WorkflowContext(jobId, ""));
    taskContext.setOmsLogger(new TestOmsLogger());
    BasicProcessor springDatasourceSqlProcessor = new SpringDatasourceSqlProcessor(dataSource);
    springDatasourceSqlProcessor.process(taskContext);
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(
                        String.format(
                            "%s.process", SpringDatasourceSqlProcessor.class.getSimpleName()))
                    .hasKind(SpanKind.INTERNAL)
                    .hasStatus(StatusData.error())
                    .hasAttributesSatisfyingExactly(
                        attributeAssertions(
                            SpringDatasourceSqlProcessor.class.getName(),
                            jobId,
                            jobParam,
                            SPRING_DATASOURCE_SQL_PROCESSOR));
              });
        });
  }

  @Test
  void testDynamicDataSourceProcessor() throws Exception {
    long jobId = 1;
    String jobParam = "{\"dirPath\":\"/abc\"}";
    TaskContext taskContext = genTaskContext(jobId, jobParam);
    taskContext.setWorkflowContext(new WorkflowContext(jobId, ""));
    taskContext.setOmsLogger(new TestOmsLogger());
    BasicProcessor dynamicDatasourceSqlProcessor = new DynamicDatasourceSqlProcessor();
    dynamicDatasourceSqlProcessor.process(taskContext);
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(
                        String.format(
                            "%s.process", DynamicDatasourceSqlProcessor.class.getSimpleName()))
                    .hasKind(SpanKind.INTERNAL)
                    .hasStatus(StatusData.error())
                    .hasAttributesSatisfyingExactly(
                        attributeAssertions(
                            DynamicDatasourceSqlProcessor.class.getName(),
                            jobId,
                            jobParam,
                            DYNAMIC_DATASOURCE_SQL_PROCESSOR));
              });
        });
  }

  private static TaskContext genTaskContext(long jobId, String jobParam) {
    TaskContext taskContext = new TaskContext();
    taskContext.setJobId(jobId);
    taskContext.setJobParams(jobParam);
    return taskContext;
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  private static List<AttributeAssertion> attributeAssertions(
      String codeNamespace, long jobId, String jobParam, String jobType) {
    List<AttributeAssertion> attributeAssertions =
        new ArrayList<>(
            asList(
                equalTo(CodeIncubatingAttributes.CODE_NAMESPACE, codeNamespace),
                equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "process"),
                equalTo(AttributeKey.stringKey("job.system"), "powerjob"),
                equalTo(AttributeKey.longKey("scheduling.powerjob.job.id"), jobId),
                equalTo(AttributeKey.stringKey("scheduling.powerjob.job.type"), jobType)));
    if (!StringUtils.isNullOrEmpty(jobParam)) {
      attributeAssertions.add(
          equalTo(AttributeKey.stringKey("scheduling.powerjob.job.param"), jobParam));
    }
    return attributeAssertions;
  }

  private static class TestOmsLogger implements OmsLogger {

    @Override
    public void debug(String s, Object... objects) {}

    @Override
    public void info(String s, Object... objects) {}

    @Override
    public void warn(String s, Object... objects) {}

    @Override
    public void error(String s, Object... objects) {}
  }
}
