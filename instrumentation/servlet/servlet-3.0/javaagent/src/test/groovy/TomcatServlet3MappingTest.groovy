/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import org.apache.catalina.Context
import org.apache.catalina.startup.Tomcat
import org.apache.tomcat.JarScanFilter
import org.apache.tomcat.JarScanType

import javax.servlet.Servlet
import java.nio.file.Files

class TomcatServlet3MappingTest extends AbstractServlet3MappingTest<Tomcat, Context> {

  @Override
  Tomcat startServer(int port) {
    def tomcatServer = new Tomcat()

    def baseDir = Files.createTempDirectory("tomcat").toFile()
    baseDir.deleteOnExit()
    tomcatServer.setBaseDir(baseDir.getAbsolutePath())

    tomcatServer.setPort(port)
    tomcatServer.getConnector().enableLookups = true // get localhost instead of 127.0.0.1

    File applicationDir = new File(baseDir, "/webapps/ROOT")
    if (!applicationDir.exists()) {
      applicationDir.mkdirs()
      applicationDir.deleteOnExit()
    }
    Context servletContext = tomcatServer.addWebapp(contextPath, applicationDir.getAbsolutePath())
    // Speed up startup by disabling jar scanning:
    servletContext.getJarScanner().setJarScanFilter(new JarScanFilter() {
      @Override
      boolean check(JarScanType jarScanType, String jarName) {
        return false
      }
    })

    setupServlets(servletContext)

    tomcatServer.start()

    return tomcatServer
  }

  @Override
  void stopServer(Tomcat server) {
    server.stop()
    server.destroy()
  }

  @Override
  void addServlet(Context servletContext, String path, Class<Servlet> servlet) {
    String name = UUID.randomUUID()
    Tomcat.addServlet(servletContext, name, servlet.newInstance())
    servletContext.addServletMappingDecoded(path, name)
  }

  @Override
  String getContextPath() {
    return "/tomcat-context"
  }
}
