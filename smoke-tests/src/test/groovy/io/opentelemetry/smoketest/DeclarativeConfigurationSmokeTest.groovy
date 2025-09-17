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
    traces.size() == 1

    then: "There is one span (io.opentelemetry.opentelemetry-instrumentation-annotations-1.16 " +
        "is not used, because instrumentation_mode=none)"
    getSpanStream(traces).count() == 1

    then: "explicitly set attribute is present"
    hasResourceAttribute(traces, "service.name", "declarative-config-smoke-test")

    then: "explicitly set container detector is used"
    findResourceAttribute(traces, "container.id").findAny().isPresent()

    then: "explicitly set container process detector is used"
    findResourceAttribute(traces, "process.executable.path").findAny().isPresent()

    then: "explicitly set container host detector is used"
    findResourceAttribute(traces, "host.name").findAny().isPresent()

    then: "distro detector is added by customizer"
    hasResourceAttribute(traces, "telemetry.distro.name", "opentelemetry-javaagent")

    cleanup:
    stopTarget()

    where:
    jdk << [8, 11, 17]
  }
}
