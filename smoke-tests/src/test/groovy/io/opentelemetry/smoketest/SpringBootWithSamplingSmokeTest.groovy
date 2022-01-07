/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import spock.lang.IgnoreIf

@IgnoreIf({ !useLinuxContainers() })
class SpringBootWithSamplingSmokeTest extends SmokeTest {

  static final double SAMPLER_PROBABILITY = 0.2
  static final int NUM_TRIES = 1000
  static final int ALLOWED_DEVIATION = 0.1 * NUM_TRIES

  protected String getTargetImage(String jdk) {
    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot:jdk$jdk-20211213.1570880324"
  }

  @Override
  protected Map<String, String> getExtraEnv() {
    return [
      "OTEL_TRACES_SAMPLER"    : "parentbased_traceidratio",
      "OTEL_TRACES_SAMPLER_ARG": String.valueOf(SAMPLER_PROBABILITY),
    ]
  }

  def "spring boot with probability sampling enabled on JDK #jdk"(int jdk) {
    setup:
    startTarget(jdk)
    when:
    for (int i = 1; i <= NUM_TRIES; i++) {
      client().get("/greeting").aggregate().join()
    }
    Collection<ExportTraceServiceRequest> traces = waitForTraces()

    then:
    // since sampling is enabled, not really expecting to receive NUM_TRIES spans
    Math.abs(countSpansByName(traces, 'WebController.greeting') - (SAMPLER_PROBABILITY * NUM_TRIES)) <= ALLOWED_DEVIATION
    Math.abs(countSpansByName(traces, '/greeting') - (SAMPLER_PROBABILITY * NUM_TRIES)) <= ALLOWED_DEVIATION

    cleanup:
    stopTarget()

    where:
    jdk << [8, 11, 17]
  }
}
