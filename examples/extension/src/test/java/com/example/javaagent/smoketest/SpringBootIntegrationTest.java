/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent.smoketest;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;

class SpringBootIntegrationTest extends IntegrationTest {

  @Override
  protected String getTargetImage(int jdk) {
    return "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot:jdk"
        + jdk
        + "-20211213.1570880324";
  }

  @Override
  protected WaitStrategy getTargetWaitStrategy() {
    return Wait.forLogMessage(".*Started SpringbootApplication in.*", 1)
        .withStartupTimeout(Duration.ofMinutes(1));
  }

  @Test
  public void extensionsAreLoadedFromJar() throws IOException, InterruptedException {
    startTarget("/opentelemetry-extensions.jar");

    testAndVerify();

    stopTarget();
  }

  @Test
  public void extensionsAreLoadedFromFolder() throws IOException, InterruptedException {
    startTarget("/");

    testAndVerify();

    stopTarget();
  }

  @Test
  public void extensionsAreLoadedFromJavaagent() throws IOException, InterruptedException {
    startTargetWithExtendedAgent();

    testAndVerify();

    stopTarget();
  }

  private void testAndVerify() throws IOException, InterruptedException {
    String url = String.format("http://localhost:%d/greeting", target.getMappedPort(8080));
    Request request = new Request.Builder().url(url).get().build();

    String currentAgentVersion =
        (String)
            new JarFile(agentPath)
                .getManifest()
                .getMainAttributes()
                .get(Attributes.Name.IMPLEMENTATION_VERSION);

    Response response = client.newCall(request).execute();

    Collection<ExportTraceServiceRequest> traces = waitForTraces();

    assertThat(response.header("X-server-id")).isNotNull();
    assertThat(response.headers("X-server-id").size()).isEqualTo(1);
    assertThat(TraceId.isValid(response.header("X-server-id"))).isTrue();
    assertThat(response.body().string()).isEqualTo("Hi!");
    assertThat(countSpansByName(traces, "GET /greeting")).isEqualTo(1);
    assertThat(countSpansByName(traces, "WebController.greeting")).isEqualTo(0);
    assertThat(countSpansByName(traces, "WebController.withSpan")).isEqualTo(1);
    assertThat(countSpansByAttributeValue(traces, "custom", "demo")).isEqualTo(2);
    assertThat(countResourcesByValue(traces, "telemetry.distro.version", currentAgentVersion))
        .isNotEqualTo(0);
    assertThat(countResourcesByValue(traces, "custom.resource", "demo")).isNotEqualTo(0);
    assertThat(countSpansByAttributeValue(traces, "demo.custom", "demo-extension")).isEqualTo(1);
  }
}
