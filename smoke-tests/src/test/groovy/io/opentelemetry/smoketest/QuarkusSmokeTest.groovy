/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import io.opentelemetry.semconv.ServiceAttributes
import spock.lang.IgnoreIf
import spock.lang.Unroll

import java.time.Duration
import java.util.jar.Attributes
import java.util.jar.JarFile

import static io.opentelemetry.smoketest.TestContainerManager.useWindowsContainers

@IgnoreIf({ useWindowsContainers() })
class QuarkusSmokeTest extends SmokeTest {

  protected String getTargetImage(String jdk) {
    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-quarkus:jdk$jdk-20241105.11678591860"
  }

  @Override
  protected TargetWaitStrategy getWaitStrategy() {
    return new TargetWaitStrategy.Log(Duration.ofMinutes(1), ".*Listening on.*")
  }

  @Override
  protected boolean getSetServiceName() {
    return false
  }

  @Unroll
  def "quarkus smoke test on JDK #jdk"(int jdk) {
    setup:
    startTarget(jdk)

    def currentAgentVersion = new JarFile(agentPath).getManifest().getMainAttributes().get(Attributes.Name.IMPLEMENTATION_VERSION)

    when:
    client().get("/hello").aggregate().join()
    TraceInspector traces = new TraceInspector(waitForTraces())

    then: "Expected span names"
    traces.countSpansByName('GET /hello') == 1

    and: "telemetry.distro.version is set"
    traces.countFilteredResourceAttributes("telemetry.distro.version", currentAgentVersion) == 1

    and: "service.name is detected from manifest"
    traces.countFilteredResourceAttributes(ServiceAttributes.SERVICE_NAME.key, "quarkus") == 1

    cleanup:
    stopTarget()

    where:
    jdk << [17, 21, 23] // Quarkus 3.7+ requires Java 17+
  }
}
