package datadog.trace.api;

public class DDSpanTypes {
  public static final String HTTP_CLIENT = "http";
  public static final String WEB_SERVLET = "web";

  public static final String SQL = "sql";
  public static final String MONGO = "mongodb";
  public static final String CASSANDRA = "cassandra";

  public static final String MESSAGE_CONSUMER = "queue";
  public static final String MESSAGE_PRODUCER = "queue";
}
