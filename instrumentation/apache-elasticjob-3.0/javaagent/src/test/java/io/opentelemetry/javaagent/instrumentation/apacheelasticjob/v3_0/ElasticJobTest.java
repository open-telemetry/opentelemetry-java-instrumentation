/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.shardingsphere.elasticjob.api.JobConfiguration;
import org.apache.shardingsphere.elasticjob.api.ShardingContext;
import org.apache.shardingsphere.elasticjob.http.props.HttpJobProperties;
import org.apache.shardingsphere.elasticjob.lite.api.bootstrap.impl.ScheduleJobBootstrap;
import org.apache.shardingsphere.elasticjob.reg.base.CoordinatorRegistryCenter;
import org.apache.shardingsphere.elasticjob.reg.zookeeper.ZookeeperConfiguration;
import org.apache.shardingsphere.elasticjob.reg.zookeeper.ZookeeperRegistryCenter;
import org.apache.shardingsphere.elasticjob.script.props.ScriptJobProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class ElasticJobTest {

  private static final int EMBED_ZOOKEEPER_PORT = 4181;
  private static final String ZOOKEEPER_CONNECTION_STRING = "localhost:" + EMBED_ZOOKEEPER_PORT;

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static CoordinatorRegistryCenter regCenter;
  private static int port;
  private static HttpServer httpServer;

  @BeforeAll
  public static void init() throws IOException {
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
  public static void stop() {
    httpServer.stop(0);
  }

  @AfterEach
  public void clearSpans() {
    testing.clearData();

    try {
      Thread.sleep(100L);
    } catch (InterruptedException var2) {
      Thread.currentThread().interrupt();
    }
  }

  @Test
  public void testHttpJob() throws InterruptedException {
    ScheduleJobBootstrap bootstrap = setUpHttpJob(regCenter);
    try {
      bootstrap.schedule();
      await().atMost(Duration.ofSeconds(12)).until(() -> testing.spans().size() >= 3);
    } finally {
      bootstrap.shutdown();
    }

    List<SpanData> spans = testing.spans();
    assertThat(spans).hasSize(3);

    for (SpanData span : spans) {
      assertThat(span.getName()).isEqualTo("HTTP");
      assertThat(span.getKind()).isEqualTo(SpanKind.INTERNAL);
      assertThat(span.getAttributes().get(AttributeKey.stringKey("job.system")))
          .isEqualTo("elasticjob");
      assertThat(span.getAttributes().get(AttributeKey.stringKey("scheduling.apache-elasticjob.job.name")))
          .isEqualTo("javaHttpJob");
      assertThat(
              span.getAttributes()
                  .get(AttributeKey.longKey("scheduling.apache-elasticjob.sharding.total.count")))
          .isEqualTo(3L);
      assertThat(
              span.getAttributes()
                  .get(AttributeKey.stringKey("scheduling.apache-elasticjob.sharding.item.parameters")))
          .isEqualTo("{0=Beijing, 1=Shanghai, 2=Guangzhou}");

      assertThat(span.getAttributes().get(AttributeKey.longKey("scheduling.apache-elasticjob.item")))
          .isIn(0L, 1L, 2L);
      assertThat(span.getAttributes().get(AttributeKey.stringKey("scheduling.apache-elasticjob.task.id")))
          .contains("javaHttpJob");
    }

    Set<Long> shardItems =
        spans.stream()
            .map(
                span ->
                    span.getAttributes().get(AttributeKey.longKey("scheduling.apache-elasticjob.item")))
            .collect(Collectors.toSet());
    assertThat(shardItems).containsExactlyInAnyOrder(0L, 1L, 2L);
  }

  @Test
  public void testSimpleJob() throws InterruptedException {
    SynchronizedTestSimpleJob job = new SynchronizedTestSimpleJob(2);
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
      assertThat(job.awaitExecution(10, TimeUnit.SECONDS))
          .as("Job should execute within timeout")
          .isTrue();
      await().atMost(Duration.ofSeconds(2)).until(() -> testing.spans().size() >= 2);
    } finally {
      bootstrap.shutdown();
    }

    List<SpanData> spans = testing.spans();
    assertThat(spans).hasSize(2);

    for (SpanData span : spans) {
      assertThat(span.getName()).isEqualTo("SynchronizedTestSimpleJob.execute");
      assertThat(span.getKind()).isEqualTo(SpanKind.INTERNAL);
      assertThat(span.getAttributes().get(AttributeKey.stringKey("job.system")))
          .isEqualTo("elasticjob");
      assertThat(span.getAttributes().get(AttributeKey.stringKey("scheduling.apache-elasticjob.job.name")))
          .isEqualTo("simpleElasticJob");
      assertThat(
              span.getAttributes()
                  .get(AttributeKey.longKey("scheduling.apache-elasticjob.sharding.total.count")))
          .isEqualTo(2L);
      assertThat(span.getAttributes().get(AttributeKey.stringKey("code.function")))
          .isEqualTo("execute");
      assertThat(span.getAttributes().get(AttributeKey.stringKey("code.namespace")))
          .isEqualTo(
              "io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0.ElasticJobTest$SynchronizedTestSimpleJob");
      assertThat(span.getAttributes().get(AttributeKey.longKey("scheduling.apache-elasticjob.item")))
          .isIn(0L, 1L);
      assertThat(span.getAttributes().get(AttributeKey.stringKey("scheduling.apache-elasticjob.task.id")))
          .contains("simpleElasticJob");
    }

    Set<Long> shardItems =
        spans.stream()
            .map(
                span ->
                    span.getAttributes().get(AttributeKey.longKey("scheduling.apache-elasticjob.item")))
            .collect(Collectors.toSet());
    assertThat(shardItems).containsExactlyInAnyOrder(0L, 1L);
  }

  @Test
  public void testDataflowJob() throws InterruptedException {
    SynchronizedTestDataflowJob job = new SynchronizedTestDataflowJob(2);
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
      assertThat(job.awaitExecution(10, TimeUnit.SECONDS))
          .as("Job should execute within timeout")
          .isTrue();
      await().atMost(Duration.ofSeconds(2)).until(() -> testing.spans().size() >= 2);
    } finally {
      bootstrap.shutdown();
    }

    List<SpanData> spans = testing.spans();
    assertThat(spans).hasSize(2);

    for (SpanData span : spans) {
      assertThat(span.getName()).isEqualTo("SynchronizedTestDataflowJob.processData");
      assertThat(span.getKind()).isEqualTo(SpanKind.INTERNAL);
      assertThat(span.getAttributes().get(AttributeKey.stringKey("job.system")))
          .isEqualTo("elasticjob");
      assertThat(span.getAttributes().get(AttributeKey.stringKey("scheduling.apache-elasticjob.job.name")))
          .isEqualTo("dataflowElasticJob");
      assertThat(
              span.getAttributes()
                  .get(AttributeKey.longKey("scheduling.apache-elasticjob.sharding.total.count")))
          .isEqualTo(2L);
      assertThat(span.getAttributes().get(AttributeKey.stringKey("code.function")))
          .isEqualTo("processData");
      assertThat(span.getAttributes().get(AttributeKey.stringKey("code.namespace")))
          .isEqualTo(
              "io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0.ElasticJobTest$SynchronizedTestDataflowJob");
      assertThat(span.getAttributes().get(AttributeKey.longKey("scheduling.apache-elasticjob.item")))
          .isIn(0L, 1L);
      assertThat(span.getAttributes().get(AttributeKey.stringKey("scheduling.apache-elasticjob.task.id")))
          .contains("dataflowElasticJob");
    }

    Set<Long> shardItems =
        spans.stream()
            .map(
                span ->
                    span.getAttributes().get(AttributeKey.longKey("scheduling.apache-elasticjob.item")))
            .collect(Collectors.toSet());
    assertThat(shardItems).containsExactlyInAnyOrder(0L, 1L);
  }

  @Test
  public void testScriptJob() throws IOException {
    ScheduleJobBootstrap bootstrap = setUpScriptJob(regCenter);
    try {
      testing.waitAndAssertTracesWithoutScopeVersionVerification(
          trace ->
              trace
                  .hasSize(1)
                  .hasSpansSatisfyingExactly(
                      span ->
                          span.hasKind(SpanKind.INTERNAL)
                              .hasName("SCRIPT")
                              .hasAttributesSatisfyingExactly(
                                  equalTo(AttributeKey.stringKey("job.system"), "elasticjob"),
                                  equalTo(
                                      AttributeKey.stringKey("scheduling.apache-elasticjob.job.name"),
                                      "scriptElasticJob"),
                                  equalTo(AttributeKey.longKey("scheduling.apache-elasticjob.item"), 0L),
                                  equalTo(
                                      AttributeKey.longKey(
                                          "scheduling.apache-elasticjob.sharding.total.count"),
                                      1L),
                                  equalTo(
                                      AttributeKey.stringKey(
                                          "scheduling.apache-elasticjob.sharding.item.parameters"),
                                      "{0=null}"),
                                  satisfies(
                                      AttributeKey.stringKey("scheduling.apache-elasticjob.task.id"),
                                      taskId -> taskId.contains("scriptElasticJob")))));
    } finally {
      bootstrap.shutdown();
    }
  }

  @Test
  public void testFailedJob() throws InterruptedException {
    SynchronizedTestFailedJob job = new SynchronizedTestFailedJob(1);
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
      assertThat(job.awaitExecution(10, TimeUnit.SECONDS))
          .as("Job should execute within timeout")
          .isTrue();
      await().atMost(Duration.ofSeconds(2)).until(() -> testing.spans().size() >= 1);
    } finally {
      bootstrap.shutdown();
    }

    List<SpanData> spans = testing.spans();
    assertThat(spans).hasSize(1);

    SpanData span = spans.get(0);
    assertThat(span.getName()).isEqualTo("SynchronizedTestFailedJob.execute");
    assertThat(span.getKind()).isEqualTo(SpanKind.INTERNAL);
    assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    assertThat(span.getAttributes().get(AttributeKey.stringKey("job.system")))
        .isEqualTo("elasticjob");
    assertThat(span.getAttributes().get(AttributeKey.stringKey("scheduling.apache-elasticjob.job.name")))
        .isEqualTo("failedElasticJob");
    assertThat(span.getAttributes().get(AttributeKey.longKey("scheduling.apache-elasticjob.item")))
        .isEqualTo(0L);
    assertThat(
            span.getAttributes()
                .get(AttributeKey.longKey("scheduling.apache-elasticjob.sharding.total.count")))
        .isEqualTo(1L);
    assertThat(
            span.getAttributes()
                .get(AttributeKey.stringKey("scheduling.apache-elasticjob.sharding.item.parameters")))
        .isEqualTo("failed");
    assertThat(span.getAttributes().get(AttributeKey.stringKey("code.function")))
        .isEqualTo("execute");
    assertThat(span.getAttributes().get(AttributeKey.stringKey("code.namespace")))
        .isEqualTo(
            "io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0.ElasticJobTest$SynchronizedTestFailedJob");

    assertThat(span.getEvents()).hasSize(1);
    assertThat(span.getEvents().get(0).getName()).isEqualTo("exception");
    assertThat(
            span.getEvents().get(0).getAttributes().get(AttributeKey.stringKey("exception.type")))
        .isEqualTo("java.lang.RuntimeException");
    assertThat(
            span.getEvents()
                .get(0)
                .getAttributes()
                .get(AttributeKey.stringKey("exception.message")))
        .isEqualTo("Simulated job failure for testing");
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
    Path result = Paths.get(ElasticJobTest.class.getResource("/script/demo.sh").getPath());
    Files.setPosixFilePermissions(result, PosixFilePermissions.fromString("rwxr-xr-x"));
    return result.toString();
  }

  static class SynchronizedTestSimpleJob extends TestSimpleJob {
    private final CountDownLatch executionLatch;

    public SynchronizedTestSimpleJob(int expectedExecutions) {
      this.executionLatch = new CountDownLatch(expectedExecutions);
    }

    @Override
    public void execute(ShardingContext shardingContext) {
      super.execute(shardingContext);
      executionLatch.countDown();
    }

    public boolean awaitExecution(long timeout, TimeUnit unit) throws InterruptedException {
      return executionLatch.await(timeout, unit);
    }
  }

  static class SynchronizedTestDataflowJob extends TestDataflowJob {
    private final CountDownLatch executionLatch;

    public SynchronizedTestDataflowJob(int expectedExecutions) {
      this.executionLatch = new CountDownLatch(expectedExecutions);
    }

    @Override
    public void processData(ShardingContext shardingContext, List<String> data) {
      super.processData(shardingContext, data);
      executionLatch.countDown();
    }

    public boolean awaitExecution(long timeout, TimeUnit unit) throws InterruptedException {
      return executionLatch.await(timeout, unit);
    }
  }

  static class SynchronizedTestFailedJob extends TestFailedJob {
    private final CountDownLatch executionLatch;

    public SynchronizedTestFailedJob(int expectedExecutions) {
      this.executionLatch = new CountDownLatch(expectedExecutions);
    }

    @Override
    public void execute(ShardingContext shardingContext) {
      try {
        super.execute(shardingContext);
      } finally {
        executionLatch.countDown();
      }
    }

    public boolean awaitExecution(long timeout, TimeUnit unit) throws InterruptedException {
      return executionLatch.await(timeout, unit);
    }
  }
}
