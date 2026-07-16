/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.common.HttpData;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

class WeaverContainer extends GenericContainer<WeaverContainer> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final Logger logger = LoggerFactory.getLogger(WeaverContainer.class);
  private static final int ADMIN_PORT = 4320;
  private static final int OTLP_PORT = 4317;

  @Nullable private JsonNode result = null;

  WeaverContainer(Path registryRoot, String... registryFiles) {
    super("otel/weaver:v0.24.2");

    super.withExposedPorts(OTLP_PORT, ADMIN_PORT);
    super.waitingFor(Wait.forListeningPorts(OTLP_PORT, ADMIN_PORT));
    super.withCommand(
        "registry",
        "live-check",
        "--registry",
        "/registry",
        "--inactivity-timeout=0",
        "--output=http",
        "--format",
        "json");
    super.withLogConsumer(new Slf4jLogConsumer(logger));

    // main registry definition
    copyRegistryFile(registryRoot, "manifest.yaml");
    // per-system registry definitions
    for (String registryFile : registryFiles) {
      copyRegistryFile(registryRoot, registryFile);
    }
    // Common definitions used when testing to inherit existing definitions.
    // Adding this allows to lower the noise level of validation errors for things that are not
    // directly part of the registry under test.
    super.withCopyFileToContainer(
        MountableFile.forClasspathResource("jmx/registry/test-common.yaml"),
        "/registry/test-common.yaml");
  }

  private void copyRegistryFile(Path root, String fileName) {
    String containerPath = "/registry/" + fileName;
    super.withCopyFileToContainer(MountableFile.forHostPath(root.resolve(fileName)), containerPath);
  }

  @Override
  public void stop() {
    if (!this.isRunning()) {
      throw new IllegalStateException("weaver container already stopped");
    }
    String uri = "http://" + this.getHost() + ":" + this.getMappedPort(ADMIN_PORT) + "/";
    WebClient client = WebClient.of(uri);
    try (HttpData result = client.post("/stop", new byte[0]).aggregate().join().content()) {
      this.result = OBJECT_MAPPER.readTree(result.toInputStream());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } finally {
      super.stop();
    }
  }

  public WeaverValidationResult getResult() {
    if (result == null) {
      throw new IllegalStateException("must stop container to get result first");
    }
    return WeaverValidationResult.fromJson(result);
  }

  public String getOtlpEndpoint() {
    return this.getHost() + ":" + this.getMappedPort(OTLP_PORT);
  }

  public static class WeaverValidationResult {
    private final List<WeaverValidationAdvice> advices;
    private final Set<String> seenNonRegistryMetrics;
    private final Set<String> seenNonRegistryAttributes;
    private final Set<String> seenRegistryMetrics;
    private final Set<String> seenRegistryAttributes;

    private WeaverValidationResult(
        List<WeaverValidationAdvice> advices,
        Set<String> seenNonRegistryMetrics,
        Set<String> seenNonRegistryAttributes,
        Set<String> seenRegistryMetrics,
        Set<String> seenRegistryAttributes) {
      this.advices = advices;
      this.seenNonRegistryMetrics = seenNonRegistryMetrics;
      this.seenNonRegistryAttributes = seenNonRegistryAttributes;
      this.seenRegistryMetrics = seenRegistryMetrics;
      this.seenRegistryAttributes = seenRegistryAttributes;
    }

    private static WeaverValidationResult fromJson(JsonNode json) {
      List<WeaverValidationAdvice> advices = new ArrayList<>();

      Consumer<JsonNode> parseValidationAdvice =
          jsonNode -> {
            JsonNode allAdvice = jsonNode.get("live_check_result").get("all_advice");
            if (allAdvice.isArray() && !allAdvice.isEmpty()) {
              allAdvice.forEach(
                  advice -> {
                    advices.add(
                        new WeaverValidationAdvice(
                            advice.get("signal_name").asText(null),
                            advice.get("message").asText(),
                            advice.get("level").asText()));
                  });
            }
          };

      json.get("samples")
          .forEach(
              sample -> {
                JsonNode resource = sample.get("resource");
                JsonNode metric = sample.get("metric");
                if (resource != null) {
                  resource.get("attributes").forEach(parseValidationAdvice);
                } else if (metric != null) {
                  parseValidationAdvice.accept(metric);
                } else {
                  throw new IllegalStateException("unexpected weaver validation result type");
                }
              });

      BiFunction<JsonNode, String, Set<String>> doParse =
          (jsonNode, key) -> {
            Set<String> result = new HashSet<>();
            JsonNode node = jsonNode.get(key);
            node.fieldNames()
                .forEachRemaining(
                    name -> {
                      JsonNode jsonCount = node.get(name);
                      // only include items that have been reported at least once
                      if (jsonCount.isInt() && jsonCount.asInt() > 0) {
                        result.add(name);
                      }
                    });
            return result;
          };

      JsonNode statistics = json.get("statistics");
      return new WeaverValidationResult(
          advices,
          doParse.apply(statistics, "seen_non_registry_metrics"),
          doParse.apply(statistics, "seen_non_registry_attributes"),
          doParse.apply(statistics, "seen_registry_metrics"),
          doParse.apply(statistics, "seen_registry_attributes"));
    }

    /**
     * Get list of all validation messages
     *
     * @return list of validation messages
     */
    public List<WeaverValidationAdvice> getValidationAdvices() {
      return advices;
    }

    /**
     * Get reported metrics that are not defined in registry
     *
     * @return set of metrics that were reported but are not part of tested registry
     */
    public Set<String> getSeenNonRegistryMetrics() {
      return seenNonRegistryMetrics;
    }

    /**
     * Get reported attributes that are not defined in registry
     *
     * @return set of attributes that were reported but are not part of tested registry
     */
    public Set<String> getSeenNonRegistryAttributes() {
      return seenNonRegistryAttributes;
    }

    /**
     * Get reported metrics that are defined in registry
     *
     * @return set of metrics that were reported and are part of tested registry
     */
    public Set<String> getSeenRegistryMetrics() {
      return seenRegistryMetrics;
    }

    /**
     * Get reported attributes that are defined in registry
     *
     * @return set of attributes that were reported and are part of tested registry
     */
    public Set<String> getSeenRegistryAttributes() {
      return seenRegistryAttributes;
    }
  }

  public static class WeaverValidationAdvice {

    @Nullable private final String signalName;
    private final String message;
    private final String level;

    WeaverValidationAdvice(@Nullable String signalName, String message, String level) {
      this.signalName = signalName;
      this.message = message;
      this.level = level;
    }

    @Nullable
    public String getSignalName() {
      return signalName;
    }

    public String getMessage() {
      return message;
    }

    public String getLevel() {
      return level;
    }
  }
}
