package datadog.trace.instrumentation.api;

// standard tag names (and span kind values) from OpenTracing (see io.opentracing.tag.Tags)
public class Tags {

  public static final String SPAN_KIND_SERVER = "server";
  public static final String SPAN_KIND_CLIENT = "client";
  public static final String SPAN_KIND_PRODUCER = "producer";
  public static final String SPAN_KIND_CONSUMER = "consumer";

  public static final String HTTP_URL = "http.url";
  public static final String HTTP_STATUS = "http.status_code";
  public static final String HTTP_METHOD = "http.method";
  public static final String PEER_HOST_IPV4 = "peer.ipv4";
  public static final String PEER_HOST_IPV6 = "peer.ipv6";
  public static final String PEER_SERVICE = "peer.service";
  public static final String PEER_HOSTNAME = "peer.hostname";
  public static final String PEER_PORT = "peer.port";
  public static final String SAMPLING_PRIORITY = "sampling.priority";
  public static final String SPAN_KIND = "span.kind";
  public static final String COMPONENT = "component";
  public static final String ERROR = "error";
  public static final String DB_TYPE = "db.type";
  public static final String DB_INSTANCE = "db.instance";
  public static final String DB_USER = "db.user";
  public static final String DB_STATEMENT = "db.statement";
}
