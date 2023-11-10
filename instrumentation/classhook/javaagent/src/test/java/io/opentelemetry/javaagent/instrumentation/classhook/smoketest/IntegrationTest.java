/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.classhook.smoketest;

import java.util.Collections;
import java.util.Map;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.MountableFile;

abstract class IntegrationTest {
  private static final Logger logger = LoggerFactory.getLogger(IntegrationTest.class);

  protected static OkHttpClient client = OkHttpUtils.client();

  private static final Network network = Network.newNetwork();
  protected static final String agentPath =
      System.getProperty("io.opentelemetry.smoketest.agentPath");

  protected static final String extensionPath =
      System.getProperty("io.opentelemetry.smoketest.extensionPath");

  protected abstract String getTargetImage(int jdk);

  /** Subclasses can override this method to customise target application's environment */
  protected Map<String, String> getExtraEnv() {
    return Collections.emptyMap();
  }

  @BeforeAll
  static void setupSpec() {}

  protected GenericContainer<?> target;

  void startTarget(String extensionLocation) {
    target = buildTargetContainer(agentPath, extensionLocation);
    target.start();
  }

  private GenericContainer<?> buildTargetContainer(String agentPath, String extensionLocation) {
    GenericContainer<?> result =
        new GenericContainer<>(getTargetImage(8))
            .withExposedPorts(8080)
            .withNetwork(network)
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .withCopyFileToContainer(
                MountableFile.forHostPath(agentPath), "/opentelemetry-javaagent.jar")
            // Adds instrumentation agent with debug configuration to the target application
            .withEnv(
                "JAVA_TOOL_OPTIONS",
                "-javaagent:/opentelemetry-javaagent.jar -Dotel.javaagent.debug=true")
            .withEnv("OTEL_BSP_MAX_EXPORT_BATCH", "1")
            .withEnv("OTEL_BSP_SCHEDULE_DELAY", "10")
            .withEnv("OTEL_PROPAGATORS", "tracecontext,baggage,demo")
            .withEnv(getExtraEnv());
    // If external extensions are requested
    if (extensionLocation != null) {
      // Asks instrumentation agent to include extensions from given location into its runtime
      result =
          result
              .withCopyFileToContainer(
                  MountableFile.forHostPath(extensionPath), "/opentelemetry-extensions.jar")
              .withEnv("OTEL_JAVAAGENT_EXTENSIONS", extensionLocation);
    }
    return result;
  }

  @AfterEach
  void cleanup() {}

  void stopTarget() {
    target.stop();
  }

  @AfterAll
  static void cleanupSpec() {}
}
