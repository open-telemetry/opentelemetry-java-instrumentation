/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attribute;
import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeGroup;
import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeWithAnyValue;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.jmx.rules.assertions.AttributeMatcherGroup;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

class CassandraTest extends TargetSystemTest {

  private static final int CASSANDRA_PORT = 9042;

  // 4 batches x 2500 rows x 2 KB = ~20 MB; at 1 MB/s throttle compaction stays visible for ~20s,
  // well within the 60s await in verifyMetrics().
  private static final int COMPACTION_BATCHES = 4;
  private static final int COMPACTION_ROWS_PER_BATCH = 2_500;
  private static final int COMPACTION_VALUE_BYTES = 2_048;

  private static final Logger logger = LoggerFactory.getLogger(CassandraTest.class);

  @Test
  void testCassandraMetrics() {
    Collection<String> yamlFiles = getAllRuleFilesForSystem("cassandra");

    List<String> jvmArgs = new ArrayList<>();
    jvmArgs.add(javaAgentJvmArgument());
    jvmArgs.addAll(javaPropertiesToJvmArgs(otelConfigProperties(yamlFiles)));

    GenericContainer<?> target =
        new GenericContainer<>("cassandra:5.0.2")
            .withEnv("JAVA_TOOL_OPTIONS", String.join(" ", jvmArgs))
            // speed up single-node startup from ~1min to ~15s
            .withEnv(
                "JVM_EXTRA_OPTS",
                "-Dcassandra.skip_wait_for_gossip_to_settle=0 -Dcassandra.initial_token=0")
            .withStartupTimeout(Duration.ofMinutes(2))
            .withExposedPorts(CASSANDRA_PORT)
            .waitingFor(Wait.forListeningPorts(CASSANDRA_PORT));

    copyAgentToTarget(target);
    copyYamlFilesToTarget(target, yamlFiles);

    startWeaverValidation(
        "cassandra.yaml",
        result ->
            result
                .checkNothingUnregisteredWithPrefix("cassandra.")
                .checkRegisteredMetrics(
                    "cassandra.",
                    asList(
                        "cassandra.compaction.tasks.completed",
                        "cassandra.compaction.tasks.pending",
                        "cassandra.storage.load",
                        "cassandra.storage.hints.count",
                        "cassandra.storage.hints.in_progress",
                        "cassandra.client.request.latency.p50",
                        "cassandra.client.request.latency.p99",
                        "cassandra.client.request.latency.max",
                        "cassandra.client.request.count",
                        "cassandra.client.request.error",
                        "cassandra.compaction.progress.completed",
                        "cassandra.compaction.progress.size"),
                    emptyList())
                .checkRegisteredAttributes(
                    "cassandra.",
                    asList(
                        "cassandra.operation",
                        "cassandra.status",
                        "cassandra.keyspace",
                        "cassandra.table",
                        "cassandra.compaction.task_type"),
                    emptyList()));

    startTarget(target);

    seedCompactionData(target);
    AtomicBoolean keepCompacting = new AtomicBoolean(true);
    triggerCompaction(target, keepCompacting);
    try {
      verifyMetrics(createMetricsVerifier());
    } finally {
      keepCompacting.set(false);
    }
  }

  private static void seedCompactionData(GenericContainer<?> target) {
    try {
      // Throttle compaction to 1 MB/s so it stays active long enough to be observed.
      nodetool(target, "setcompactionthroughput", "1");

      execOrThrow(
          target,
          "cqlsh",
          "-e",
          "CREATE KEYSPACE IF NOT EXISTS test"
              + " WITH replication = {'class':'SimpleStrategy','replication_factor':1};"
              + "CREATE TABLE IF NOT EXISTS test.data (id uuid PRIMARY KEY, val text)"
              + " WITH compression = {'enabled':'false'};");
      nodetool(target, "disableautocompaction", "test", "data");

      for (int i = 0; i < COMPACTION_BATCHES; i++) {
        seedCompactionBatch(target);
        nodetool(target, "flush", "test", "data");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Failed to seed Cassandra data", e);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to seed Cassandra data", e);
    }
  }

  private static void seedCompactionBatch(GenericContainer<?> target)
      throws IOException, InterruptedException {
    execOrThrow(
        target,
        "bash",
        "-c",
        // value is generated once and reused for all rows - only byte volume matters here.
        "set -euo pipefail; "
            + "value=$(head -c "
            + COMPACTION_VALUE_BYTES
            + " /dev/zero | tr '\\0' x); "
            + "rm -f /tmp/cassandra-data.csv; "
            + "for i in $(seq 1 "
            + COMPACTION_ROWS_PER_BATCH
            + "); do "
            + "printf \"%s,%s\\n\" \"$(cat /proc/sys/kernel/random/uuid)\" \"$value\"; "
            + "done > /tmp/cassandra-data.csv; "
            + "cqlsh -e \"COPY test.data (id, val) FROM '/tmp/cassandra-data.csv' "
            + "WITH HEADER = false AND MINBATCHSIZE = 1 AND MAXBATCHSIZE = 2;\"");
  }

  private static void triggerCompaction(GenericContainer<?> target, AtomicBoolean keepCompacting) {
    Thread compactionThread =
        new Thread(
            () -> {
              while (keepCompacting.get()) {
                try {
                  nodetool(target, "compact", "--", "test", "data");
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  return;
                } catch (Exception e) {
                  if (!target.isRunning()) {
                    return;
                  }
                  logger.warn("Background compaction failed", e);
                  return;
                }
              }
            },
            "cassandra-compaction-trigger");
    compactionThread.setDaemon(true);
    compactionThread.start();
  }

  private static void nodetool(GenericContainer<?> target, String... args)
      throws IOException, InterruptedException {
    String[] command = new String[args.length + 1];
    command[0] = "nodetool";
    System.arraycopy(args, 0, command, 1, args.length);
    execOrThrow(target, command);
  }

  private static void execOrThrow(GenericContainer<?> target, String... command)
      throws IOException, InterruptedException {
    Container.ExecResult result = target.execInContainer(command);
    if (result.getExitCode() != 0) {
      throw new IllegalStateException(
          String.join(" ", command)
              + " failed with exit code "
              + result.getExitCode()
              + "\nstdout:\n"
              + result.getStdout()
              + "\nstderr:\n"
              + result.getStderr());
    }
  }

  private static MetricsVerifier createMetricsVerifier() {
    return MetricsVerifier.create()
        .add(
            "cassandra.compaction.tasks.completed",
            metric ->
                metric
                    .hasDescription("Number of completed compactions since server start.")
                    .hasUnit("{task}")
                    .isCounter()
                    .hasDataPointsWithoutAttributes())
        .add(
            "cassandra.compaction.tasks.pending",
            metric ->
                metric
                    .hasDescription("Estimated number of compactions remaining to perform.")
                    .hasUnit("{task}")
                    .isGauge()
                    .hasDataPointsWithoutAttributes())
        .add(
            "cassandra.storage.load",
            metric ->
                metric
                    .hasDescription("Size of the on disk data size this node manages.")
                    .hasUnit("By")
                    .isUpDownCounter()
                    .hasDataPointsWithoutAttributes())
        .add(
            "cassandra.storage.hints.count",
            metric ->
                metric
                    .hasDescription(
                        "Number of hint messages written to this node since server start.")
                    .hasUnit("{hint}")
                    .isCounter()
                    .hasDataPointsWithoutAttributes())
        .add(
            "cassandra.storage.hints.in_progress",
            metric ->
                metric
                    .hasDescription("Number of hints attempting to be sent currently.")
                    .hasUnit("{hint}")
                    .isUpDownCounter()
                    .hasDataPointsWithoutAttributes())
        .add(
            "cassandra.client.request.latency.p50",
            metric ->
                metric
                    .hasDescription("Request latency 50th percentile by operation.")
                    .hasUnit("s")
                    .isGauge()
                    .hasDataPointsWithAttributes(
                        attributeGroup(attribute("cassandra.operation", "read")),
                        attributeGroup(attribute("cassandra.operation", "write")),
                        attributeGroup(attribute("cassandra.operation", "rangeslice"))))
        .add(
            "cassandra.client.request.latency.p99",
            metric ->
                metric
                    .hasDescription("Request latency 99th percentile by operation.")
                    .hasUnit("s")
                    .isGauge()
                    .hasDataPointsWithAttributes(
                        attributeGroup(attribute("cassandra.operation", "read")),
                        attributeGroup(attribute("cassandra.operation", "write")),
                        attributeGroup(attribute("cassandra.operation", "rangeslice"))))
        .add(
            "cassandra.client.request.latency.max",
            metric ->
                metric
                    .hasDescription("Maximum request latency by operation.")
                    .hasUnit("s")
                    .isGauge()
                    .hasDataPointsWithAttributes(
                        attributeGroup(attribute("cassandra.operation", "read")),
                        attributeGroup(attribute("cassandra.operation", "write")),
                        attributeGroup(attribute("cassandra.operation", "rangeslice"))))
        .add(
            "cassandra.client.request.count",
            metric ->
                metric
                    .hasDescription("Number of requests by operation.")
                    .hasUnit("{request}")
                    .isCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(attribute("cassandra.operation", "rangeslice")),
                        attributeGroup(attribute("cassandra.operation", "read")),
                        attributeGroup(attribute("cassandra.operation", "write"))))
        .add(
            "cassandra.client.request.error",
            metric ->
                metric
                    .hasDescription("Number of request errors by operation.")
                    .hasUnit("{error}")
                    .isCounter()
                    .hasDataPointsWithAttributes(
                        errorAttributesGroup("rangeslice", "timeout"),
                        errorAttributesGroup("rangeslice", "failure"),
                        errorAttributesGroup("rangeslice", "unavailable"),
                        errorAttributesGroup("read", "timeout"),
                        errorAttributesGroup("read", "failure"),
                        errorAttributesGroup("read", "unavailable"),
                        errorAttributesGroup("write", "timeout"),
                        errorAttributesGroup("write", "failure"),
                        errorAttributesGroup("write", "unavailable")))
        .add(
            "cassandra.compaction.progress.completed",
            metric ->
                metric
                    .hasDescription("Bytes completed for in-flight compactions.")
                    .hasUnit("By")
                    .isGauge()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("cassandra.compaction.task_type"),
                            attribute("cassandra.keyspace", "test"),
                            attribute("cassandra.table", "data"))))
        .add(
            "cassandra.compaction.progress.size",
            metric ->
                metric
                    .hasDescription("Total bytes for in-flight compactions.")
                    .hasUnit("By")
                    .isGauge()
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("cassandra.compaction.task_type"),
                            attribute("cassandra.keyspace", "test"),
                            attribute("cassandra.table", "data"))));
  }

  private static AttributeMatcherGroup errorAttributesGroup(String operation, String status) {
    return attributeGroup(
        attribute("cassandra.operation", operation), attribute("cassandra.status", status));
  }
}
