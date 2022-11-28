/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.scalaexecutors;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import scala.concurrent.forkjoin.ForkJoinPool;
import scala.concurrent.forkjoin.ForkJoinTask;

public class ForkJoinPoolBridge implements ExecutorService {

  private final ForkJoinPool delegate;

  public ForkJoinPoolBridge(ForkJoinPool delegate) {
    this.delegate = delegate;
  }

  @Override
  public void shutdown() {
    delegate.shutdown();
  }

  @NotNull
  @Override
  public List<Runnable> shutdownNow() {
    return delegate.shutdownNow();
  }

  @Override
  public boolean isShutdown() {
    return delegate.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return delegate.isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, @NotNull TimeUnit unit)
      throws InterruptedException {
    return delegate.awaitTermination(timeout, unit);
  }

  @NotNull
  @Override
  public <T> Future<T> submit(@NotNull Callable<T> task) {
    return delegate.submit(task);
  }

  @NotNull
  @Override
  public <T> Future<T> submit(@NotNull Runnable task, T result) {
    return delegate.submit(task, result);
  }

  @NotNull
  @Override
  public Future<?> submit(@NotNull Runnable task) {
    return delegate.submit(task);
  }

  @NotNull
  @Override
  public <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks) {
    return delegate.invokeAll(tasks);
  }

  @NotNull
  @Override
  public <T> List<Future<T>> invokeAll(
      @NotNull Collection<? extends Callable<T>> tasks, long timeout, @NotNull TimeUnit unit) {
    // Scala 2.8's ForkJoinPool doesn't have corresponding method
    return delegate.invokeAll(tasks);
  }

  @NotNull
  @Override
  public <T> T invokeAny(@NotNull Collection<? extends Callable<T>> tasks)
      throws InterruptedException, ExecutionException {
    // Scala 2.8's ForkJoinPool doesn't have corresponding method
    return delegate.submit(tasks.iterator().next()).get();
  }

  @Override
  public <T> T invokeAny(
      @NotNull Collection<? extends Callable<T>> tasks, long timeout, @NotNull TimeUnit unit)
      throws InterruptedException, ExecutionException {
    // Scala 2.8's ForkJoinPool doesn't have corresponding method
    return delegate.submit(tasks.iterator().next()).get();
  }

  public <T> T invoke(ForkJoinTask<T> task) {
    return delegate.invoke(task);
  }

  @Override
  public void execute(@NotNull Runnable command) {
    delegate.execute(command);
  }

  public <T> void execute(ForkJoinTask<T> task) {
    delegate.execute(task);
  }
}
