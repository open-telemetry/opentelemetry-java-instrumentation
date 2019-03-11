package datadog.trace.instrumentation.jdbc;

import datadog.trace.agent.decorator.DatabaseClientDecorator;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class JDBCDecorator extends DatabaseClientDecorator<JDBCMaps.DBInfo> {
  public static final JDBCDecorator DECORATE = new JDBCDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"jdbc"};
  }

  @Override
  protected String service() {
    return "jdbc"; // Overridden by onConnection
  }

  @Override
  protected String component() {
    return "java-jdbc"; // Overridden by onStatement and onPreparedStatement
  }

  @Override
  protected String spanType() {
    return DDSpanTypes.SQL;
  }

  @Override
  protected String dbType() {
    return "jdbc";
  }

  @Override
  protected String dbUser(final JDBCMaps.DBInfo info) {
    return info.getUser();
  }

  @Override
  protected String dbInstance(final JDBCMaps.DBInfo info) {
    return info.getUrl();
  }

  public Span onConnection(final Span span, final Connection connection) {
    JDBCMaps.DBInfo dbInfo = JDBCMaps.connectionInfo.get(connection);
    /**
     * Logic to get the DBInfo from a JDBC Connection, if the connection was never seen before, the
     * connectionInfo map will return null and will attempt to extract DBInfo from the connection.
     * If the DBInfo can't be extracted, then the connection will be stored with the DEFAULT DBInfo
     * as the value in the connectionInfo map to avoid retry overhead.
     */
    {
      if (dbInfo == null) {
        try {
          final DatabaseMetaData metaData = connection.getMetaData();
          final String url = metaData.getURL();
          if (url != null) {
            // Remove end of url to prevent passwords from leaking:
            final String sanitizedURL = url.replaceAll("[?;].*", "");
            final String type = url.split(":", -1)[1];
            String user = metaData.getUserName();
            if (user != null && user.trim().equals("")) {
              user = null;
            }
            dbInfo = new JDBCMaps.DBInfo(sanitizedURL, type, user);
          } else {
            dbInfo = JDBCMaps.DBInfo.DEFAULT;
          }
        } catch (final SQLException se) {
          dbInfo = JDBCMaps.DBInfo.DEFAULT;
        }
        JDBCMaps.connectionInfo.put(connection, dbInfo);
      }
    }

    if (dbInfo != null) {
      Tags.DB_TYPE.set(span, dbInfo.getType());
      span.setTag(DDTags.SERVICE_NAME, dbInfo.getType());
    }
    return super.onConnection(span, dbInfo);
  }

  @Override
  public Span onStatement(final Span span, final String statement) {
    final String resourceName = statement == null ? JDBCMaps.DB_QUERY : statement;
    span.setTag(DDTags.RESOURCE_NAME, resourceName);
    Tags.COMPONENT.set(span, "java-jdbc-statement");
    return super.onStatement(span, statement);
  }

  public Span onPreparedStatement(final Span span, final PreparedStatement statement) {
    final String sql = JDBCMaps.preparedStatements.get(statement);
    final String resourceName = sql == null ? JDBCMaps.DB_QUERY : sql;
    span.setTag(DDTags.RESOURCE_NAME, resourceName);
    Tags.COMPONENT.set(span, "java-jdbc-prepared_statement");
    return super.onStatement(span, sql);
  }
}
