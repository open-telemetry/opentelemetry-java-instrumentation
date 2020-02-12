package com.datadog.profiling.util;

import java.util.concurrent.ThreadFactory;

// FIXME: we should unify all thread factories in common library
public final class ProfilingThreadFactory implements ThreadFactory {
  private static final ThreadGroup THREAD_GROUP = new ThreadGroup("Datadog Profiler");

  private final String name;

  public ProfilingThreadFactory(final String name) {
    this.name = name;
  }

  @Override
  public Thread newThread(final Runnable r) {
    final Thread t = new Thread(THREAD_GROUP, r, name);
    t.setDaemon(true);
    return t;
  }
}
