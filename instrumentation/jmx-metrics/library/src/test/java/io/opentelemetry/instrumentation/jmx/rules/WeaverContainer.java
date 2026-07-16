/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.common.HttpData;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final Map<String, Integer> seenRegistryMetrics;
    private final Map<String, Integer> seenRegistryAttributes;

    private WeaverValidationResult(
        List<WeaverValidationAdvice> advices,
        Set<String> seenNonRegistryMetrics,
        Set<String> seenNonRegistryAttributes,
        Map<String, Integer> seenRegistryMetrics,
        Map<String, Integer> seenRegistryAttributes) {
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

      BiFunction<JsonNode, String, Map<String, Integer>> doParse =
          (jsonNode, key) -> {
            Map<String, Integer> result = new HashMap<>();
            JsonNode node = jsonNode.get(key);
            node.fieldNames()
                .forEachRemaining(
                    name -> {
                      JsonNode jsonCount = node.get(name);
                      result.put(name, jsonCount.asInt());
                    });
            return result;
          };

      JsonNode statistics = json.get("statistics");
      return new WeaverValidationResult(
          advices,
          doParse.apply(statistics, "seen_non_registry_metrics").keySet(),
          doParse.apply(statistics, "seen_non_registry_attributes").keySet(),
          doParse.apply(statistics, "seen_registry_metrics"),
          doParse.apply(statistics, "seen_registry_attributes"));
    }

    @CanIgnoreReturnValue
    public WeaverValidationResult checkNothingUnregisteredWithPrefix(String prefix) {
      seenNonRegistryMetrics.forEach(
          attribute ->
              assertThat(attribute)
                  .describedAs("no un-registered metric with prefix %s expected", prefix)
                  .doesNotStartWith(prefix));

      seenNonRegistryAttributes.forEach(
          attribute ->
              assertThat(attribute)
                  .describedAs("no un-registered attribute with prefix %s expected", prefix)
                  .doesNotStartWith(prefix));
      return this;
    }

    /**
     * Checks metrics that are registered
     *
     * @param prefix metric prefix
     * @param reportedMetrics metrics that should have been registered and reported
     * @param optionalReportedMetrics metrics that should have been registered but are optionally
     *     reported
     * @return this
     */
    @CanIgnoreReturnValue
    public WeaverValidationResult checkRegisteredMetrics(
        String prefix,
        Collection<String> reportedMetrics,
        Collection<String> optionalReportedMetrics) {
      return checkRegistered(
          "metrics", seenRegistryMetrics, prefix, reportedMetrics, optionalReportedMetrics);
    }

    private WeaverValidationResult checkRegistered(
        String type,
        Map<String, Integer> map,
        String prefix,
        Collection<String> reported,
        Collection<String> notReported) {

      Set<String> expectedKeys = new HashSet<>(reported);
      expectedKeys.addAll(notReported);
      assertThat(expectedKeys)
          .describedAs("overlap between reported and optionally reported %s", type)
          .hasSize(reported.size() + notReported.size());

      assertThat(map.keySet())
          .filteredOn(item -> item.startsWith(prefix))
          .describedAs("expected registered %s to contain all keys", type)
          .containsExactlyInAnyOrderElementsOf(expectedKeys);

      reported.forEach(
          key ->
              assertThat(map.get(key))
                  .describedAs("expected registered %s to contain key %s with count > 0", type, key)
                  .isGreaterThan(0));

      notReported.forEach(
          key ->
              assertThat(map.get(key))
                  .describedAs(
                      "expected registered %s to contain key %s with count >= 0", type, key)
                  .isGreaterThanOrEqualTo(0));

      return this;
    }

    /**
     * Checks attributes that are registered
     *
     * @param prefix attribute prefix
     * @param reportedAttributes attributes that should have been registered and reported
     * @param optionalReportedAttributes attributes that should have been registered but are
     *     optionally reported
     * @return this
     */
    @CanIgnoreReturnValue
    public WeaverValidationResult checkRegisteredAttributes(
        String prefix,
        Collection<String> reportedAttributes,
        Collection<String> optionalReportedAttributes) {
      return checkRegistered(
          "attributes",
          seenRegistryAttributes,
          prefix,
          reportedAttributes,
          optionalReportedAttributes);
    }

    @CanIgnoreReturnValue
    public WeaverValidationResult checkCommonViolations() {
      AtomicInteger violationCount = new AtomicInteger(0);
      advices.forEach(
          advice -> {
            if (shouldIgnoreAdvice(advice)) {
              return;
            }

            String signalOrResource = advice.getSignalName();
            if (signalOrResource == null) {
              signalOrResource = "resource attribute";
            }
            switch (advice.getLevel()) {
              case "information":
                logger.info(
                    "weaver reported information on {} : {}",
                    signalOrResource,
                    advice.getMessage());
                break;
              case "violation":
                logger.error(
                    "weaver reported violation on {} : {}", signalOrResource, advice.getMessage());
                violationCount.getAndIncrement();
                break;
              case "improvement":
                logger.warn(
                    "weaver reported improvement on {} : {}",
                    signalOrResource,
                    advice.getMessage());
                break;
              default:
                throw new IllegalStateException("unknown advice level " + advice.getLevel());
            }
          });
      assertThat(violationCount.get())
          .describedAs("no registry violation should be reported")
          .isEqualTo(0);
      return this;
    }

    private static boolean shouldIgnoreAdvice(WeaverContainer.WeaverValidationAdvice advice) {
      if (advice.getSignalName() == null) {
        return false;
      }
      // ignore old sdk metrics that are not part of semantic conventions
      switch (advice.getSignalName()) {
        case "otlp.exporter.seen":
        case "otlp.exporter.exported":
        case "otel.sdk.metric_reader.collection.duration":
          return true;
        default:
          return false;
      }
    }
  }

  private static class WeaverValidationAdvice {

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
