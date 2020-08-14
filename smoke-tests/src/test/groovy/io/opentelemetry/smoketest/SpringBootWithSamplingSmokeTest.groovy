/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.smoketest

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import okhttp3.Request

class SpringBootWithSamplingSmokeTest extends SmokeTest {

  static final double SAMPLER_PROBABILITY = 0.2
  static final int NUM_TRIES = 1000
  static final int ALLOWED_DEVIATION = 0.1 * NUM_TRIES

  protected String getTargetImage(int jdk) {
    "open-telemetry-docker-dev.bintray.io/java/smoke-springboot-jdk$jdk:latest"
  }

  @Override
  protected Map<String, String> getExtraEnv() {
    return ["OTEL_CONFIG_SAMPLER_PROBABILITY": String.valueOf(SAMPLER_PROBABILITY)]
  }

  def "spring boot with probability sampling enabled on JDK #jdk"(int jdk) {
    setup:
    startTarget(jdk)
    String url = "http://localhost:${target.getMappedPort(8080)}/greeting"
    def request = new Request.Builder().url(url).get().build()

    when:
    for (int i = 1; i <= NUM_TRIES; i++) {
      client.newCall(request).execute()
    }
    Collection<ExportTraceServiceRequest> traces = waitForTraces()

    then:
    // since sampling is enabled, not really expecting to receive NUM_TRIES spans
    Math.abs(countSpansByName(traces, 'WebController.greeting') - (SAMPLER_PROBABILITY * NUM_TRIES)) <= ALLOWED_DEVIATION
    Math.abs(countSpansByName(traces, '/greeting') - (SAMPLER_PROBABILITY * NUM_TRIES)) <= ALLOWED_DEVIATION

    cleanup:
    stopTarget()

    where:
    jdk << [8, 11, 14]
  }
}
