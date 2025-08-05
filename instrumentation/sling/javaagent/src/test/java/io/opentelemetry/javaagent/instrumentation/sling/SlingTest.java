/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.sling;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.javaagent.testing.common.TestAgentListenerAccess;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.sling.feature.launcher.impl.Bootstrap;
import org.apache.sling.feature.launcher.impl.LauncherConfig;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlingTest extends AbstractHttpServerUsingTest<Void> {

  @RegisterExtension
  public static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  private final Logger log = LoggerFactory.getLogger(getClass());

  @BeforeAll
  void setup() {
    startServer();
  }

  @AfterAll
  void cleanup() {
    cleanupServer();
  }

  @Test
  void basic() throws Exception {
    AggregatedHttpResponse response =
        client.get(address.resolve("starter.html").toString()).aggregate().join();

    assertThat(response.status().code()).isEqualTo(200);

    // FIXME - we need to reset this because of unrelated failures; not supported by Apache Sling
    // https://github.com/apache/sling-org-apache-sling-engine/blob/8ef91a0ea56d3fa919fb1cde3d1c08419722fe45/src/main/java/org/apache/sling/engine/impl/helper/SlingServletContext.java#L763-L767
    TestAgentListenerAccess.getAndResetAdviceFailureCount();

    // FIXME - Muzzle checks we need to clarify
    //    [otel.javaagent 2025-07-02 13:00:07:012 +0200] [FelixStartLevel] WARN
    // io.opentelemetry.javaagent.tooling.instrumentation.MuzzleMatcher - Instrumentation skipped,
    // mismatched references were found: sling [class
    // io.opentelemetry.javaagent.instrumentation.sling.SlingInstrumentationModule] on
    // org.apache.sling.installer.console [217]
    //    [otel.javaagent 2025-07-02 13:00:07:012 +0200] [FelixStartLevel] WARN
    // io.opentelemetry.javaagent.tooling.instrumentation.MuzzleMatcher - --
    // io.opentelemetry.javaagent.instrumentation.sling.ServletResolverInstrumentation$ResolveServletAdvice:65 Missing class javax.servlet.http.HttpServletRequest
    //    [otel.javaagent 2025-07-02 13:00:07:015 +0200] [FelixStartLevel] WARN
    // io.opentelemetry.javaagent.tooling.instrumentation.MuzzleMatcher - Instrumentation skipped,
    // mismatched references were found: servlet [class
    // io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3InstrumentationModule] on
    // org.apache.sling.installer.console [217]
    //    [otel.javaagent 2025-07-02 13:00:07:017 +0200] [FelixStartLevel] WARN
    // io.opentelemetry.javaagent.tooling.instrumentation.MuzzleMatcher - --
    // io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet.Servlet3SnippetInjectingResponseWrapper:56 Missing class javax.servlet.http.HttpServletResponse
    //    [otel.javaagent 2025-07-02 13:00:07:017 +0200] [FelixStartLevel] WARN
    // io.opentelemetry.javaagent.tooling.instrumentation.MuzzleMatcher - --
    // io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3Accessor:28 Missing class
    // javax.servlet.http.HttpServletRequest
    //    [otel.javaagent 2025-07-02 13:00:07:017 +0200] [FelixStartLevel] WARN
    // io.opentelemetry.javaagent.tooling.instrumentation.MuzzleMatcher - --
    // io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet.ServletOutputStreamInjectionState:20 Missing method io.opentelemetry.javaagent.bootstrap.servlet.SnippetInjectingResponseWrapper#getCharacterEncoding()Ljava/lang/String;
    //    [otel.javaagent 2025-07-02 13:00:07:017 +0200] [FelixStartLevel] WARN
    // io.opentelemetry.javaagent.tooling.instrumentation.MuzzleMatcher - --
    // io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet.Servlet3SnippetInjectingResponseWrapper:0 Missing class javax.servlet.http.HttpServletResponseWrapper
    //
    // Potentially because of optional imports - Import-Package:
    // javax.servlet;resolution:=optional;version="[3.1,4)"
    TestAgentListenerAccess.getAndResetMuzzleFailureCount();

    List<List<SpanData>> traces = testing.waitForTraces(1);

    List<SpanData> mainTrace = traces.get(0);
    assertThat(mainTrace).hasSize(3);
    // top-level trace
    assertThat(mainTrace.get(0))
        .hasKind(SpanKind.SERVER)
        .hasAttributesSatisfying(
            attributes -> {
              assertThat(attributes).containsEntry("http.request.method", "GET");
              assertThat(attributes)
                  .containsEntry("http.route", "/apps/sling/starter/home/home.html.esp");
            });

    assertThat(mainTrace.get(1))
        .hasKind(SpanKind.INTERNAL)
        .hasName("/apps/sling/starter/home/home.html.esp")
        .hasInstrumentationScopeInfo(InstrumentationScopeInfo.create("io.opentelemetry.sling-1.0"));

    assertThat(mainTrace.get(2))
        .hasKind(SpanKind.INTERNAL)
        .hasName("/apps/sling/starter/sidebar-extensions/sidebar-extensions.html.esp")
        .hasInstrumentationScopeInfo(InstrumentationScopeInfo.create("io.opentelemetry.sling-1.0"));
  }

  @Override
  protected Void setupServer() throws Exception {

    Path homeDir = Files.createTempDirectory("javaagent_sling-test");

    LauncherConfig cfg = new LauncherConfig();
    cfg.getInstallation()
        .addFrameworkProperty("org.osgi.service.http.port", String.valueOf(this.port));
    cfg.setHomeDirectory(homeDir.toFile());
    cfg.addFeatureFiles(
        "mvn:org.apache.sling/org.apache.sling.starter/13/slingosgifeature/oak_tar");

    CompletableFuture.runAsync(
        () -> {
          try {
            Bootstrap b = new Bootstrap(cfg, log);
            b.runWithException();
          } catch (Exception e) {
            log.error("Failed to start Sling server, test will time out and fail.", e);
          }
        });

    Awaitility.await()
        .atMost(Duration.ofMinutes(1))
        .pollInterval(Duration.ofMillis(500))
        .ignoreExceptions()
        .until(
            () -> {
              try (Socket s = new Socket(address.getHost(), address.getPort())) {
                return s.isConnected();
              } catch (Exception e) {
                return false;
              }
            });

    Awaitility.await()
        .atMost(Duration.ofMinutes(4))
        .pollInterval(Duration.ofMillis(500))
        .ignoreExceptions()
        .until(
            () -> {
              try {
                HttpResponse response = client.get(address.resolve("/").toString());
                return response.aggregate().join().status().code() >= 200
                    && response.aggregate().join().status().code() < 400;
              } catch (RuntimeException e) {
                return false;
              }
            });

    return null;
  }

  @Override
  protected void stopServer(Void unused) throws Exception {
    log.warn("Stopping Sling server is not implemented");
  }

  @Override
  protected String getContextPath() {
    return "";
  }
}
