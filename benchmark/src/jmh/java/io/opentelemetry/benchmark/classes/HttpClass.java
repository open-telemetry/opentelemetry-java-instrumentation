/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.benchmark.classes;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import java.io.IOException;
import java.net.InetSocketAddress;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;

public class HttpClass {
  private String contextPath = "/path";
  private Integer port = 18888;

  public Server buildJettyServer() {
    System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.StdErrLog");
    System.setProperty("org.eclipse.jetty.LEVEL", "WARN");

    Server jettyServer = new Server(new InetSocketAddress("localhost", port));
    ServletContextHandler servletContext = new ServletContextHandler();

    servletContext.addServlet(HttpClassServlet.class, contextPath);
    jettyServer.setHandler(servletContext);
    return jettyServer;
  }

  @WebServlet
  public static class HttpClassServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
      try {
        Thread.sleep(10);
      } catch (Exception e) {
      }
      resp.setContentType("application/json");
      resp.setStatus(HttpServletResponse.SC_OK);
      resp.getWriter().println("{ \"status\": \"ok\"}");
    }
  }

  private HttpRequestFactory requestFactory = new NetHttpTransport().createRequestFactory();

  public void executeRequest() throws IOException {
    String url = "http://localhost:" + port + contextPath;

    HttpRequest request = requestFactory.buildGetRequest(new GenericUrl(url));
    request.setThrowExceptionOnExecuteError(false);
    request.execute();
  }
}
