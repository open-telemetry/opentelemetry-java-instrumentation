/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.mapping;

import java.io.File;
import java.util.UUID;
import javax.servlet.Servlet;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.io.TempDir;

class TomcatServlet3MappingTest extends AbstractServlet3MappingTest<Tomcat, Context> {
  @TempDir private static File tempDir;

  @Override
  protected Tomcat setupServer() throws Exception {
    Tomcat tomcatServer = new Tomcat();

    File baseDir = tempDir;
    tomcatServer.setBaseDir(baseDir.getAbsolutePath());

    tomcatServer.setPort(port);
    tomcatServer.getConnector().setEnableLookups(true); // get localhost instead of 127.0.0.1

    File applicationDir = new File(baseDir, "/webapps/ROOT");
    applicationDir.mkdirs();

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
