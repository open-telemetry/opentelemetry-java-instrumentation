/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.mapping;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;

class TomcatServlet3MappingTest extends AbstractServlet3MappingTest<Tomcat, Context> {
  @Override
  protected Tomcat setupServer() throws Exception {
    Tomcat tomcatServer = new Tomcat();

    File baseDir;
    try {
      baseDir = Files.createTempDirectory("tomcat").toFile();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    baseDir.deleteOnExit();
    tomcatServer.setBaseDir(baseDir.getAbsolutePath());

    tomcatServer.setPort(port);
    tomcatServer.getConnector().setEnableLookups(true); // get localhost instead of 127.0.0.1

    File applicationDir = new File(baseDir, "/webapps/ROOT");
    if (!applicationDir.exists()) {
      applicationDir.mkdirs();
      applicationDir.deleteOnExit();
    }

    Context servletContext;
    try {
      servletContext = tomcatServer.addWebapp(getContextPath(), applicationDir.getAbsolutePath());
    } catch (ServletException e) {
      throw new RuntimeException(e);
    }
    // Speed up startup by disabling jar scanning:
    servletContext.getJarScanner().setJarScanFilter((jarScanType, jarName) -> false);

    setupServlets(servletContext);

    try {
      tomcatServer.start();
    } catch (LifecycleException e) {
      throw new RuntimeException(e);
    }
    return tomcatServer;
  }

  @Override
  public void stopServer(Tomcat server) {
    try {
      server.stop();
      server.destroy();
    } catch (LifecycleException e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("ClassNewInstance")
  @Override
  public void addServlet(Context context, String path, Class<? extends Servlet> servlet) {
    String name = UUID.randomUUID().toString();
    try {
      Tomcat.addServlet(context, name, servlet.newInstance());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    context.addServletMappingDecoded(path, name);
  }

  @Override
  public String getContextPath() {
    return "/tomcat-context";
  }
}
