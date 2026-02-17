/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeGroup;
import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeWithAnyValue;

import io.opentelemetry.instrumentation.jmx.rules.assertions.AttributeMatcherGroup;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

class CamelTest extends TargetSystemTest {

  @Test
  void testCollectedMetrics() {
    List<String> yamlFiles = Collections.singletonList("camel.yaml");

    yamlFiles.forEach(this::validateYamlSyntax);

    List<String> jvmArgs = new ArrayList<>();
    jvmArgs.add(javaAgentJvmArgument());
    jvmArgs.addAll(javaPropertiesToJvmArgs(otelConfigProperties(yamlFiles)));

    GenericContainer<?> target =
        new GenericContainer<>("eclipse-temurin:17.0.18_8-jre")
            .withCommand(String.format("java %s -jar /camel_test.jar", String.join(" ", jvmArgs)))
            .withExposedPorts(8080) // This port is used only to detect container start
            .waitingFor(Wait.forListeningPort());

    copyAgentToTarget(target);
    copyYamlFilesToTarget(target, yamlFiles);

    copyTestAppToTarget(
        getArtifactPath("io.opentelemetry.cameltestapp.path"), target, "/camel_test.jar");

    startTarget(target);

    verifyMetrics(createMetricsVerifier());
  }

  private static MetricsVerifier createMetricsVerifier() {
    AttributeMatcherGroup contextAttributes =
        attributeGroup(attributeWithAnyValue("camel.context"));

    AttributeMatcherGroup routeAttributes =
        attributeGroup(
            attributeWithAnyValue("camel.context"), attributeWithAnyValue("camel.route"));

    AttributeMatcherGroup processorAttributes =
        attributeGroup(
            attributeWithAnyValue("camel.context"),
            attributeWithAnyValue("camel.route"),
            attributeWithAnyValue("camel.processor"));

    // camel.destination attribute is present only in destination-aware processors MBeans
    // (processors that send to an endpoint, like: to, toD, wireTap, enrich, pollEnrich, poll,
    // dynamicRouter), and from Camel 4.16.0+.
    AttributeMatcherGroup destinationAwareProcessorAttributes =
        attributeGroup(
                attributeWithAnyValue("camel.context"),
                attributeWithAnyValue("camel.route"),
                attributeWithAnyValue("camel.processor"),
                attributeWithAnyValue("camel.destination"))
            .applicableWhen(
                (attributes) ->
                    Optional.of(attributes.get("camel.processor"))
                        // Test application uses only "to" destination-aware processor
                        .filter((processorId) -> processorId.startsWith("to"))
                        .isPresent());

    AttributeMatcherGroup threadPoolAttributes =
        attributeGroup(
            attributeWithAnyValue("camel.context"), attributeWithAnyValue("camel.threadpool.name"));

    // camel.route attribute is only present for route's thread pools
    AttributeMatcherGroup routeRelatedThreadPoolAttributes =
        attributeGroup(
            attributeWithAnyValue("camel.context"),
            attributeWithAnyValue("camel.route"),
            attributeWithAnyValue("camel.threadpool.name"));

    return MetricsVerifier.create()
        // context metrics
        .add(
            "camel.context.route.started",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasDescription(
                        "Indicates the number of routes started successfully since context start-up or the last reset operation.")
                    .hasUnit("{route}")
                    .hasDataPointsWithAttributes(contextAttributes))
        .add(
            "camel.context.route.added",
            metric ->
                metric
                    .isCounter()
                    .hasDescription(
                        "Indicates the total number of routes added successfully since context start-up or the last reset operation.")
                    .hasUnit("{route}")
                    .hasDataPointsWithAttributes(contextAttributes))
        .add(
            "camel.context.exchange.count",
            metric ->
                metric
                    .isCounter()
                    .hasDescription(
                        "Indicates the total number of exchanges, passed or failed, processed context start-up or the last reset operation.")
                    .hasUnit("{exchange}")
                    .hasDataPointsWithAttributes(contextAttributes))
        .add(
            "camel.context.exchange.completed",
            metric ->
                metric
                    .isCounter()
                    .hasDescription(
                        "Indicates the total number of exchanges processed successfully since context start-up or the last reset operation.")
                    .hasUnit("{exchange}")
                    .hasDataPointsWithAttributes(contextAttributes))
        .add(
            "camel.context.exchange.failed.count",
            metric ->
                metric
                    .isCounter()
                    .hasDescription(
                        "Indicates the total number of exchanges that failed to process since context start-up or the last reset operation.")
                    .hasUnit("{exchange}")
                    .hasDataPointsWithAttributes(contextAttributes))
        .add(
            "camel.context.exchange.failed.handled",
            metric ->
                metric
                    .isCounter()
                    .hasDescription(
                        "Indicates the number of exchanges failed and handled by an ExceptionHandler in the context.")
                    .hasUnit("{exchange}")
                    .hasDataPointsWithAttributes(contextAttributes))
        .add(
            "camel.context.exchange.inflight",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasDescription(
                        "Indicates the number of exchanges currently transiting the context.")
                    .hasUnit("{exchange}")
                    .hasDataPointsWithAttributes(contextAttributes))
        .add(
            "camel.context.exchange.redelivered.count",
            metric ->
                metric
                    .isCounter()
                    .hasDescription(
                        "Number of exchanges redelivered (internal only)  since context start-up or the last reset operation.")
                    .hasUnit("{exchange}")
                    .hasDataPointsWithAttributes(contextAttributes))
        .add(
            "camel.context.exchange.redelivered.external",
            metric ->
                metric
                    .isCounter()
                    .hasDescription(
                        "The total number of all external initiated redeliveries (such as from JMS broker) since context start-up or the last reset operation.")
                    .hasUnit("{exchange}")
                    .hasDataPointsWithAttributes(contextAttributes))
        .add(
            "camel.context.exchange.processing.duration.max",
            metric ->
                metric
                    .isGauge()
                    .hasDescription(
                        "Indicates the longest time, in milliseconds, to process an exchange since context start-up or the last reset operation.")
                    .hasUnit("s")
                    .hasDataPointsWithAttributes(contextAttributes))
        .add(
            "camel.context.exchange.processing.duration.mean",
            metric ->
                metric
                    .isGauge()
                    .hasDescription(
                        "Indicates the mean processing time, in milliseconds, for all exchanges processed since context start-up or the last reset operation.")
                    .hasUnit("s")
                    .hasDataPointsWithAttributes(contextAttributes))
        .add(
            "camel.context.exchange.processing.duration.min",
            metric ->
                metric
                    .isGauge()
                    .hasDescription(
                        "Indicates the shortest time, in milliseconds, to process an exchange since context start-up or the last reset operation.")
                    .hasUnit("s")
                    .hasDataPointsWithAttributes(contextAttributes))
        .add(
            "camel.context.exchange.processing.duration.last",
            metric ->
                metric
                    .isGauge()
                    .hasDescription(
                        "Indicates the time, in milliseconds, it took to process the last exchange.")
                    .hasUnit("s")
                    .hasDataPointsWithAttributes(contextAttributes))
        .add(
            "camel.context.exchange.processing.duration.last_delta",
            metric ->
                metric
                    .isGauge()
                    .hasDescription(
                        "Indicates the difference, in milliseconds, of the Processing Time of the last two exchanges transited the context.")
                    .hasUnit("s")
                    .hasDataPointsWithAttributes(contextAttributes))
        .add(
            "camel.context.exchange.processing.duration.sum",
            metric ->
                metric
                    .isCounter()
                    .hasDescription(
                        "Indicates the total processing time, in milliseconds, to process all exchanges since context start-up or the last reset operation.")
                    .hasUnit("s")
                    .hasDataPointsWithAttributes(contextAttributes))
        // route metrics
        .add(
            "camel.route.exchange.count",
            metric ->
                metric
                    .isCounter()
                    .hasDescription(
                        "Indicates the total number of exchanges, passed or failed, that the route has processed since route start-up or the last reset operation.")
                    .hasUnit("{exchange}")
                    .hasDataPointsWithAttributes(routeAttributes))
        .add(
            "camel.route.exchange.completed",
            metric ->
                metric
                    .isCounter()
                    .hasDescription(
                        "Indicates the total number of exchanges the route has processed successfully since route start-up or the last reset operation.")
                    .hasUnit("{exchange}")
                    .hasDataPointsWithAttributes(routeAttributes))
        .add(
            "camel.route.exchange.failed.count",
            metric ->
                metric
                    .isCounter()
                    .hasDescription(
                        "Indicates the total number of exchanges that the route has failed to process since route start-up or the last reset operation.")
                    .hasUnit("{exchange}")
                    .hasDataPointsWithAttributes(routeAttributes))
        .add(
            "camel.route.exchange.failed.handled",
            metric ->
                metric
                    .isCounter()
                    .hasDescription(
                        "Indicates the number of exchanges failed and handled by an ExceptionHandler in the route.")
                    .hasUnit("{exchange}")
                    .hasDataPointsWithAttributes(routeAttributes))
        .add(
            "camel.route.exchange.inflight",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasDescription(
                        "Indicates the number of exchanges currently transiting the route.")
                    .hasUnit("{exchange}")
                    .hasDataPointsWithAttributes(routeAttributes))
        .add(
            "camel.route.exchange.redelivered.count",
            metric ->
                metric
                    .isCounter()
                    .hasDescription(
                        "Number of exchanges redelivered (internal only)  since route start-up or the last reset operation.")
                    .hasUnit("{exchange}")
                    .hasDataPointsWithAttributes(routeAttributes))
        .add(
            "camel.route.exchange.redelivered.external",
            metric ->
                metric
                    .isCounter()
                    .hasDescription(
                        "The total number of all external initiated redeliveries (such as from JMS broker) since the route start-up or the last reset operation.")
                    .hasUnit("{exchange}")
                    .hasDataPointsWithAttributes(routeAttributes))
        .add(
            "camel.route.exchange.processing.duration.max",
            metric ->
                metric
                    .isGauge()
                    .hasDescription(
                        "Indicates the longest time, in milliseconds, to process an exchange since the route start-up or the last reset operation.")
                    .hasUnit("s")
                    .hasDataPointsWithAttributes(routeAttributes))
        .add(
            "camel.route.exchange.processing.duration.mean",
            metric ->
                metric
                    .isGauge()
                    .hasDescription(
                        "Indicates the mean processing time, in milliseconds, for all exchanges processed since the route start-up or the last reset operation.")
                    .hasUnit("s")
                    .hasDataPointsWithAttributes(routeAttributes))
        .add(
            "camel.route.exchange.processing.duration.min",
            metric ->
                metric
                    .isGauge()
                    .hasDescription(
                        "Indicates the shortest time, in milliseconds, to process an exchange since the route start-up or the last reset operation.")
                    .hasUnit("s")
                    .hasDataPointsWithAttributes(routeAttributes))
        .add(
            "camel.route.exchange.processing.duration.last",
            metric ->
                metric
                    .isGauge()
                    .hasDescription(
                        "Indicates the time, in milliseconds, it took the route to process the last exchange.")
                    .hasUnit("s")
                    .hasDataPointsWithAttributes(routeAttributes))
        .add(
            "camel.route.exchange.processing.duration.last_delta",
            metric ->
                metric
                    .isGauge()
                    .hasDescription(
                        "Indicates the difference, in milliseconds, of the Processing Time of the last two exchanges transited the route.")
                    .hasUnit("s")
                    .hasDataPointsWithAttributes(routeAttributes))
        .add(
            "camel.route.exchange.processing.duration.sum",
            metric ->
                metric
                    .isCounter()
                    .hasDescription(
                        "Indicates the total processing time, in milliseconds, of all exchanges the selected processed since route start-up or the last reset operation.")
                    .hasUnit("s")
                    .hasDataPointsWithAttributes(routeAttributes))
        // processor metrics
        .add(
            "camel.processor.exchange.count",
            metric ->
                metric
                    .isCounter()
                    .hasDescription(
                        "Indicates the total number of exchanges, passed or failed, that the selected processor has processed since processor start-up or the last reset operation.")
                    .hasUnit("{exchange}")
                    .hasDataPointsWithAttributes(
                        processorAttributes, destinationAwareProcessorAttributes))
        .add(
            "camel.processor.exchange.completed",
            metric ->
                metric
                    .isCounter()
                    .hasDescription(
                        "Indicates the total number of exchanges the selected processor has processed successfully since processor start-up or the last reset operation.")
                    .hasUnit("{exchange}")
                    .hasDataPointsWithAttributes(
                        processorAttributes, destinationAwareProcessorAttributes))
        .add(
            "camel.processor.exchange.failed.count",
            metric ->
                metric
                    .isCounter()
                    .hasDescription(
                        "Indicates the total number of exchanges that the selected processor has failed to process since processor start-up or the last reset operation.")
                    .hasUnit("{exchange}")
                    .hasDataPointsWithAttributes(
                        processorAttributes, destinationAwareProcessorAttributes))
        .add(
            "camel.processor.exchange.failed.handled",
            metric ->
                metric
                    .isCounter()
                    .hasDescription(
                        "Indicates the number of exchanges failed and handled by an ExceptionHandler in the context.")
                    .hasUnit("{exchange}")
                    .hasDataPointsWithAttributes(
                        processorAttributes, destinationAwareProcessorAttributes))
        .add(
            "camel.processor.exchange.inflight",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasDescription(
                        "Indicates the number of exchanges currently transiting the processor.")
                    .hasUnit("{exchange}")
                    .hasDataPointsWithAttributes(
                        processorAttributes, destinationAwareProcessorAttributes))
        .add(
            "camel.processor.exchange.redelivered.count",
            metric ->
                metric
                    .isCounter()
                    .hasDescription(
                        "Number of exchanges redelivered (internal only)  since selected processor start-up or the last reset operation.")
                    .hasUnit("{exchange}")
                    .hasDataPointsWithAttributes(
                        processorAttributes, destinationAwareProcessorAttributes))
        .add(
            "camel.processor.exchange.redelivered.external",
            metric ->
                metric
                    .isCounter()
                    .hasDescription(
                        "The total number of all external initiated redeliveries (such as from JMS broker) since processor start-up or the last reset operation.")
                    .hasUnit("{exchange}")
                    .hasDataPointsWithAttributes(
                        processorAttributes, destinationAwareProcessorAttributes))
        .add(
            "camel.processor.exchange.processing.duration.max",
            metric ->
                metric
                    .isGauge()
                    .hasDescription(
                        "Indicates the longest time, in milliseconds, to process an exchange since processor start-up or the last reset operation.")
                    .hasUnit("s")
                    .hasDataPointsWithAttributes(
                        processorAttributes, destinationAwareProcessorAttributes))
        .add(
            "camel.processor.exchange.processing.duration.mean",
            metric ->
                metric
                    .isGauge()
                    .hasDescription(
                        "Indicates the mean processing time, in milliseconds, for all exchanges processed since processor start-up or the last reset operation.")
                    .hasUnit("s")
                    .hasDataPointsWithAttributes(
                        processorAttributes, destinationAwareProcessorAttributes))
        .add(
            "camel.processor.exchange.processing.duration.min",
            metric ->
                metric
                    .isGauge()
                    .hasDescription(
                        "Indicates the shortest time, in milliseconds, to process an exchange since processor start-up or the last reset operation.")
                    .hasUnit("s")
                    .hasDataPointsWithAttributes(
                        processorAttributes, destinationAwareProcessorAttributes))
        .add(
            "camel.processor.exchange.processing.duration.last",
            metric ->
                metric
                    .isGauge()
                    .hasDescription(
                        "Indicates the time, in milliseconds, it took the selected processor to process the last exchange.")
                    .hasUnit("s")
                    .hasDataPointsWithAttributes(
                        processorAttributes, destinationAwareProcessorAttributes))
        .add(
            "camel.processor.exchange.processing.duration.last_delta",
            metric ->
                metric
                    .isGauge()
                    .hasDescription(
                        "Indicates the difference, in milliseconds, of the Processing Time of the last two exchanges transited the selected processor.")
                    .hasUnit("s")
                    .hasDataPointsWithAttributes(
                        processorAttributes, destinationAwareProcessorAttributes))
        .add(
            "camel.processor.exchange.processing.duration.sum",
            metric ->
                metric
                    .isCounter()
                    .hasDescription(
                        "Indicates the total processing time, in milliseconds, to process all exchanges since start-up or the last reset operation.")
                    .hasUnit("s")
                    .hasDataPointsWithAttributes(
                        processorAttributes, destinationAwareProcessorAttributes))
        // threadpool metrics
        .add(
            "camel.threadpool.task.count",
            metric ->
                metric
                    .isCounter()
                    .hasDescription(
                        "The approximate total number of tasks that have ever been scheduled for execution.")
                    .hasUnit("{task}")
                    .hasDataPointsWithAttributes(
                        threadPoolAttributes, routeRelatedThreadPoolAttributes))
        .add(
            "camel.threadpool.task.active",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasDescription(
                        "The approximate number of threads that are actively executing tasks.")
                    .hasUnit("{thread}")
                    .hasDataPointsWithAttributes(
                        threadPoolAttributes, routeRelatedThreadPoolAttributes))
        .add(
            "camel.threadpool.task.completed",
            metric ->
                metric
                    .isCounter()
                    .hasDescription(
                        "The approximate total number of tasks that have completed execution. Because the states of tasks and threads may change dynamically during computation, the returned value is only an approximation, but one that does not ever decrease across successive calls.")
                    .hasUnit("{task}")
                    .hasDataPointsWithAttributes(
                        threadPoolAttributes, routeRelatedThreadPoolAttributes))
        .add(
            "camel.threadpool.pool.size.current",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasDescription("The current number of threads in the pool.")
                    .hasUnit("{thread}")
                    .hasDataPointsWithAttributes(
                        threadPoolAttributes, routeRelatedThreadPoolAttributes))
        .add(
            "camel.threadpool.pool.size.max",
            metric ->
                metric
                    .isGauge()
                    .hasDescription(
                        "The largest number of threads that have ever simultaneously been in the pool.")
                    .hasUnit("{thread}")
                    .hasDataPointsWithAttributes(
                        threadPoolAttributes, routeRelatedThreadPoolAttributes))
        .add(
            "camel.threadpool.pool.core.size",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasDescription(
                        "The number of threads that are always kept in the pool, even if they are idle.")
                    .hasUnit("{thread}")
                    .hasDataPointsWithAttributes(
                        threadPoolAttributes, routeRelatedThreadPoolAttributes))
        .add(
            "camel.threadpool.task.queue.size",
            metric ->
                metric
                    .isUpDownCounter()
                    .hasDescription("The number of Tasks in the Task Queue.")
                    .hasUnit("{task}")
                    .hasDataPointsWithAttributes(
                        threadPoolAttributes, routeRelatedThreadPoolAttributes));
  }
}
