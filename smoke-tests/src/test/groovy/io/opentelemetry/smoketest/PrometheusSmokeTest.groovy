/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import io.opentelemetry.testing.internal.armeria.client.WebClient
import java.time.Duration
import spock.lang.IgnoreIf

import static io.opentelemetry.smoketest.TestContainerManager.useWindowsContainers

@IgnoreIf({ useWindowsContainers() })
class PrometheusSmokeTest extends SmokeTest {
  private static final int PROMETHEUS_PORT = 9090

  protected String getTargetImage(String jdk) {
    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot:jdk$jdk-20211213.1570880324"
  }

  @Override
  protected Map<String, String> getExtraEnv() {
    return Map.of("OTEL_METRICS_EXPORTER", "prometheus", "OTEL_EXPORTER_PROMETHEUS_PORT", PROMETHEUS_PORT.toString())
  }

  @Override
  protected TargetWaitStrategy getWaitStrategy() {
    return new TargetWaitStrategy.Log(Duration.ofMinutes(1), ".*Started SpringbootApplication in.*")
  }

  protected List<ResourceMapping> getExtraPorts() {
    return [PROMETHEUS_PORT]
  }

  def "Should export metrics"(int jdk) {
    setup:
    startTarget(jdk)

    when:
    client().get("/greeting").aggregate().join()

    then:
    def prometheusClient = WebClient.of("h1c://localhost:${containerManager.getTargetMappedPort(9090)}")
    def prometheusData = prometheusClient.get("/").aggregate().join().contentUtf8()

    prometheusData.contains("process_runtime_jvm_memory_usage")

    cleanup:
    stopTarget()

    where:
    jdk << [8, 11, 17]
  }

}