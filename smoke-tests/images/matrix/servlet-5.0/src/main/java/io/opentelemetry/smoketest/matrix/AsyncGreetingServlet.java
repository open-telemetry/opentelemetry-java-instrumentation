/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.matrix;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class AsyncGreetingServlet extends GreetingServlet {
  private static final BlockingQueue<AsyncContext> jobQueue = new LinkedBlockingQueue<>();
  private static final ExecutorService executor = Executors.newFixedThreadPool(2);

  @Override
  public void init() throws ServletException {
    executor.submit(
        new Runnable() {
          @Override
          public void run() {
            try {
              while (true) {
                AsyncContext ac = jobQueue.take();
                executor.submit(() -> handleRequest(ac));
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }
        });
  }

  @Override
  public void destroy() {
    executor.shutdownNow();
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
    AsyncContext ac = req.startAsync(req, resp);
    jobQueue.add(ac);
  }

  private void handleRequest(AsyncContext ac) {
    ac.dispatch("/greeting");
  }
}
