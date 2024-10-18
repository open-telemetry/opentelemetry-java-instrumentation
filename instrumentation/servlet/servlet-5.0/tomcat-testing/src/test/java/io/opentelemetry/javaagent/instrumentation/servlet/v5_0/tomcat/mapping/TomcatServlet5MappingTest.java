/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.tomcat.mapping;

import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.AbstractServlet5MappingTest;
import jakarta.servlet.Servlet;
import java.io.File;
import java.nio.file.Files;
import java.util.UUID;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;

class TomcatServlet5MappingTest extends AbstractServlet5MappingTest<Tomcat, Context> {
  @Override
  protected Tomcat setupServer() throws Exception {
    Tomcat tomcatServer = new Tomcat();

    File baseDir = Files.createTempDirectory("tomcat").toFile();
    baseDir.deleteOnExit();
    tomcatServer.setBaseDir(baseDir.getAbsolutePath());

    tomcatServer.setPort(port);
    tomcatServer.getConnector().setEnableLookups(true); // get localhost instead of 127.0.0.1

    File applicationDir = new File(baseDir, "/webapps/ROOT");
    if (!applicationDir.exists()) {
      applicationDir.mkdirs();
      applicationDir.deleteOnExit();
    }

    Context servletContext =
        tomcatServer.addWebapp(getContextPath(), applicationDir.getAbsolutePath());
    // Speed up startup by disabling jar scanning:
    servletContext.getJarScanner().setJarScanFilter((jarScanType, jarName) -> false);

    setupServlets(servletContext);

    tomcatServer.start();
    return tomcatServer;
  }

  @Override
  public void stopServer(Tomcat server) throws LifecycleException {
    server.stop();
    server.destroy();
  }

  @Override
  public void addServlet(Context context, String path, Class<? extends Servlet> servlet)
      throws Exception {
    String name = UUID.randomUUID().toString();
    Tomcat.addServlet(context, name, servlet.getConstructor().newInstance());
    context.addServletMappingDecoded(path, name);
  }

  @Override
  public String getContextPath() {
    return "/tomcat-context";
  }
}
