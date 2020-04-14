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
package io.opentelemetry.auto.common.exec;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class CommonTaskExecutor extends AbstractExecutorService {
  public static final CommonTaskExecutor INSTANCE = new CommonTaskExecutor();
  private static final long SHUTDOWN_WAIT_SECONDS = 5;

  private final ScheduledExecutorService executorService =
      Executors.newSingleThreadScheduledExecutor(DaemonThreadFactory.TASK_SCHEDULER);

  private CommonTaskExecutor() {
    try {
      Runtime.getRuntime().addShutdownHook(new ShutdownCallback(executorService));
    } catch (final IllegalStateException ex) {
      // The JVM is already shutting down.
      log.debug("Error adding shutdown hook", ex);
    }
  }

  public ScheduledFuture<?> scheduleAtFixedRate(
      final Runnable command, final long initialDelay, final long period, final TimeUnit unit) {
    return executorService.scheduleAtFixedRate(command, initialDelay, period, unit);
  }

  @Override
  public void shutdown() {
    executorService.shutdown();
  }

  @Override
  public List<Runnable> shutdownNow() {
    return executorService.shutdownNow();
  }

  @Override
  public boolean isShutdown() {
    return executorService.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return executorService.isTerminated();
  }

  @Override
  public boolean awaitTermination(final long timeout, final TimeUnit unit)
      throws InterruptedException {
    return executorService.awaitTermination(timeout, unit);
  }

  @Override
  public void execute(final Runnable command) {
    executorService.execute(command);
  }

  private static final class ShutdownCallback extends Thread {

    private final ScheduledExecutorService executorService;

    private ShutdownCallback(final ScheduledExecutorService executorService) {
      super("opentelemetry-exec-shutdown-hook");
      this.executorService = executorService;
    }

    @Override
    public void run() {
      executorService.shutdown();
      try {
        if (!executorService.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
          executorService.shutdownNow();
        }
      } catch (final InterruptedException e) {
        executorService.shutdownNow();
      }
    }
  }
}
