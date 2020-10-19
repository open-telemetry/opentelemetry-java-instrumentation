/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import okhttp3.Request

class PlaySmokeTest extends SmokeTest {

  protected String getTargetImage(int jdk) {
    "open-telemetry-docker-dev.bintray.io/java/smoke-play-jdk$jdk:latest"
  }

  def "play smoke test on JDK #jdk"(int jdk) {
    setup:
    startTarget(jdk)
    String url = "http://localhost:${target.getMappedPort(8080)}/welcome?id=1"
    def request = new Request.Builder().url(url).get().build()

    when:
    def response = CLIENT.newCall(request).execute()
    Collection<ExportTraceServiceRequest> traces = waitForTraces()

    then:
    response.body().string() == "Welcome 1."
    //Both play and akka-http support produce spans with the same name.
    //One internal, one SERVER
    countSpansByName(traces, '/welcome') == 2

    cleanup:
    stopTarget()

    where:
    jdk << [8, 11, 15]
  }

}
