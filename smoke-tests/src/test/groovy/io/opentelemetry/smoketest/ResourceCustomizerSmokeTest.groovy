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
class ResourceCustomizerSmokeTest extends SmokeTest {

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

    then: "declarative config is applied"
    def serviceName = findResourceAttribute(traces, "service.name")
        .map { it.stringValue }
        .findAny()
    serviceName.isPresent()
    serviceName.get() == "declarative-config-smoke-test"

    then: "service detector is added by customizer"
    def serviceInstanceId = findResourceAttribute(traces, "service.instance.id")
        .map { it.stringValue }
        .findAny()
    serviceInstanceId.isPresent()

    then: "distro detector is added by customizer"
    def distroName = findResourceAttribute(traces, "telemetry.distro.name")
        .map { it.stringValue }
        .findAny()
    distroName.isPresent()
    serviceName.get() == "opentelemetry-java-instrumentation"

    cleanup:
    stopTarget()

    where:
    jdk << [8, 11, 17]
  }
}
