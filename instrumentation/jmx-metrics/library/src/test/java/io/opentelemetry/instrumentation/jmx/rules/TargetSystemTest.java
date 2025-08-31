/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.grpc.GrpcService;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.instrumentation.jmx.yaml.JmxConfig;
import io.opentelemetry.instrumentation.jmx.yaml.JmxRule;
import io.opentelemetry.instrumentation.jmx.yaml.RuleParser;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.MountableFile;

/** Base class for testing YAML metric definitions with a real target system */
public class TargetSystemTest {

  private static final Logger logger = LoggerFactory.getLogger(TargetSystemTest.class);
  private static final Logger targetSystemLogger = LoggerFactory.getLogger("targetSystem");

  private static final String AGENT_PATH = "/opentelemetry-instrumentation-javaagent.jar";
  protected static final String APP_PATH = "/testapp.war";

  private static final Network network = Network.newNetwork();

  private static OtlpGrpcServer otlpServer;
  private static Path agentPath;
  private static Path testAppPath;
  private static String otlpEndpoint;

  private GenericContainer<?> targetSystem;
  private Collection<GenericContainer<?>> targetDependencies;

  @BeforeAll
  static void beforeAll() {
    otlpServer = new OtlpGrpcServer();
    otlpServer.start();
    Testcontainers.exposeHostPorts(otlpServer.httpPort());
    otlpEndpoint = "http://host.testcontainers.internal:" + otlpServer.httpPort();

    TargetSystemTest.agentPath = getArtifactPath("io.opentelemetry.javaagent.path");
    TargetSystemTest.testAppPath = getArtifactPath("io.opentelemetry.testapp.path");
  }

  private static Path getArtifactPath(String systemProperty) {
    String pathValue = System.getProperty(systemProperty);
    assertThat(pathValue).isNotNull();
    Path path = Paths.get(pathValue);
    assertThat(path).isReadable().isNotEmptyFile();
    return path;
  }

  @BeforeEach
  void beforeEach() {
    otlpServer.reset();
  }

  @AfterEach
  void afterEach() {
    stop(targetSystem);
    targetSystem = null;

    if (targetDependencies != null) {
      for (GenericContainer<?> targetDependency : targetDependencies) {
        stop(targetDependency);
      }
    }
    targetDependencies = Collections.emptyList();
  }

  private static void stop(@Nullable GenericContainer<?> container) {
    if (container != null && container.isRunning()) {
      container.stop();
    }
  }

  @AfterAll
  static void afterAll() {
    try {
      otlpServer.stop().get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  protected static String javaAgentJvmArgument() {
    return "-javaagent:" + AGENT_PATH;
  }

  protected static List<String> javaPropertiesToJvmArgs(Map<String, String> config) {
    return config.entrySet().stream()
        .map(e -> String.format("-D%s=%s", e.getKey(), e.getValue()))
        .collect(Collectors.toList());
  }

  /**
   * Generates otel configuration for JMX testing with instrumentation agent
   *
   * @param yamlFiles JMX metrics definitions in YAML
   * @return map of otel configuration properties for JMX testing
   */
  protected static Map<String, String> otelConfigProperties(List<String> yamlFiles) {
    Map<String, String> config = new HashMap<>();
    // only export metrics
    config.put("otel.logs.exporter", "none");
    config.put("otel.traces.exporter", "none");
    config.put("otel.metrics.exporter", "otlp");
    // use test grpc endpoint
    config.put("otel.exporter.otlp.endpoint", otlpEndpoint);
    config.put("otel.exporter.otlp.protocol", "grpc");
    // short export interval for testing
    config.put("otel.metric.export.interval", "5s");
    // disable runtime telemetry metrics
    config.put("otel.instrumentation.runtime-telemetry.enabled", "false");
    // set yaml config files to test
    config.put("otel.jmx.target", "tomcat");
    config.put(
        "otel.jmx.config",
        yamlFiles.stream()
            .map(TargetSystemTest::containerYamlPath)
            .collect(Collectors.joining(",")));
    return config;
  }

  /**
   * Starts the target system
   *
   * @param target target system to start
   */
  protected void startTarget(GenericContainer<?> target) {
    startTarget(target, Collections.emptyList());
  }

  /**
   * Starts the target system with its dependencies first
   *
   * @param target target system
   * @param targetDependencies dependencies of target system
   */
  protected void startTarget(
      GenericContainer<?> target, Collection<GenericContainer<?>> targetDependencies) {

    // If there are any containers that must be started before target then initialize them.
    // Then make target depending on them, so it is started after dependencies
    this.targetDependencies = targetDependencies;
    for (GenericContainer<?> container : targetDependencies) {
      container.withNetwork(network);
      target.dependsOn(container);
    }

    targetSystem =
        target.withLogConsumer(new Slf4jLogConsumer(targetSystemLogger)).withNetwork(network);
    targetSystem.start();
  }

  protected static void copyAgentToTarget(GenericContainer<?> target) {
    logger.info("copying java agent {} to container {}", agentPath, AGENT_PATH);
    target.withCopyFileToContainer(MountableFile.forHostPath(agentPath), AGENT_PATH);
  }

  protected static void copyYamlFilesToTarget(GenericContainer<?> target, List<String> yamlFiles) {
    for (String file : yamlFiles) {
      String resourcePath = yamlResourcePath(file);
      String destPath = containerYamlPath(file);
      logger.info("copying yaml from resources {} to container {}", resourcePath, destPath);
      target.withCopyFileToContainer(MountableFile.forClasspathResource(resourcePath), destPath);
    }
  }

  protected static void copyTestWebAppToTarget(GenericContainer<?> target, String targetPath) {
    logger.info("copying test application {} to container {}", testAppPath, targetPath);
    target.withCopyFileToContainer(MountableFile.forHostPath(testAppPath), targetPath);
  }

  private static String yamlResourcePath(String yaml) {
    return "jmx/rules/" + yaml;
  }

  private static String containerYamlPath(String yaml) {
    return "/" + yaml;
  }

  /**
   * Validates YAML definition by parsing it to check for syntax errors
   *
   * @param yaml path to YAML resource (in classpath)
   */
  protected void validateYamlSyntax(String yaml) {
    String path = yamlResourcePath(yaml);
    try (InputStream input = TargetSystemTest.class.getClassLoader().getResourceAsStream(path)) {
      JmxConfig config;
      // try-catch to provide a slightly better error
      try {
        config = RuleParser.get().loadConfig(input);
      } catch (RuntimeException e) {
        fail("Failed to parse yaml file " + path, e);
        throw e;
      }

      // make sure all the rules in that file are valid
      for (JmxRule rule : config.getRules()) {
        try {
          rule.buildMetricDef();
        } catch (Exception e) {
          fail("Failed to build metric definition " + rule.getBeans(), e);
        }
      }
    } catch (IOException e) {
      fail("Failed to read yaml file " + path, e);
    }
  }

  protected void verifyMetrics(MetricsVerifier metricsVerifier) {
    await()
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              List<ExportMetricsServiceRequest> receivedMetrics = otlpServer.getMetrics();
              assertThat(receivedMetrics).describedAs("No metric received").isNotEmpty();

              List<Metric> metrics =
                  receivedMetrics.stream()
                      .map(ExportMetricsServiceRequest::getResourceMetricsList)
                      .flatMap(rm -> rm.stream().map(ResourceMetrics::getScopeMetricsList))
                      .flatMap(Collection::stream)
                      .filter(
                          // TODO: disabling batch span exporter might help remove unwanted metrics
                          sm -> sm.getScope().getName().equals("io.opentelemetry.jmx"))
                      .flatMap(sm -> sm.getMetricsList().stream())
                      .collect(Collectors.toList());

              assertThat(metrics).describedAs("Metrics received but not from JMX").isNotEmpty();

              metricsVerifier.verify(metrics);
            });
  }

  /** Minimal OTLP gRPC backend to capture metrics */
  private static class OtlpGrpcServer extends ServerExtension {

    private final BlockingQueue<ExportMetricsServiceRequest> metricRequests =
        new LinkedBlockingDeque<>();

    List<ExportMetricsServiceRequest> getMetrics() {
      return new ArrayList<>(metricRequests);
    }

    void reset() {
      metricRequests.clear();
    }

    @Override
    protected void configure(ServerBuilder sb) {
      sb.service(
          GrpcService.builder()
              .addService(
                  new MetricsServiceGrpc.MetricsServiceImplBase() {
                    @Override
                    public void export(
                        ExportMetricsServiceRequest request,
                        StreamObserver<ExportMetricsServiceResponse> responseObserver) {

                      // verbose but helpful to diagnose what is received
                      logger.debug("receiving metrics {}", request);

                      metricRequests.add(request);
                      responseObserver.onNext(ExportMetricsServiceResponse.getDefaultInstance());
                      responseObserver.onCompleted();
                    }
                  })
              .build());
      sb.http(0);
    }
  }
}
