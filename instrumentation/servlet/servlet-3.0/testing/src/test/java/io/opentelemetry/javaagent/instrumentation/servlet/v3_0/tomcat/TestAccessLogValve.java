/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.tomcat;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.servlet.ServletException;
import org.apache.catalina.AccessLog;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

// public, because it's loaded by reflection
public class TestAccessLogValve extends ValveBase implements AccessLog {

  public final List<Map.Entry<String, String>> getLoggedIds() {
    return loggedIds;
  }

  private final List<Map.Entry<String, String>> loggedIds = new ArrayList<>();

  public TestAccessLogValve() {
    super(true);
  }

  @Override
  public void log(Request request, Response response, long time) {
    if (request.getParameter("access-log") == null) {
      return;
    }

    synchronized (loggedIds) {
      loggedIds.add(
          new AbstractMap.SimpleEntry<>(
              request.getAttribute("trace_id").toString(),
              request.getAttribute("span_id").toString()));
      loggedIds.notifyAll();
    }
  }

  public void waitForLoggedIds(int expected) {
    long timeout = TimeUnit.SECONDS.toMillis(20);
    long startTime = System.currentTimeMillis();
    long endTime = startTime + timeout;
    long toWait = timeout;
    synchronized (loggedIds) {
      while (loggedIds.size() < expected && toWait > 0) {
        try {
          loggedIds.wait(toWait);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        toWait = endTime - System.currentTimeMillis();
      }

      if (toWait <= 0) {
        throw new RuntimeException(
            "Timeout waiting for " + expected + " access log ids, got " + loggedIds.size());
      }
    }
  }

  @Override
  public void setRequestAttributesEnabled(boolean requestAttributesEnabled) {}

  @Override
  public boolean getRequestAttributesEnabled() {
    return false;
  }

  @Override
  public void invoke(Request request, Response response) throws IOException, ServletException {
    getNext().invoke(request, response);
  }
}
