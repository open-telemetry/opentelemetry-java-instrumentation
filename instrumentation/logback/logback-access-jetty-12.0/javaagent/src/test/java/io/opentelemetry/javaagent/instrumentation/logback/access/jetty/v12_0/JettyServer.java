/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.access.jetty.v12_0;

import ch.qos.logback.access.jetty.RequestLogImpl;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;

public class JettyServer {
  protected final RequestLogImpl requestLogImpl;
  protected Handler handler = new BasicHandler();
  private final int port;
  Server server;
  protected String url;

  public JettyServer(RequestLogImpl impl, int port) {
    requestLogImpl = impl;
    this.port = port;
    url = "http://localhost:" + port + "/";
  }

  public String getName() {
    return "Jetty Test Setup";
  }

  public String getUrl() {
    return url;
  }

  public void start() throws Exception {
    server = new Server(port);

    server.setRequestLog(requestLogImpl);
    configureRequestLogImpl();

    server.setHandler(getRequestHandler());
    server.start();
  }

  public void stop() throws Exception {
    server.stop();
    server = null;
  }

  protected void configureRequestLogImpl() {
    requestLogImpl.start();
  }

  protected Handler getRequestHandler() {
    return handler;
  }

  static class BasicHandler extends Handler.Wrapper {

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
      HttpFields.Mutable responseHeaders = response.getHeaders();
      responseHeaders.put(HttpHeader.CONTENT_TYPE, "text/plain; charset=UTF-8");

      byte[] bytes = "hello world".getBytes(StandardCharsets.UTF_8);

      ByteBuffer content = ByteBuffer.wrap(bytes);
      response.write(true, content, callback);
      return true;
    }
  }
}
