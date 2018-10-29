import datadog.trace.api.Trace;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AsyncChild implements Runnable, Callable {
  private final AtomicBoolean blockThread;
  private final boolean doTraceableWork;
  private final AtomicInteger numberOfWorkers = new AtomicInteger(0);

  public AsyncChild() {
    this(true, false);
  }

  public AsyncChild(final boolean doTraceableWork, final boolean blockThread) {
    this.doTraceableWork = doTraceableWork;
    this.blockThread = new AtomicBoolean(blockThread);
  }

  public void unblock() {
    blockThread.set(false);
  }

  @Override
  public void run() {
    runImpl();
  }

  @Override
  public Object call() throws Exception {
    runImpl();
    return null;
  }

  private void runImpl() {
    if (doTraceableWork) {
      asyncChild();
    }
    numberOfWorkers.getAndIncrement();
    try {
      while (blockThread.get()) {
        // busy-wait to block thread
      }
    } finally {
      numberOfWorkers.getAndDecrement();
    }
  }

  @Trace(operationName = "asyncChild")
  private void asyncChild() {}
}
