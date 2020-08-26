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

import javax.servlet.Servlet
import javax.servlet.http.HttpServletRequest
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ErrorHandler
import org.eclipse.jetty.servlet.ServletHandler

class JettyServletHandlerTest extends AbstractServlet3Test<Server, ServletHandler> {

  static {
    //We want to test spans produced by servlet instrumentation, not those of jetty
    System.setProperty("otel.integration.jetty.enabled", "false")
  }

  @Override
  Server startServer(int port) {
    Server server = new Server(port)
    ServletHandler handler = new ServletHandler()
    server.setHandler(handler)
    setupServlets(handler)
    server.addBean(new ErrorHandler() {
      protected void handleErrorPage(HttpServletRequest request, Writer writer, int code, String message) throws IOException {
        Throwable th = (Throwable) request.getAttribute("javax.servlet.error.exception")
        writer.write(th ? th.message : message)
      }
    })
    server.start()
    return server
  }

  @Override
  void addServlet(ServletHandler servletHandler, String path, Class<Servlet> servlet) {
    servletHandler.addServletWithMapping(servlet, path)
  }

  @Override
  void stopServer(Server server) {
    server.stop()
    server.destroy()
  }

  @Override
  String getContext() {
    ""
  }

  @Override
  Class<Servlet> servlet() {
    TestServlet3.Sync
  }

  @Override
  boolean testNotFound() {
    false
  }
}
