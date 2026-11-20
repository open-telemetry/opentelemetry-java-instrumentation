/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.boot.actuator.autoconfigure.v2_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.spring.boot.actuator.autoconfigure.v2_0.SpringApp.TestBean;
import java.util.ArrayList;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

class ActuatorTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void shouldInjectOtelMeterRegistry() {
    SpringApplication app = new SpringApplication(SpringApp.class);
    ConfigurableApplicationContext context = app.run();
    cleanup.deferCleanup(context);

    TestBean testBean = context.getBean(TestBean.class);
    testBean.inc();

    testing.waitAndAssertMetrics(
        "io.opentelemetry.micrometer-1.5",
        metric ->
            metric
                .hasName("test-counter")
                .hasUnit("thingies")
                .hasDoubleSumSatisfying(
                    sum ->
                        sum.isMonotonic()
                            .hasPointsSatisfying(
                                point ->
                                    point
                                        .hasValue(1)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("tag"), "value")))));

    MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);
    // the composite registry bean must be the one spring created, not a wrapper; beans that were
    // injected with the original composite would not see the wrapper, and its registry list would
    // be frozen at the time it was created
    assertThat(meterRegistry).isInstanceOf(CompositeMeterRegistry.class);
    assertThat(meterRegistry.getClass().getName()).doesNotStartWith("io.opentelemetry.");

    CompositeMeterRegistry composite = (CompositeMeterRegistry) meterRegistry;
    Set<MeterRegistry> registries = composite.getRegistries();
    assertOtelMeterRegistryIsLast(registries);

    // the actuator metrics endpoint reads from the first registry that has a matching meter, so
    // that registry has to be able to report the value
    Counter counter = findFirstMatchingCounter(composite);
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1);

    // the returned set is a live view of the composite, same as micrometer's
    int registryCount = registries.size();
    SimpleMeterRegistry added = new SimpleMeterRegistry();
    composite.add(added);
    assertThat(registries).hasSize(registryCount + 1);
    assertOtelMeterRegistryIsLast(registries);

    composite.remove(added);
    assertThat(registries).hasSize(registryCount);
  }

  private static void assertOtelMeterRegistryIsLast(Set<MeterRegistry> registries) {
    ArrayList<MeterRegistry> list = new ArrayList<>(registries);

    assertThat(list)
        .extracting(registry -> registry.getClass().getSimpleName())
        .endsWith("OpenTelemetryMeterRegistry");
  }

  private static Counter findFirstMatchingCounter(CompositeMeterRegistry composite) {
    for (MeterRegistry registry : composite.getRegistries()) {
      Counter counter = registry.find("test-counter").counter();
      if (counter != null) {
        return counter;
      }
    }
    return null;
  }
}
