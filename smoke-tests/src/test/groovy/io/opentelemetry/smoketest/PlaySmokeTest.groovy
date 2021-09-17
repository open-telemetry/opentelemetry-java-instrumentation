/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import spock.lang.IgnoreIf

@IgnoreIf({ os.windows })
class PlaySmokeTest extends SmokeTest {

  protected String getTargetImage(String jdk) {
    "ghcr.io/open-telemetry/java-test-containers:smoke-play-jdk$jdk-20210915.1238703013"
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
    jdk << [8, 11, 16]
  }

}
