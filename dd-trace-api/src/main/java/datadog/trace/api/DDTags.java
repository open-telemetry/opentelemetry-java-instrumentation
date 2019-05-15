package datadog.trace.api;

public class DDTags {
  public static final String SPAN_TYPE = "span.type";
  public static final String SERVICE_NAME = "service.name";
  public static final String RESOURCE_NAME = "resource.name";
  public static final String THREAD_NAME = "thread.name";
  public static final String THREAD_ID = "thread.id";
  public static final String DB_STATEMENT = "sql.query";

  public static final String USER_NAME = "user.principal";

  public static final String ERROR_MSG = "error.msg"; // string representing the error message
  public static final String ERROR_TYPE = "error.type"; // string representing the type of the error
  public static final String ERROR_STACK = "error.stack"; // human readable version of the stack

  public static final String ANALYTICS_SAMPLE_RATE = "_dd1.sr.eausr";
  @Deprecated public static final String EVENT_SAMPLE_RATE = ANALYTICS_SAMPLE_RATE;

  /** Manually force tracer to be keep the trace */
  public static final String MANUAL_KEEP = "manual.keep";
  /** Manually force tracer to be drop the trace */
  public static final String MANUAL_DROP = "manual.drop";
}
