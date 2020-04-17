package datadog.opentracing.jfr.openjdk;

import datadog.opentracing.DDSpanContext;
import datadog.opentracing.jfr.DDScopeEvent;
import datadog.trace.common.util.ThreadCpuTimeAccess;
import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Timespan;

@Name("datadog.Scope")
@Label("Scope")
@Description("Datadog event corresponding to a scope.")
@Category("Datadog")
@StackTrace(false)
public final class ScopeEvent extends Event implements DDScopeEvent {

  private static final int IDS_RADIX = 16;

  private final transient DDSpanContext spanContext;

  @Label("Trace Id")
  private String traceId;

  @Label("Span Id")
  private String spanId;

  @Label("Parent Id")
  private String parentId;

  @Label("Service Name")
  private String serviceName;

  @Label("Resource Name")
  private String resourceName;

  @Label("Operation Name")
  private String operationName;

  @Label("Thread CPU Time")
  @Timespan
  // does not need to be volatile since the event is created and committed from the same thread
  private long cpuTime = 0L;

  ScopeEvent(final DDSpanContext spanContext) {
    this.spanContext = spanContext;
  }

  @Override
  public void start() {
    if (isEnabled()) {
      cpuTime = ThreadCpuTimeAccess.getCurrentThreadCpuTime();
      begin();
    }
  }

  @Override
  public void finish() {
    end();
    if (shouldCommit()) {
      if (cpuTime > 0) {
        cpuTime = ThreadCpuTimeAccess.getCurrentThreadCpuTime() - cpuTime;
      }
      traceId = spanContext.getTraceId().toString(IDS_RADIX);
      spanId = spanContext.getSpanId().toString(IDS_RADIX);
      parentId = spanContext.getParentId().toString(IDS_RADIX);
      serviceName = spanContext.getServiceName();
      resourceName = spanContext.getResourceName();
      operationName = spanContext.getOperationName();
      commit();
    }
  }
}
