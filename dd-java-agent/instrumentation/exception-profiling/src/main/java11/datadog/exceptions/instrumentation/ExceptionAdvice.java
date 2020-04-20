package datadog.exceptions.instrumentation;

import com.datadog.profiling.exceptions.ExceptionProfiling;
import com.datadog.profiling.exceptions.ExceptionSampleEvent;
import net.bytebuddy.asm.Advice;

public class ExceptionAdvice {
  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void onExit(@Advice.This final Exception e) {
    /*
     * We may get into a situation when this is called before ExceptionProfiling had a chance
     * to fully initialize. So despite the fact that this returns static singleton this may
     * return null sometimes.
     */
    if (ExceptionProfiling.getInstance() == null) {
      return;
    }
    /*
     * JFR will assign the stacktrace depending on the place where the event is committed.
     * Therefore we need to commit the event here, right in the 'Exception' constructor
     */
    final ExceptionSampleEvent event = ExceptionProfiling.getInstance().process(e);
    if (event != null && event.shouldCommit()) {
      event.commit();
    }
  }
}
