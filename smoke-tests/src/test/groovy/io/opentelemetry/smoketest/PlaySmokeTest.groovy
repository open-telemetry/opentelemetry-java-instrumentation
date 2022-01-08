/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import spock.lang.IgnoreIf

import static io.opentelemetry.smoketest.TestContainerManager.useWindowsContainers

@IgnoreIf({ useWindowsContainers() })
class PlaySmokeTest extends SmokeTest {

  protected String getTargetImage(String jdk) {
    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-play:jdk$jdk-20210917.1246460868"
  }

  def "play smoke test on JDK #jdk"(int jdk) {
    setup:
    startTarget(jdk)
    when:
    def response = client().get("/welcome?id=1").aggregate().join()
    Collection<ExportTraceServiceRequest> traces = waitForTraces()

    then:
    response.contentUtf8() == "Welcome 1."
    //Both play and akka-http support produce spans with the same name.
    //One internal, one SERVER
    countSpansByName(traces, '/welcome') == 2

    cleanup:
    stopTarget()

    where:
    // Play doesn't support Java 16 (or 17) yet
    // https://github.com/playframework/playframework/pull/10819
    jdk << [8, 11, 15]
  }
}
