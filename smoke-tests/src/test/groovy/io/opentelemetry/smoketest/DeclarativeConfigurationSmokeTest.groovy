/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest


import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import spock.lang.IgnoreIf
import spock.lang.Unroll

import java.time.Duration

import static io.opentelemetry.smoketest.TestContainerManager.useWindowsContainers

@IgnoreIf({ useWindowsContainers() })
class DeclarativeConfigurationSmokeTest extends SmokeTest {

  protected String getTargetImage(String jdk) {
    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot:jdk$jdk-20241021.11448062567"
  }

  @Override
  protected List<ResourceMapping> getExtraResources() {
    [
        ResourceMapping.of("declarative-config.yaml", "/declarative-config.yaml"),
    ]
  }

  @Override
  protected Map<String, String> getExtraEnv() {
    return ["OTEL_EXPERIMENTAL_CONFIG_FILE": "declarative-config.yaml"]
  }

  @Override
  protected TargetWaitStrategy getWaitStrategy() {
    return new TargetWaitStrategy.Log(Duration.ofMinutes(1), ".*Started SpringbootApplication in.*")
  }

  @Unroll
  def "spring boot smoke test on JDK #jdk"(int jdk) {
    setup:
    startTarget(jdk)

    when:
    client().get("/greeting").aggregate().join()
    Collection<ExportTraceServiceRequest> traces = waitForTraces()

    then: "There is one trace"
    traces.size() > 0

    then: "distro detector is added by customizer"
    def distroName = findResourceAttribute(traces, "telemetry.distro.name")
        .map { it.stringValue }
        .findAny()
    distroName.isPresent()
    distroName.get() == "opentelemetry-javaagent"

    cleanup:
    stopTarget()

    where:
    jdk << [8, 11, 17]
  }
}
