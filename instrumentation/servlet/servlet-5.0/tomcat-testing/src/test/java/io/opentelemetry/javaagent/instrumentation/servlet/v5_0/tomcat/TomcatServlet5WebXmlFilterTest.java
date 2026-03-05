/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.tomcat;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression test for <a
 * href="https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/16393">#16393</a>.
 *
 * <p>When a servlet filter is deployed via web.xml and loaded by Tomcat's WebappClassLoader (rather
 * than being registered programmatically), a ClassCastException can occur in {@code
 * Servlet5Singletons.getMappingResolverFactory} because the {@code
 * JakartaServletFilterMappingResolverFactory} (from the agent classloader) cannot be cast to {@code
 * MappingResolver.Factory} (loaded by the webapp classloader).
 *
 * <p>This test deploys a filter and servlet via web.xml into an embedded Tomcat where the classes
 * are loaded from WEB-INF/classes by the WebappClassLoader, reproducing the classloader separation
 * that triggers the bug.
 */
class TomcatServlet5WebXmlFilterTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @TempDir static File tempDir;

  static Tomcat tomcat;
  static int port;

  @BeforeAll
  static void setup() throws Exception {
    // Create webapp directory structure with WEB-INF/classes
    File webappDir = new File(tempDir, "webapp");
    File webInfDir = new File(webappDir, "WEB-INF");

    // Create the package directory for filter and servlet classes
    String packagePath = WebXmlTestFilter.class.getPackage().getName().replace('.', '/');
    File packageDir = new File(new File(webInfDir, "classes"), packagePath);
    packageDir.mkdirs();

    // Copy compiled filter and servlet .class files to WEB-INF/classes so that
    // Tomcat's WebappClassLoader loads them (rather than the parent/system classloader)
    copyClassFile(WebXmlTestFilter.class, packageDir);
    copyClassFile(WebXmlTestServlet.class, packageDir);

    // Create web.xml that declares the filter and servlet
    String webXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<web-app xmlns=\"https://jakarta.ee/xml/ns/jakartaee\"\n"
            + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
            + "         xsi:schemaLocation=\"https://jakarta.ee/xml/ns/jakartaee\n"
            + "         https://jakarta.ee/xml/ns/jakartaee/web-app_5_0.xsd\"\n"
            + "         version=\"5.0\">\n"
            + "  <servlet>\n"
            + "    <servlet-name>testServlet</servlet-name>\n"
            + "    <servlet-class>"
            + WebXmlTestServlet.class.getName()
            + "</servlet-class>\n"
            + "  </servlet>\n"
            + "  <servlet-mapping>\n"
            + "    <servlet-name>testServlet</servlet-name>\n"
            + "    <url-pattern>/users/*</url-pattern>\n"
            + "  </servlet-mapping>\n"
            + "  <filter>\n"
            + "    <filter-name>testFilter</filter-name>\n"
            + "    <filter-class>"
            + WebXmlTestFilter.class.getName()
            + "</filter-class>\n"
            + "  </filter>\n"
            + "  <filter-mapping>\n"
            + "    <filter-name>testFilter</filter-name>\n"
            + "    <url-pattern>/*</url-pattern>\n"
            + "  </filter-mapping>\n"
            + "</web-app>\n";
    try (OutputStream os = new FileOutputStream(new File(webInfDir, "web.xml"))) {
      os.write(webXml.getBytes(UTF_8));
    }

    // Start embedded Tomcat with the webapp deployed via addWebapp,
    // which creates a WebappClassLoader for the context
    tomcat = new Tomcat();
    tomcat.setBaseDir(tempDir.getAbsolutePath());
    tomcat.setPort(0);
    tomcat.getConnector().setEnableLookups(true);

    Context ctx = tomcat.addWebapp("/app", webappDir.getAbsolutePath());
    // Disable jar scanning for faster startup
    ctx.getJarScanner().setJarScanFilter((type, name) -> false);

    tomcat.start();
    port = tomcat.getConnector().getLocalPort();
  }

  @AfterAll
  static void cleanup() throws Exception {
    if (tomcat != null) {
      tomcat.stop();
      tomcat.destroy();
    }
  }

  private static String readFully(InputStream is) throws Exception {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    byte[] data = new byte[1024];
    int len;
    while ((len = is.read(data)) != -1) {
      buffer.write(data, 0, len);
    }
    return buffer.toString(UTF_8.name());
  }

  private static void copyClassFile(Class<?> clazz, File targetDir) throws Exception {
    String classFileName = clazz.getSimpleName() + ".class";
    String classResourcePath = clazz.getName().replace('.', '/') + ".class";
    try (InputStream is = clazz.getClassLoader().getResourceAsStream(classResourcePath)) {
      assertThat(is).as("Class file resource for " + clazz.getName()).isNotNull();
      Files.copy(
          is, new File(targetDir, classFileName).toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  @Test
  void filterLoadedFromWebXmlDoesNotCauseClassCastException() throws Exception {
    HttpURLConnection connection =
        (HttpURLConnection)
            URI.create("http://localhost:" + port + "/app/users/123").toURL().openConnection();
    int responseCode = connection.getResponseCode();
    String body = readFully(connection.getInputStream());
    connection.disconnect();

    assertThat(responseCode).isEqualTo(200);
    assertThat(body).isEqualTo("OK");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("GET /app/users/*").hasKind(SpanKind.SERVER)));
  }
}
