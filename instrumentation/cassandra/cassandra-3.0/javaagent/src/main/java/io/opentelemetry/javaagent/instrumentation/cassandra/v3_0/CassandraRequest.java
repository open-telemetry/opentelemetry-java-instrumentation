package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import com.datastax.driver.core.ExecutionInfo;
import com.datastax.driver.core.Session;

public final class CassandraRequest {

  private final Session session;
  private final String statement;
  // volatile is not needed here as this field is set and get from the same thread
  private ExecutionInfo executionInfo;

  public CassandraRequest(Session session, String statement) {
    this.session = session;
    this.statement = statement;
  }

  public Session getSession() {
    return session;
  }

  public String getStatement() {
    return statement;
  }

  public void setExecutionInfo(ExecutionInfo executionInfo) {
    this.executionInfo = executionInfo;
  }

  public ExecutionInfo getExecutionInfo() {
    return executionInfo;
  }
}
