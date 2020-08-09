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

class PlaySmokeTest extends SmokeTest {

  protected String getTargetImage() {
    "docker.pkg.github.com/inikem/opentelemetry-java-instrumentation/smoke-play:latest"
  }

  def "play smoke test"() {
    setup:
    String url = "http://localhost:${target.getMappedPort(8080)}/welcome?id=1"
    def request = new Request.Builder().url(url).get().build()

    when:
    def response = client.newCall(request).execute()
    Collection<ExportTraceServiceRequest> traces = waitForTraces()

    then:
    response.body().string() == "Welcome 1."
    //Both play and akka-http support produce spans with the same name.
    //One internal, one SERVER
    countSpansByName(traces, 'GET /welcome') == 2
  }

}
