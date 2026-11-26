/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import io.opentelemetry.instrumentation.api.internal.GuardedBy;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The logger factory in effect before the agent selects a logging implementation. Records are kept
 * in a bounded buffer and replayed into the selected factory once it is known.
 *
 * <p>Loggers handed out by this factory keep working after initialization: they switch to a logger
 * from the selected factory. This matters because {@link PatchLogger} captures its {@link
 * InternalLogger} once, so a {@code static final} logger in a class loaded early would otherwise
 * stay disconnected for the life of the JVM.
 */
final class BufferingLoggerFactory implements InternalLogger.Factory {

  private final Object lock = new Object();
  private final int limit;

  @GuardedBy("lock")
  private final List<BufferedLog> buffer = new ArrayList<>();

  @GuardedBy("lock")
  private int dropped;

  @GuardedBy("lock")
  private boolean claimed;

  // written under lock, read without it on the fast path
  @Nullable private volatile InternalLogger.Factory target;

  BufferingLoggerFactory(int limit) {
    this.limit = limit;
  }

  @Override
  public InternalLogger create(String name) {
    InternalLogger.Factory target = this.target;
    if (target != null) {
      return target.create(name);
    }
    return new BufferingLogger(name);
  }

  /** Returns {@code false} if another caller has already started initialization. */
  boolean claim() {
    synchronized (lock) {
      if (claimed) {
        return false;
      }
      claimed = true;
      return true;
    }
  }

  /**
   * Replays the buffer into {@code factory} and routes all later records there. Must be preceded by
   * a successful {@link #claim()}.
   */
  void drainTo(InternalLogger.Factory factory) {
    boolean drained = false;
    try {
      int droppedCount;
      while (true) {
        List<BufferedLog> batch;
        synchronized (lock) {
          if (buffer.isEmpty()) {
            // switching to the target in the same critical section as the empty check means no
            // record can be buffered after the last batch was taken
            target = factory;
            droppedCount = dropped;
            dropped = 0;
            break;
          }
          batch = new ArrayList<>(buffer);
          buffer.clear();
        }
        // logging outside the lock: the selected logger may load classes, and those may log
        // through loggers from this factory, which lands in the buffer and is picked up by the
        // next iteration, so ordering is kept
        for (BufferedLog log : batch) {
          InternalLogger logger = factory.create(log.name);
          if (logger.isLoggable(log.level)) {
            logger.log(log.level, log.message, log.error);
          }
        }
      }
      drained = true;
      if (droppedCount > 0) {
        factory
            .create(InternalLogger.class.getName())
            .log(InternalLogger.Level.WARN, droppedMessage(droppedCount), null);
      }
    } finally {
      if (!drained) {
        // the selected logger threw while replaying; don't leave later records stuck in the buffer
        synchronized (lock) {
          buffer.clear();
          dropped = 0;
          target = factory;
        }
      }
    }
  }

  /**
   * Prints buffered WARN and ERROR records to {@code out}. Does nothing once initialization has
   * started.
   */
  void dumpTo(PrintStream out) {
    List<BufferedLog> copy;
    int droppedCount;
    synchronized (lock) {
      if (claimed) {
        return;
      }
      copy = new ArrayList<>(buffer);
      buffer.clear();
      droppedCount = dropped;
      dropped = 0;
    }
    for (BufferedLog log : copy) {
      if (log.level == InternalLogger.Level.ERROR || log.level == InternalLogger.Level.WARN) {
        out.println("[otel.javaagent] " + log.level + " " + log.name + " - " + log.message);
        if (log.error != null) {
          log.error.printStackTrace(out);
        }
      }
    }
    // dropped records may have included warnings or errors, so always say that some were lost
    if (droppedCount > 0) {
      out.println(
          "[otel.javaagent] WARN "
              + InternalLogger.class.getName()
              + " - "
              + droppedMessage(droppedCount));
    }
  }

  private String droppedMessage(int droppedCount) {
    return "Dropped "
        + droppedCount
        + " log records emitted before logging was initialized (buffer limit "
        + limit
        + ")";
  }

  /**
   * Buffers the record, or returns the factory to log it to if initialization finished in the
   * meantime.
   */
  @Nullable
  private InternalLogger.Factory bufferOrGetTarget(BufferedLog log) {
    synchronized (lock) {
      InternalLogger.Factory target = this.target;
      if (target != null) {
        return target;
      }
      if (buffer.size() >= limit) {
        dropped++;
      } else {
        buffer.add(log);
      }
      return null;
    }
  }

  private final class BufferingLogger implements InternalLogger {

    private final String name;
    @Nullable private volatile InternalLogger delegate;

    private BufferingLogger(String name) {
      this.name = name;
    }

    @Override
    public boolean isLoggable(InternalLogger.Level level) {
      InternalLogger delegate = resolveDelegate();
      // the selected logger's level is unknown yet; filtering happens when the buffer is replayed
      return delegate == null || delegate.isLoggable(level);
    }

    @Override
    public void log(InternalLogger.Level level, String message, @Nullable Throwable error) {
      InternalLogger delegate = resolveDelegate();
      if (delegate == null) {
        InternalLogger.Factory target =
            bufferOrGetTarget(new BufferedLog(name, level, message, error));
        if (target == null) {
          return;
        }
        delegate = target.create(name);
        this.delegate = delegate;
      }
      delegate.log(level, message, error);
    }

    @Override
    public String name() {
      return name;
    }

    @Nullable
    private InternalLogger resolveDelegate() {
      InternalLogger delegate = this.delegate;
      if (delegate != null) {
        return delegate;
      }
      InternalLogger.Factory target = BufferingLoggerFactory.this.target;
      if (target == null) {
        return null;
      }
      delegate = target.create(name);
      this.delegate = delegate;
      return delegate;
    }
  }

  private static final class BufferedLog {
    final String name;
    final InternalLogger.Level level;
    final String message;
    @Nullable final Throwable error;

    BufferedLog(
        String name, InternalLogger.Level level, String message, @Nullable Throwable error) {
      this.name = name;
      this.level = level;
      this.message = message;
      this.error = error;
    }
  }
}
