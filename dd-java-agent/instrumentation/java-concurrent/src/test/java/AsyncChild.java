import datadog.trace.api.Trace;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncChild implements Runnable, Callable {
  private final AtomicBoolean blockThread;
  private final boolean doTraceableWork;

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
    while (blockThread.get()) {
      // busy-wait to block thread
    }
  }

  @Trace(operationName = "asyncChild")
  private void asyncChild() {}
}
