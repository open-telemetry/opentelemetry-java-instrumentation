package datadog.trace.api;

public class DDSpanTypes {
  public static final String HTTP_CLIENT = "http";
  public static final String HTTP_SERVER = "web";
  @Deprecated public static final String WEB_SERVLET = HTTP_SERVER;
  public static final String RPC = "rpc";
  public static final String CACHE = "cache";

  public static final String SQL = "sql";
  public static final String MONGO = "mongodb";
  public static final String CASSANDRA = "cassandra";
  public static final String COUCHBASE = "db"; // Using generic for now.
  public static final String REDIS = "redis";
  public static final String MEMCACHED = "memcached";
  public static final String ELASTICSEARCH = "elasticsearch";
  public static final String HIBERNATE = "hibernate";

  public static final String MESSAGE_CLIENT = "queue";
  public static final String MESSAGE_CONSUMER = "queue";
  public static final String MESSAGE_PRODUCER = "queue";
}
