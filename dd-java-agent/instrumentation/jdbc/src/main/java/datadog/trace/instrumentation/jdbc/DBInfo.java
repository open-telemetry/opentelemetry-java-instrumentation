package datadog.trace.instrumentation.jdbc;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(builderClassName = "Builder", toBuilder = true)
public class DBInfo {
  public static DBInfo DEFAULT = new Builder().type("database").build();
  private final String type;
  private final String subtype;
  private final String url;
  private final String user;
  private final String instance;
  private final String db;
  private final String host;
  private final Integer port;
}
