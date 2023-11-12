/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.classhook.smoketest;

import java.io.IOException;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SpringBootIntegrationTest extends IntegrationTest {

  @Override
  protected String getTargetImage(int jdk) {
    return "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot:jdk"
        + jdk
        + "-20211213.1570880324";
  }

  @Test
  public void extensionsAreLoadedFromJar() throws IOException {
    startTarget();

    testAndVerify();

    stopTarget();
  }

  private void testAndVerify() throws IOException {
    String url = String.format("http://localhost:%d/greeting", target.getMappedPort(8080));
    Request request = new Request.Builder().url(url).get().build();

    Response response = client.newCall(request).execute();

    String resp = response.body().string();

    Assertions.assertEquals("ClassHookMethodAdvice -- Hi!", resp);
  }
}
