/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest


import okhttp3.Request

class WildflySmokeTest extends SmokeTest {

  protected String getTargetImage(int jdk) {
    "jboss/wildfly:latest"
  }

  //We don't have support for Wildfly Undertow server yet.
  //So this test just verifies that Wildfly has come up.
  def "wildfly smoke test"() {
    setup:
    startTarget(11) // does not actually matter
    String url = "http://localhost:${target.getMappedPort(8080)}"
    def request = new Request.Builder().url(url).get().build()

    when:
    def response = CLIENT.newCall(request).execute()

    then:
    def responseBodyStr = response.body().string()
    responseBodyStr != null
    responseBodyStr.contains("Your WildFly instance is running.")
    response.body().contentType().toString().contains("text/html")
    response.code() == 200

    cleanup:
    stopTarget()
  }

}
