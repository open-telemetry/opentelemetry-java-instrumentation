package com.datadoghq.agent.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TomcatServletInstrumentationTest {

  private final int serverPort = 9786;

  protected Tomcat tomcatServer;
  Context appContext;

  @Before
  public void beforeTest() throws Exception {
    tomcatServer = new Tomcat();
    tomcatServer.setPort(serverPort);

    final File baseDir = new File("tomcat");
    tomcatServer.setBaseDir(baseDir.getAbsolutePath());

    final File applicationDir = new File(baseDir + "/webapps", "/ROOT");
    if (!applicationDir.exists()) {
      applicationDir.mkdirs();
    }
    appContext = tomcatServer.addWebapp("", applicationDir.getAbsolutePath());
    //	        Tomcat.addServlet(appContext, "helloWorldServlet", new TestServlet());
    //	        appContext.addServletMappingDecoded("/hello", "helloWorldServlet");

    tomcatServer.start();
    System.out.println(
        "Tomcat server: http://" + tomcatServer.getHost().getName() + ":" + serverPort + "/");
  }

  @Test
  public void test() {
    assertThat(appContext.getServletContext().getFilterRegistration("tracingFilter")).isNotNull();
  }

  @After
  public void afterTest() throws Exception {
    tomcatServer.stop();
  }
}
