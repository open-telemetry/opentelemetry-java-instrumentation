import akka.dispatch.forkjoin.ForkJoinTask;
import io.opentelemetry.auto.api.Trace;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class AkkaAsyncChild extends ForkJoinTask implements Runnable, Callable {
  private final AtomicBoolean blockThread;
  private final boolean doTraceableWork;
  private final CountDownLatch latch = new CountDownLatch(1);

  public AkkaAsyncChild() {
    this(true, false);
  }

  @Override
  public Object getRawResult() {
    return null;
  }

  @Override
  protected void setRawResult(final Object value) {}

  @Override
  protected boolean exec() {
    runImpl();
    return true;
  }

  public AkkaAsyncChild(final boolean doTraceableWork, final boolean blockThread) {
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

  public void waitForCompletion() throws InterruptedException {
    latch.await();
  }

  private void runImpl() {
    while (blockThread.get()) {
      // busy-wait to block thread
    }
    if (doTraceableWork) {
      asyncChild();
    }
    latch.countDown();
  }

  @Trace(operationName = "asyncChild")
  private void asyncChild() {}
}
