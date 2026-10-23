/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.tomcat;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static io.opentelemetry.semconv.incubating.UserIncubatingAttributes.USER_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.servlet.v3_0.TestServlet3;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest;
import io.opentelemetry.testing.internal.armeria.common.HttpMethod;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import javax.servlet.ServletException;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.ValveBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class Servlet3UserNameTest extends AbstractHttpServerUsingTest<Tomcat> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @BeforeAll
  void startTomcat() {
    super.startServer();
  }

  @AfterAll
  void cleanupTomcat() {
    cleanupServer();
  }

  @Override
  protected Tomcat setupServer() throws Exception {
    Tomcat tomcat = new Tomcat();
    File baseDir = Files.createTempDirectory("tomcat").toFile();
    baseDir.deleteOnExit();
    tomcat.setBaseDir(baseDir.getAbsolutePath());
    tomcat.setPort(port);
    tomcat.getConnector();

    Context context = tomcat.addContext(getContextPath(), new File(".").getAbsolutePath());
    Tomcat.addServlet(context, "servlet", new TestServlet3.Sync());
    context.addServletMappingDecoded(SUCCESS.getPath(), "servlet");
    context.getPipeline().addValve(new PrincipalValve());

    tomcat.start();
    return tomcat;
  }

  @Override
  protected void stopServer(Tomcat server) throws LifecycleException {
    server.stop();
    server.destroy();
  }

  @Override
  protected String getContextPath() {
    return "/tomcat-context";
  }

  @Test
  void capturesUserNameFromPrincipal() {
    AggregatedHttpRequest request =
        AggregatedHttpRequest.of(HttpMethod.GET, resolveAddress(SUCCESS));
    request =
        AggregatedHttpRequest.of(
            request.headers().toBuilder().add("X-Test-Principal", "true").build());
    client.execute(request).aggregate().join();

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasKind(SpanKind.SERVER), span -> span.hasName("controller"));
          assertThat(trace.getSpan(0).getInstrumentationScopeInfo().getName())
              .isEqualTo("io.opentelemetry.servlet-3.0");
          assertThat(trace.getSpan(0).getAttributes().get(USER_NAME))
              .isEqualTo(
                  Boolean.getBoolean("otel.instrumentation.common.user.name.enabled")
                      ? "test-user"
                      : null);
        });
  }

  private static class PrincipalValve extends ValveBase {

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
      if ("true".equals(request.getHeader("X-Test-Principal"))) {
        request.setUserPrincipal(() -> "test-user");
      }
      getNext().invoke(request, response);
    }
  }
}
