/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.typedspan;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Tracer;
import java.util.logging.Logger;

/**
 * <b>Required attributes:</b>
 *
 * <ul>
 *   <li>db.system: An identifier for the database management system (DBMS) product being used. See below for a list of well-known identifiers.
 *   <li>db.hbase.namespace: The [HBase namespace](https://hbase.apache.org/book.html#_namespace) being accessed. To be used instead of the generic `db.name` attribute.
 * </ul>
 *
 * <b>Conditional attributes:</b>
 *
 * <ul>
 *   <li>db.name: If no tech-specific attribute is defined, this attribute is used to report the name of the database being accessed. For commands that switch the database, this should be set to the target database (even if the command fails).
 *   <li>db.statement: The database statement being executed.
 *   <li>db.operation: The name of the operation being executed, e.g. the [MongoDB command name](https://docs.mongodb.com/manual/reference/command/#database-operations) such as `findAndModify`.
 *   <li>net.peer.name: Remote hostname or similar, see note below.
 *   <li>net.peer.ip: Remote address of the peer (dotted decimal for IPv4 or [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6)
 *   <li>net.peer.port: Remote port number.
 *   <li>net.transport: Transport protocol used. See note below.
 * </ul>
 *
 * <b>Additional constraints</b>
 *
 * <p>At least one of the following must be set:
 *
 * <ul>
 *   <li>net.peer.name
 *   <li>net.peer.ip
 * </ul>
 */
public class DbHbaseSpan extends DelegatingSpan implements DbHbaseSemanticConvention {

  enum AttributeStatus {
    EMPTY,
    DB_SYSTEM,
    DB_CONNECTION_STRING,
    DB_USER,
    DB_JDBC_DRIVER_CLASSNAME,
    DB_NAME,
    DB_STATEMENT,
    DB_OPERATION,
    NET_PEER_NAME,
    NET_PEER_IP,
    NET_PEER_PORT,
    NET_TRANSPORT,
    DB_HBASE_NAMESPACE;
    

    @SuppressWarnings("ImmutableEnumChecker")
    private long flag;

    AttributeStatus() {
      this.flag = 1L << this.ordinal();
    }

    public boolean isSet(AttributeStatus attribute) {
      return (this.flag & attribute.flag) > 0;
    }

    public void set(AttributeStatus attribute) {
      this.flag |= attribute.flag;
    }

    public void set(long attribute) {
      this.flag = attribute;
    }

    public long getValue() {
      return flag;
    }
  }

  @SuppressWarnings("unused")
  private static final Logger logger = Logger.getLogger(DbHbaseSpan.class.getName());
  public final AttributeStatus status;

  protected DbHbaseSpan(Span span, AttributeStatus status) {
    super(span);
    this.status = status;
  }

	/**
	 * Entry point to generate a {@link DbHbaseSpan}.
	 * @param tracer Tracer to use
	 * @param spanName Name for the {@link Span}
	 * @return a {@link DbHbaseSpan} object.
	 */
  public static DbHbaseSpanBuilder createDbHbaseSpanBuilder(Tracer tracer, String spanName) {
    return new DbHbaseSpanBuilder(tracer, spanName);
  }

  /**
	 * Creates a {@link DbHbaseSpan} from a {@link DbSpan}.
	 * @param builder {@link DbSpan.DbSpanBuilder} to use.
	 * @return a {@link DbHbaseSpan} object built from a {@link DbSpan}.
	 */
  public static DbHbaseSpanBuilder createDbHbaseSpanBuilder(DbSpan.DbSpanBuilder builder) {
	  // we accept a builder from Db since DbHbase "extends" Db
    return new DbHbaseSpanBuilder(builder.getSpanBuilder(), builder.status.getValue());
  }

  /** @return the Span used internally */
  @Override
  public Span getSpan() {
    return this.delegate;
  }

  /** Terminates the Span. Here there is the checking for required attributes. */
  @Override
  @SuppressWarnings("UnnecessaryParentheses")
  public void end() {
    delegate.end();

    // required attributes
    if (!this.status.isSet(AttributeStatus.DB_SYSTEM)) {
      logger.warning("Wrong usage - Span missing db.system attribute");
    }
    if (!this.status.isSet(AttributeStatus.DB_HBASE_NAMESPACE)) {
      logger.warning("Wrong usage - Span missing db.hbase.namespace attribute");
    }
    // extra constraints.
    {
      boolean flag =
        (!this.status.isSet(AttributeStatus.NET_PEER_NAME) ) ||
        (!this.status.isSet(AttributeStatus.NET_PEER_IP) ) ;
      if (flag) {
        logger.info("Constraint not respected!");
      }
    }
    // conditional attributes
    if (!this.status.isSet(AttributeStatus.DB_NAME)) {
      logger.info("WARNING! Missing db.name attribute!");
    }
    if (!this.status.isSet(AttributeStatus.DB_STATEMENT)) {
      logger.info("WARNING! Missing db.statement attribute!");
    }
    if (!this.status.isSet(AttributeStatus.DB_OPERATION)) {
      logger.info("WARNING! Missing db.operation attribute!");
    }
    if (!this.status.isSet(AttributeStatus.NET_PEER_NAME)) {
      logger.info("WARNING! Missing net.peer.name attribute!");
    }
    if (!this.status.isSet(AttributeStatus.NET_PEER_IP)) {
      logger.info("WARNING! Missing net.peer.ip attribute!");
    }
    if (!this.status.isSet(AttributeStatus.NET_PEER_PORT)) {
      logger.info("WARNING! Missing net.peer.port attribute!");
    }
    if (!this.status.isSet(AttributeStatus.NET_TRANSPORT)) {
      logger.info("WARNING! Missing net.transport attribute!");
    }
  }


  /**
   * Sets db.system.
   * @param dbSystem An identifier for the database management system (DBMS) product being used. See below for a list of well-known identifiers.
   */
  @Override
  public DbHbaseSemanticConvention setDbSystem(String dbSystem) {
    status.set(AttributeStatus.DB_SYSTEM);
    delegate.setAttribute("db.system", dbSystem);
    return this;
  }

  /**
   * Sets db.connection_string.
   * @param dbConnectionString The connection string used to connect to the database.
   * <p> It is recommended to remove embedded credentials.
   */
  @Override
  public DbHbaseSemanticConvention setDbConnectionString(String dbConnectionString) {
    status.set(AttributeStatus.DB_CONNECTION_STRING);
    delegate.setAttribute("db.connection_string", dbConnectionString);
    return this;
  }

  /**
   * Sets db.user.
   * @param dbUser Username for accessing the database.
   */
  @Override
  public DbHbaseSemanticConvention setDbUser(String dbUser) {
    status.set(AttributeStatus.DB_USER);
    delegate.setAttribute("db.user", dbUser);
    return this;
  }

  /**
   * Sets db.jdbc.driver_classname.
   * @param dbJdbcDriverClassname The fully-qualified class name of the [Java Database Connectivity (JDBC)](https://docs.oracle.com/javase/8/docs/technotes/guides/jdbc/) driver used to connect.
   */
  @Override
  public DbHbaseSemanticConvention setDbJdbcDriverClassname(String dbJdbcDriverClassname) {
    status.set(AttributeStatus.DB_JDBC_DRIVER_CLASSNAME);
    delegate.setAttribute("db.jdbc.driver_classname", dbJdbcDriverClassname);
    return this;
  }

  /**
   * Sets db.name.
   * @param dbName If no tech-specific attribute is defined, this attribute is used to report the name of the database being accessed. For commands that switch the database, this should be set to the target database (even if the command fails).
   * <p> In some SQL databases, the database name to be used is called &#34;schema name&#34;.
   */
  @Override
  public DbHbaseSemanticConvention setDbName(String dbName) {
    status.set(AttributeStatus.DB_NAME);
    delegate.setAttribute("db.name", dbName);
    return this;
  }

  /**
   * Sets db.statement.
   * @param dbStatement The database statement being executed.
   * <p> The value may be sanitized to exclude sensitive information.
   */
  @Override
  public DbHbaseSemanticConvention setDbStatement(String dbStatement) {
    status.set(AttributeStatus.DB_STATEMENT);
    delegate.setAttribute("db.statement", dbStatement);
    return this;
  }

  /**
   * Sets db.operation.
   * @param dbOperation The name of the operation being executed, e.g. the [MongoDB command name](https://docs.mongodb.com/manual/reference/command/#database-operations) such as `findAndModify`.
   * <p> While it would semantically make sense to set this, e.g., to a SQL keyword like `SELECT` or `INSERT`, it is not recommended to attempt any client-side parsing of `db.statement` just to get this property (the back end can do that if required).
   */
  @Override
  public DbHbaseSemanticConvention setDbOperation(String dbOperation) {
    status.set(AttributeStatus.DB_OPERATION);
    delegate.setAttribute("db.operation", dbOperation);
    return this;
  }

  /**
   * Sets net.peer.name.
   * @param netPeerName Remote hostname or similar, see note below.
   */
  @Override
  public DbHbaseSemanticConvention setNetPeerName(String netPeerName) {
    status.set(AttributeStatus.NET_PEER_NAME);
    delegate.setAttribute("net.peer.name", netPeerName);
    return this;
  }

  /**
   * Sets net.peer.ip.
   * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
   */
  @Override
  public DbHbaseSemanticConvention setNetPeerIp(String netPeerIp) {
    status.set(AttributeStatus.NET_PEER_IP);
    delegate.setAttribute("net.peer.ip", netPeerIp);
    return this;
  }

  /**
   * Sets net.peer.port.
   * @param netPeerPort Remote port number.
   */
  @Override
  public DbHbaseSemanticConvention setNetPeerPort(long netPeerPort) {
    status.set(AttributeStatus.NET_PEER_PORT);
    delegate.setAttribute("net.peer.port", netPeerPort);
    return this;
  }

  /**
   * Sets net.transport.
   * @param netTransport Transport protocol used. See note below.
   */
  @Override
  public DbHbaseSemanticConvention setNetTransport(String netTransport) {
    status.set(AttributeStatus.NET_TRANSPORT);
    delegate.setAttribute("net.transport", netTransport);
    return this;
  }

  /**
   * Sets db.hbase.namespace.
   * @param dbHbaseNamespace The [HBase namespace](https://hbase.apache.org/book.html#_namespace) being accessed. To be used instead of the generic `db.name` attribute.
   */
  @Override
  public DbHbaseSemanticConvention setDbHbaseNamespace(String dbHbaseNamespace) {
    status.set(AttributeStatus.DB_HBASE_NAMESPACE);
    delegate.setAttribute("db.hbase.namespace", dbHbaseNamespace);
    return this;
  }


	/**
	 * Builder class for {@link DbHbaseSpan}.
	 */
	public static class DbHbaseSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Span.Builder internalBuilder;
    protected AttributeStatus status = AttributeStatus.EMPTY;

    protected DbHbaseSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public DbHbaseSpanBuilder(Span.Builder spanBuilder, long attributes) {
      this.internalBuilder = spanBuilder;
      this.status.set(attributes);
    }

    public Span.Builder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** sets the {@link Span} parent. */
    public DbHbaseSpanBuilder setParent(Span parent){
      this.internalBuilder.setParent(parent);
      return this;
    }

    /** sets the {@link Span} parent. */
    public DbHbaseSpanBuilder setParent(SpanContext remoteParent){
      this.internalBuilder.setParent(remoteParent);
      return this;
    }

    /** this method sets the type of the {@link Span} is only available in the builder. */
    public DbHbaseSpanBuilder setKind(Span.Kind kind) {
      internalBuilder.setSpanKind(kind);
      return this;
    }

    /** starts the span */
    public DbHbaseSpan start() {
      // check for sampling relevant field here, but there are none.
      return new DbHbaseSpan(this.internalBuilder.startSpan(), status);
    }

    
    /**
     * Sets db.system.
     * @param dbSystem An identifier for the database management system (DBMS) product being used. See below for a list of well-known identifiers.
     */
    public DbHbaseSpanBuilder setDbSystem(String dbSystem) {
      status.set(AttributeStatus.DB_SYSTEM);
      internalBuilder.setAttribute("db.system", dbSystem);
      return this;
    }

    /**
     * Sets db.connection_string.
     * @param dbConnectionString The connection string used to connect to the database.
     * <p> It is recommended to remove embedded credentials.
     */
    public DbHbaseSpanBuilder setDbConnectionString(String dbConnectionString) {
      status.set(AttributeStatus.DB_CONNECTION_STRING);
      internalBuilder.setAttribute("db.connection_string", dbConnectionString);
      return this;
    }

    /**
     * Sets db.user.
     * @param dbUser Username for accessing the database.
     */
    public DbHbaseSpanBuilder setDbUser(String dbUser) {
      status.set(AttributeStatus.DB_USER);
      internalBuilder.setAttribute("db.user", dbUser);
      return this;
    }

    /**
     * Sets db.jdbc.driver_classname.
     * @param dbJdbcDriverClassname The fully-qualified class name of the [Java Database Connectivity (JDBC)](https://docs.oracle.com/javase/8/docs/technotes/guides/jdbc/) driver used to connect.
     */
    public DbHbaseSpanBuilder setDbJdbcDriverClassname(String dbJdbcDriverClassname) {
      status.set(AttributeStatus.DB_JDBC_DRIVER_CLASSNAME);
      internalBuilder.setAttribute("db.jdbc.driver_classname", dbJdbcDriverClassname);
      return this;
    }

    /**
     * Sets db.name.
     * @param dbName If no tech-specific attribute is defined, this attribute is used to report the name of the database being accessed. For commands that switch the database, this should be set to the target database (even if the command fails).
     * <p> In some SQL databases, the database name to be used is called &#34;schema name&#34;.
     */
    public DbHbaseSpanBuilder setDbName(String dbName) {
      status.set(AttributeStatus.DB_NAME);
      internalBuilder.setAttribute("db.name", dbName);
      return this;
    }

    /**
     * Sets db.statement.
     * @param dbStatement The database statement being executed.
     * <p> The value may be sanitized to exclude sensitive information.
     */
    public DbHbaseSpanBuilder setDbStatement(String dbStatement) {
      status.set(AttributeStatus.DB_STATEMENT);
      internalBuilder.setAttribute("db.statement", dbStatement);
      return this;
    }

    /**
     * Sets db.operation.
     * @param dbOperation The name of the operation being executed, e.g. the [MongoDB command name](https://docs.mongodb.com/manual/reference/command/#database-operations) such as `findAndModify`.
     * <p> While it would semantically make sense to set this, e.g., to a SQL keyword like `SELECT` or `INSERT`, it is not recommended to attempt any client-side parsing of `db.statement` just to get this property (the back end can do that if required).
     */
    public DbHbaseSpanBuilder setDbOperation(String dbOperation) {
      status.set(AttributeStatus.DB_OPERATION);
      internalBuilder.setAttribute("db.operation", dbOperation);
      return this;
    }

    /**
     * Sets net.peer.name.
     * @param netPeerName Remote hostname or similar, see note below.
     */
    public DbHbaseSpanBuilder setNetPeerName(String netPeerName) {
      status.set(AttributeStatus.NET_PEER_NAME);
      internalBuilder.setAttribute("net.peer.name", netPeerName);
      return this;
    }

    /**
     * Sets net.peer.ip.
     * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
     */
    public DbHbaseSpanBuilder setNetPeerIp(String netPeerIp) {
      status.set(AttributeStatus.NET_PEER_IP);
      internalBuilder.setAttribute("net.peer.ip", netPeerIp);
      return this;
    }

    /**
     * Sets net.peer.port.
     * @param netPeerPort Remote port number.
     */
    public DbHbaseSpanBuilder setNetPeerPort(long netPeerPort) {
      status.set(AttributeStatus.NET_PEER_PORT);
      internalBuilder.setAttribute("net.peer.port", netPeerPort);
      return this;
    }

    /**
     * Sets net.transport.
     * @param netTransport Transport protocol used. See note below.
     */
    public DbHbaseSpanBuilder setNetTransport(String netTransport) {
      status.set(AttributeStatus.NET_TRANSPORT);
      internalBuilder.setAttribute("net.transport", netTransport);
      return this;
    }

    /**
     * Sets db.hbase.namespace.
     * @param dbHbaseNamespace The [HBase namespace](https://hbase.apache.org/book.html#_namespace) being accessed. To be used instead of the generic `db.name` attribute.
     */
    public DbHbaseSpanBuilder setDbHbaseNamespace(String dbHbaseNamespace) {
      status.set(AttributeStatus.DB_HBASE_NAMESPACE);
      internalBuilder.setAttribute("db.hbase.namespace", dbHbaseNamespace);
      return this;
    }

  }
}