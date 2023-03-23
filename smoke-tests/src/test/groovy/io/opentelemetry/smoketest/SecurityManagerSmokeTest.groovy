/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import spock.lang.IgnoreIf
import spock.lang.Unroll

import static io.opentelemetry.smoketest.TestContainerManager.useWindowsContainers

@IgnoreIf({ useWindowsContainers() })
class SecurityManagerSmokeTest extends SmokeTest {

  protected String getTargetImage(String jdk) {
    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-security-manager:jdk$jdk-20230323.4502979551"
  }

  @Unroll
  def "security manager smoke test on JDK #jdk"(int jdk) {
    setup:
    startTarget(jdk)

    expect:
    Collection<ExportTraceServiceRequest> traces = waitForTraces()
    countSpansByName(traces, 'test') == 1

    cleanup:
    stopTarget()

    where:
    jdk << [8, 11, 17, 19]
  }
}
