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


import okhttp3.Request

class WildflySmokeTest extends SmokeTest {

  protected String getTargetImage() {
    "jboss/wildfly:latest"
  }

  //We don't have support for Wildfly Undertow server yet.
  //So this test just verifies that Wildfly has come up.
  def "wildfly smoke test"() {
    setup:
    String url = "http://localhost:${target.getMappedPort(8080)}"
    def request = new Request.Builder().url(url).get().build()

    when:
    def response = client.newCall(request).execute()

    then:
    def responseBodyStr = response.body().string()
    responseBodyStr != null
    responseBodyStr.contains("Your WildFly instance is running.")
    response.body().contentType().toString().contains("text/html")
    response.code() == 200
  }

}
