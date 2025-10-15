/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.comparingRootSpanAttribute;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.shardingsphere.elasticjob.api.JobConfiguration;
import org.apache.shardingsphere.elasticjob.http.props.HttpJobProperties;
import org.apache.shardingsphere.elasticjob.lite.api.bootstrap.impl.ScheduleJobBootstrap;
import org.apache.shardingsphere.elasticjob.reg.base.CoordinatorRegistryCenter;
import org.apache.shardingsphere.elasticjob.reg.zookeeper.ZookeeperConfiguration;
import org.apache.shardingsphere.elasticjob.reg.zookeeper.ZookeeperRegistryCenter;
import org.apache.shardingsphere.elasticjob.script.props.ScriptJobProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ElasticJobTest {

  private static final int EMBED_ZOOKEEPER_PORT = 4181;
  private static final String ZOOKEEPER_CONNECTION_STRING = "localhost:" + EMBED_ZOOKEEPER_PORT;

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static CoordinatorRegistryCenter regCenter;
  private static int port;
  private static HttpServer httpServer;

  @BeforeAll
  static void init() throws IOException {
    EmbedZookeeperServer.start(EMBED_ZOOKEEPER_PORT);
    regCenter = setUpRegistryCenter();
    port = PortUtils.findOpenPort();
    httpServer = HttpServer.create(new InetSocketAddress(port), 0);
    httpServer.createContext(
        "/hello",
        new HttpHandler() {
          @Override
          public void handle(HttpExchange exchange) throws IOException {
            byte[] response = "{\"success\": true}".getBytes(UTF_8);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
          }
        });
    new Thread(() -> httpServer.start()).start();
  }

  @AfterAll
  static void stop() {
    if (httpServer != null) {
      httpServer.stop(0);
    }
    if (regCenter != null) {
      regCenter.close();
    }
    EmbedZookeeperServer.stop();
  }

  @Test
  void testHttpJob() {
    ScheduleJobBootstrap bootstrap = setUpHttpJob(regCenter);
    try {
      bootstrap.schedule();

      testing.waitAndAssertSortedTraces(
          comparingRootSpanAttribute(longKey("scheduling.apache-elasticjob.item")),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasKind(SpanKind.INTERNAL)
                          .hasName("HTTP")
                          .hasAttributesSatisfyingExactly(
                              elasticJobBaseAttributes(
                                  "javaHttpJob",
                                  0L,
                                  3L,
                                  "{0=Beijing, 1=Shanghai, 2=Guangzhou}"))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasKind(SpanKind.INTERNAL)
                          .hasName("HTTP")
                          .hasAttributesSatisfyingExactly(
                              elasticJobBaseAttributes(
                                  "javaHttpJob",
                                  1L,
                                  3L,
                                  "{0=Beijing, 1=Shanghai, 2=Guangzhou}"))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasKind(SpanKind.INTERNAL)
                          .hasName("HTTP")
                          .hasAttributesSatisfyingExactly(
                              elasticJobBaseAttributes(
                                  "javaHttpJob",
                                  2L,
                                  3L,
                                  "{0=Beijing, 1=Shanghai, 2=Guangzhou}"))));
    } finally {
      bootstrap.shutdown();
    }
  }

  @Test
  void testSimpleJob() {
    TestSimpleJob job = new TestSimpleJob();
    ScheduleJobBootstrap bootstrap =
        new ScheduleJobBootstrap(
            regCenter,
            job,
            JobConfiguration.newBuilder("simpleElasticJob", 2)
                .cron("0/5 * * * * ?")
                .shardingItemParameters("0=A,1=B")
                .build());

    try {
      bootstrap.schedule();

      testing.waitAndAssertSortedTraces(
          comparingRootSpanAttribute(longKey("scheduling.apache-elasticjob.item")),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasKind(SpanKind.INTERNAL)
                          .hasName("TestSimpleJob.execute")
                          .hasAttributesSatisfyingExactly(
                              elasticJobWithCodeAttributes(
                                  "simpleElasticJob",
                                  0L,
                                  2L,
                                  "A",
                                  "execute",
                                  "io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0.TestSimpleJob"))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasKind(SpanKind.INTERNAL)
                          .hasName("TestSimpleJob.execute")
                          .hasAttributesSatisfyingExactly(
                              elasticJobWithCodeAttributes(
                                  "simpleElasticJob",
                                  1L,
                                  2L,
                                  "B",
                                  "execute",
                                  "io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0.TestSimpleJob"))));
    } finally {
      bootstrap.shutdown();
    }
  }

  @Test
  void testDataflowJob() {
    TestDataflowJob job = new TestDataflowJob();
    ScheduleJobBootstrap bootstrap =
        new ScheduleJobBootstrap(
            regCenter,
            job,
            JobConfiguration.newBuilder("dataflowElasticJob", 2)
                .cron("0/5 * * * * ?")
                .shardingItemParameters("0=X,1=Y")
                .build());

    try {
      bootstrap.schedule();

      testing.waitAndAssertSortedTraces(
          comparingRootSpanAttribute(longKey("scheduling.apache-elasticjob.item")),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasKind(SpanKind.INTERNAL)
                          .hasName("TestDataflowJob.processData")
                          .hasAttributesSatisfyingExactly(
                              elasticJobWithCodeAttributes(
                                  "dataflowElasticJob",
                                  0L,
                                  2L,
                                  "X",
                                  "processData",
                                  "io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0.TestDataflowJob"))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasKind(SpanKind.INTERNAL)
                          .hasName("TestDataflowJob.processData")
                          .hasAttributesSatisfyingExactly(
                              elasticJobWithCodeAttributes(
                                  "dataflowElasticJob",
                                  1L,
                                  2L,
                                  "Y",
                                  "processData",
                                  "io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0.TestDataflowJob"))));
    } finally {
      bootstrap.shutdown();
    }
  }

  @Test
  void testScriptJob() throws IOException {
    ScheduleJobBootstrap bootstrap = setUpScriptJob(regCenter);
    try {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasKind(SpanKind.INTERNAL)
                          .hasName("SCRIPT")
                          .hasAttributesSatisfyingExactly(
                              elasticJobBaseAttributes(
                                  "scriptElasticJob", 0L, 1L, "{0=null}"))));
    } finally {
      bootstrap.shutdown();
    }
  }

  @Test
  void testFailedJob() {
    TestFailedJob job = new TestFailedJob();
    ScheduleJobBootstrap bootstrap =
        new ScheduleJobBootstrap(
            regCenter,
            job,
            JobConfiguration.newBuilder("failedElasticJob", 1)
                .cron("0/5 * * * * ?")
                .shardingItemParameters("0=failed")
                .build());

    try {
      bootstrap.schedule();

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasKind(SpanKind.INTERNAL)
                          .hasName("TestFailedJob.execute")
                          .hasStatus(StatusData.error())
                          .hasException(new RuntimeException("Simulated job failure for testing"))
                          .hasAttributesSatisfyingExactly(
                              elasticJobWithCodeAttributes(
                                  "failedElasticJob",
                                  0L,
                                  1L,
                                  "failed",
                                  "execute",
                                  "io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0.TestFailedJob"))));
    } finally {
      bootstrap.shutdown();
    }
  }

  private static CoordinatorRegistryCenter setUpRegistryCenter() {
    ZookeeperConfiguration zkConfig =
        new ZookeeperConfiguration(ZOOKEEPER_CONNECTION_STRING, "elasticjob-example-lite-java");
    CoordinatorRegistryCenter result = new ZookeeperRegistryCenter(zkConfig);
    result.init();
    return result;
  }

  private static ScheduleJobBootstrap setUpHttpJob(CoordinatorRegistryCenter regCenter) {
    return new ScheduleJobBootstrap(
        regCenter,
        "HTTP",
        JobConfiguration.newBuilder("javaHttpJob", 3)
            .setProperty(HttpJobProperties.URI_KEY, "http://localhost:" + port + "/hello")
            .setProperty(HttpJobProperties.METHOD_KEY, "GET")
            .cron("0/5 * * * * ?")
            .shardingItemParameters("0=Beijing,1=Shanghai,2=Guangzhou")
            .build());
  }

  private static ScheduleJobBootstrap setUpScriptJob(CoordinatorRegistryCenter regCenter)
      throws IOException {
    ScheduleJobBootstrap bootstrap =
        new ScheduleJobBootstrap(
            regCenter,
            "SCRIPT",
            JobConfiguration.newBuilder("scriptElasticJob", 1)
                .cron("0/5 * * * * ?")
                .setProperty(ScriptJobProperties.SCRIPT_KEY, buildScriptCommandLine())
                .build());
    bootstrap.schedule();
    return bootstrap;
  }

  private static String buildScriptCommandLine() throws IOException {
    String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    String scriptName = os.contains("win") ? "/script/demo.bat" : "/script/demo.sh";
    String extension = os.contains("win") ? ".bat" : ".sh";

    Path tempScript = Files.createTempFile("elasticjob-test-", extension);
    tempScript.toFile().deleteOnExit();

    try (InputStream scriptStream = ElasticJobTest.class.getResourceAsStream(scriptName)) {
      if (scriptStream == null) {
        throw new IOException("Script resource not found: " + scriptName);
      }
      Files.copy(scriptStream, tempScript, StandardCopyOption.REPLACE_EXISTING);
    }

    File scriptFile = tempScript.toFile();
    if (!scriptFile.setExecutable(true)) {
      throw new IOException("Failed to set executable permission on script: " + tempScript);
    }

    return tempScript.toString();
  }

  private static List<AttributeAssertion> elasticJobBaseAttributes(
      String jobName, long item, long totalCount, String parameters) {
    List<AttributeAssertion> assertions = new ArrayList<>();
    assertions.add(equalTo(stringKey("job.system"), "elasticjob"));
    assertions.add(equalTo(stringKey("scheduling.apache-elasticjob.job.name"), jobName));
    assertions.add(equalTo(longKey("scheduling.apache-elasticjob.item"), item));
    assertions.add(
        equalTo(
            longKey("scheduling.apache-elasticjob.sharding.total.count"), totalCount));
    assertions.add(
        equalTo(stringKey("scheduling.apache-elasticjob.sharding.item.parameters"), parameters));
    assertions.add(
        satisfies(
            stringKey("scheduling.apache-elasticjob.task.id"),
            taskId -> taskId.contains(jobName)));
    return assertions;
  }

  private static List<AttributeAssertion> elasticJobWithCodeAttributes(
      String jobName,
      long item,
      long totalCount,
      String parameters,
      String codeFunction,
      String codeNamespace) {
    List<AttributeAssertion> assertions = new ArrayList<>();
    assertions.add(equalTo(stringKey("code.function"), codeFunction));
    assertions.add(equalTo(stringKey("code.namespace"), codeNamespace));
    assertions.addAll(elasticJobBaseAttributes(jobName, item, totalCount, parameters));
    return assertions;
  }
}
