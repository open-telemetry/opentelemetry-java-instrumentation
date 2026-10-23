/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.appserver;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.UrlAttributes.URL_PATH;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.smoketest.AbstractSmokeTest;
import io.opentelemetry.smoketest.ResourceMapping;
import io.opentelemetry.smoketest.SmokeTestOptions;
import io.opentelemetry.smoketest.TargetWaitStrategy;
import io.opentelemetry.smoketest.TestContainerManager;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

abstract class TomcatSpringWarTest extends AbstractSmokeTest<AppServerImage> {

  @Override
  protected void configure(SmokeTestOptions<AppServerImage> options) {
    options
        .image(
            AppServerTest.appServerImage(
                "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-servlet-tomcat"))
        .waitStrategy(new TargetWaitStrategy.Log(Duration.ofMinutes(1), ".*Server startup in.*"))
        .extraResources(
            ResourceMapping.of("spring-war/probe.war", "/opt/probe.war"),
            ResourceMapping.of("spring-war/shared.war", "/opt/shared.war"),
            ResourceMapping.of(
                "spring-war/common-spring-libs.tar.gz", "/opt/common-spring-libs.tar.gz"))
        .entrypoint("/bin/sh", "-c")
        .command(
            "cp /opt/probe.war /server/webapps/probe.war && "
                + "mkdir -p /server/lib && "
                + "tar -xzf /opt/common-spring-libs.tar.gz -C /server/lib && "
                + "exec /server/bin/catalina.sh run");
  }

  @Test
  void orderedSpringWarDeployment() throws IOException, InterruptedException {
    assumeFalse(TestContainerManager.useWindowsContainers());

    var output = start(new AppServerImage("8", "9.0.111", false));
    assertHello("/probe/hello");

    var deployResult =
        containerManager.execTarget(
            "sh",
            "-c",
            "cp /opt/shared.war /server/webapps/shared.war; "
                + "count=0; "
                + "while [ \"$count\" -lt 600 ]; do "
                + "if grep -q 'Deployment of web application archive.*shared.war.*has finished' "
                + "/server/logs/catalina.\"$(date +%Y-%m-%d)\".log 2>/dev/null; then exit 0; fi; "
                + "sleep 0.1; count=$((count + 1)); "
                + "done; "
                + "cat /server/logs/catalina.\"$(date +%Y-%m-%d)\".log; exit 1");
    if (deployResult.getExitCode() != 0) {
      throw new AssertionError(deployResult.getStdout() + deployResult.getStderr());
    }

    assertHello("/shared/hello");
    if (agentEnabled()) {
      output.assertAgentVersionLogged();
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasKind(SpanKind.SERVER).hasAttribute(URL_PATH, "/probe/hello")),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasKind(SpanKind.SERVER).hasAttribute(URL_PATH, "/shared/hello")));
    }
  }

  protected boolean agentEnabled() {
    return true;
  }

  private void assertHello(String path) {
    var response = client().get(path).aggregate().join();
    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8()).isEqualTo("hello from Spring MVC\n");
  }
}
