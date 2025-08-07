/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attribute;

import io.opentelemetry.instrumentation.jmx.rules.assertions.AttributeMatcher;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

class HadoopTest extends TargetSystemTest {
  private static final int HADOOP_PORT = 50070;

  @ParameterizedTest
  @ValueSource(
      strings = {
//          "jack20191124/hadoop:2.9.0",
        "bmedora/hadoop:2.9-base",
//        "bmedora/hadoop:3.3-base"
      })
  void testMetrics(String image) {
    List<String> yamlFiles = Collections.singletonList("hadoop.yaml");

    yamlFiles.forEach(this::validateYamlSyntax);

//    List<String> jvmArgs = new ArrayList<>();
//    jvmArgs.add(javaAgentJvmArgument());
//    jvmArgs.addAll(javaPropertiesToJvmArgs(otelConfigProperties(yamlFiles)));

    // bmedora docker images do not propagate env vars to spanned hadoop processes,
    // so everything needs to be embedded inside the hadoop-env.sh file
    GenericContainer<?> target =
        new GenericContainer<>(image)
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("hadoop-env.sh", 0400),
                "/hadoop/etc/hadoop/hadoop-env.sh")
//            .withEnv("JAVA_TOOL_OPTIONS", String.join(" ", jvmArgs))
//            .withEnv("HADOOP_OPTS", String.join(" ", jvmArgs))
            .withEnv("OTLP_ENDPOINT", otlpEndpoint)
            .withCreateContainerCmdModifier(cmd -> cmd.withHostName("test-host"))
            .withStartupTimeout(Duration.ofMinutes(3))
            .withExposedPorts(HADOOP_PORT)
//                .waitingFor(Wait.forLogMessage(".*started.*", 1));
            .waitingFor(Wait.forListeningPorts(HADOOP_PORT));

    copyAgentToTarget(target);
    copyYamlFilesToTarget(target, yamlFiles);

    startTarget(target);

    AttributeMatcher nodeNameAttribute = attribute("hadoop.node.name", "test-host");

    verifyMetrics(
      MetricsVerifier.create()
          .add(
              "hadoop.capacity.usage",
              metric ->
                  metric
                      .hasDescription(
                          "Current used capacity across all data nodes.")
                      .hasUnit("By")
                      .isUpDownCounter()
                      .hasDataPointsWithOneAttribute(nodeNameAttribute))
    );



//    AttributeMatcher poolNameAttribute = attributeWithAnyValue("jvm.memory.pool.name");
//
//    AttributeMatcherGroup heapPoolAttributes =
//        attributeGroup(attribute("jvm.memory.type", "heap"), poolNameAttribute);
//
//    AttributeMatcherGroup nonHeapPoolAttributes =
//        attributeGroup(attribute("jvm.memory.type", "non_heap"), poolNameAttribute);
//
//    AttributeMatcher bufferPoolName = attributeWithAnyValue("jvm.buffer.pool.name");
//    verifyMetrics(
//        MetricsVerifier.create()
//            .add(
//                "jvm.memory.used",
//                metric ->
//                    metric
//                        .hasDescription("Measure of memory used.")
//                        .hasUnit("By")
//                        .isUpDownCounter()
//                        .hasDataPointsWithAttributes(heapPoolAttributes, nonHeapPoolAttributes))
//            .add(
//                "jvm.memory.committed",
//                metric ->
//                    metric
//                        .hasDescription("Measure of memory committed.")
//                        .hasUnit("By")
//                        .isUpDownCounter()
//                        .hasDataPointsWithAttributes(heapPoolAttributes, nonHeapPoolAttributes))
//            .add(
//                "jvm.memory.limit",
//                metric ->
//                    metric
//                        .hasDescription("Measure of max obtainable memory.")
//                        .hasUnit("By")
//                        .isUpDownCounter()
//                        .hasDataPointsWithAttributes(heapPoolAttributes, nonHeapPoolAttributes))
//            .add(
//                "jvm.memory.init",
//                metric ->
//                    metric
//                        .hasDescription("Measure of initial memory requested.")
//                        .hasUnit("By")
//                        .isUpDownCounter()
//                        .hasDataPointsWithAttributes(heapPoolAttributes, nonHeapPoolAttributes))
//            .add(
//                "jvm.memory.used_after_last_gc",
//                metric ->
//                    metric
//                        .hasDescription(
//                            "Measure of memory used, as measured after the most recent garbage collection event on this pool.")
//                        .hasUnit("By")
//                        .isUpDownCounter()
//                        // note: there is no GC for non-heap memory
//                        .hasDataPointsWithAttributes(heapPoolAttributes))
//            .add(
//                "jvm.thread.count",
//                metric ->
//                    metric
//                        .hasDescription("Number of executing platform threads.")
//                        .hasUnit("{thread}")
//                        .isUpDownCounter()
//                        .hasDataPointsWithoutAttributes())
//            .add(
//                "jvm.class.loaded",
//                metric ->
//                    metric
//                        .hasDescription("Number of classes loaded since JVM start.")
//                        .hasUnit("{class}")
//                        .isUpDownCounter()
//                        .hasDataPointsWithoutAttributes())
//            .add(
//                "jvm.class.unloaded",
//                metric ->
//                    metric
//                        .hasDescription("Number of classes unloaded since JVM start.")
//                        .hasUnit("{class}")
//                        .isUpDownCounter()
//                        .hasDataPointsWithoutAttributes())
//            .add(
//                "jvm.class.count",
//                metric ->
//                    metric
//                        .hasDescription("Number of classes currently loaded.")
//                        .hasUnit("{class}")
//                        .isUpDownCounter()
//                        .hasDataPointsWithoutAttributes())
//            .add(
//                "jvm.cpu.count",
//                metric ->
//                    metric
//                        .hasDescription(
//                            "Number of processors available to the Java virtual machine.")
//                        .hasUnit("{cpu}")
//                        .isUpDownCounter()
//                        .hasDataPointsWithoutAttributes())
//            .add(
//                "jvm.cpu.time",
//                metric ->
//                    metric
//                        .hasDescription("CPU time used by the process as reported by the JVM.")
//                        .hasUnit("s")
//                        .isCounter()
//                        .hasDataPointsWithoutAttributes())
//            .add(
//                "jvm.cpu.recent_utilization",
//                metric ->
//                    metric
//                        .hasDescription(
//                            "Recent CPU utilization for the process as reported by the JVM.")
//                        .hasUnit("1")
//                        .isGauge()
//                        .hasDataPointsWithoutAttributes())
//            .add(
//                "jvm.file_descriptor.count",
//                metric ->
//                    metric
//                        .hasDescription("Number of open file descriptors as reported by the JVM.")
//                        .hasUnit("{file_descriptor}")
//                        .isUpDownCounter()
//                        .hasDataPointsWithoutAttributes())
//            .add(
//                "jvm.system.cpu.load_1m",
//                metric ->
//                    metric
//                        .hasDescription(
//                            "Average CPU load of the whole system for the last minute as reported by the JVM.")
//                        .hasUnit("{run_queue_item}")
//                        .isGauge()
//                        .hasDataPointsWithoutAttributes())
//            .add(
//                "jvm.system.cpu.utilization",
//                metric ->
//                    metric
//                        .hasDescription(
//                            "Recent CPU utilization for the whole system as reported by the JVM.")
//                        .hasUnit("1")
//                        .isGauge()
//                        .hasDataPointsWithoutAttributes())
//            .add(
//                "jvm.buffer.memory.used",
//                metric ->
//                    metric
//                        .hasDescription("Measure of memory used by buffers.")
//                        .hasUnit("By")
//                        .isUpDownCounter()
//                        .hasDataPointsWithOneAttribute(bufferPoolName))
//            .add(
//                "jvm.buffer.memory.limit",
//                metric ->
//                    metric
//                        .hasDescription("Measure of total memory capacity of buffers.")
//                        .hasUnit("By")
//                        .isUpDownCounter()
//                        .hasDataPointsWithOneAttribute(bufferPoolName))
//            .add(
//                "jvm.buffer.count",
//                metric ->
//                    metric
//                        .hasDescription("Number of buffers in the pool.")
//                        .hasUnit("{buffer}")
//                        .isUpDownCounter()
//                        .hasDataPointsWithOneAttribute(bufferPoolName)));
  }
}
