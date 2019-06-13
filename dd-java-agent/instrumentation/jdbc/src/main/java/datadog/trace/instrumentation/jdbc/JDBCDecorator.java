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

public class JDBCDecorator extends DatabaseClientDecorator<DBInfo> {
  public static final JDBCDecorator DECORATE = new JDBCDecorator();

  private static final String DB_QUERY = "DB Query";

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
  protected String dbUser(final DBInfo info) {
    return info.getUser();
  }

  @Override
  protected String dbInstance(final DBInfo info) {
    if (info.getInstance() != null) {
      return info.getInstance();
    } else {
      return info.getDb();
    }
  }

  public Span onConnection(final Span span, final Connection connection) {
    DBInfo dbInfo = JDBCMaps.connectionInfo.get(connection);
    /**
     * Logic to get the DBInfo from a JDBC Connection, if the connection was not created via
     * Driver.connect, or it has never seen before, the connectionInfo map will return null and will
     * attempt to extract DBInfo from the connection. If the DBInfo can't be extracted, then the
     * connection will be stored with the DEFAULT DBInfo as the value in the connectionInfo map to
     * avoid retry overhead.
     */
    {
      if (dbInfo == null) {
        try {
          final DatabaseMetaData metaData = connection.getMetaData();
          final String url = metaData.getURL();
          if (url != null) {
            dbInfo = JDBCConnectionUrlParser.parse(url, connection.getClientInfo());
          } else {
            dbInfo = DBInfo.DEFAULT;
          }
        } catch (final SQLException se) {
          dbInfo = DBInfo.DEFAULT;
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
    final String resourceName = statement == null ? DB_QUERY : statement;
    span.setTag(DDTags.RESOURCE_NAME, resourceName);
    Tags.COMPONENT.set(span, "java-jdbc-statement");
    return super.onStatement(span, statement);
  }

  public Span onPreparedStatement(final Span span, final PreparedStatement statement) {
    final String sql = JDBCMaps.preparedStatements.get(statement);
    final String resourceName = sql == null ? DB_QUERY : sql;
    span.setTag(DDTags.RESOURCE_NAME, resourceName);
    Tags.COMPONENT.set(span, "java-jdbc-prepared_statement");
    return super.onStatement(span, sql);
  }
}
