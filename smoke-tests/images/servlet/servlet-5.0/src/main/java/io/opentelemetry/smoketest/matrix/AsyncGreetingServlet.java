/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.matrix;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

@SuppressWarnings("SystemOut")
public class AsyncGreetingServlet extends GreetingServlet {
  private static final long serialVersionUID = 1L;
  private static final BlockingQueue<AsyncContext> jobQueue = new LinkedBlockingQueue<>();
  private static final ExecutorService executor = Executors.newFixedThreadPool(2);

  @Override
  public void init() {
    System.err.println("init AsyncGreetingServlet");
    executor.submit(
        new Runnable() {
          @Override
          public void run() {
            try {
              while (true) {
                AsyncContext ac = jobQueue.take();
                System.err.println("got async request from queue");
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
    System.err.println("destroy AsyncGreetingServlet");
    executor.shutdownNow();
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
    System.err.println("start async request");
    AsyncContext ac = req.startAsync(req, resp);
    System.err.println("add async request to queue");
    jobQueue.add(ac);
    System.err.println("async request added to queue");
  }

  private static void handleRequest(AsyncContext ac) {
    System.err.println("dispatch async request");
    try {
      ac.dispatch("/greeting");
      System.err.println("async request dispatched");
    } catch (Throwable throwable) {
      System.err.println("dispatching async request failed");
      throwable.printStackTrace();
      throw throwable;
    }
  }
}
