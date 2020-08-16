/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
