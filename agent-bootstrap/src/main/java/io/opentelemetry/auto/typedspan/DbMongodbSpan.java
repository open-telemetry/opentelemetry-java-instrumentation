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
 *   <li>db.mongodb.collection: The collection being accessed within the database stated in `db.name`.
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
public class DbMongodbSpan extends DelegatingSpan implements DbMongodbSemanticConvention {

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
    DB_MONGODB_COLLECTION;
    

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
  private static final Logger logger = Logger.getLogger(DbMongodbSpan.class.getName());
  public final AttributeStatus status;

  protected DbMongodbSpan(Span span, AttributeStatus status) {
    super(span);
    this.status = status;
  }

	/**
	 * Entry point to generate a {@link DbMongodbSpan}.
	 * @param tracer Tracer to use
	 * @param spanName Name for the {@link Span}
	 * @return a {@link DbMongodbSpan} object.
	 */
  public static DbMongodbSpanBuilder createDbMongodbSpanBuilder(Tracer tracer, String spanName) {
    return new DbMongodbSpanBuilder(tracer, spanName);
  }

  /**
	 * Creates a {@link DbMongodbSpan} from a {@link DbSpan}.
	 * @param builder {@link DbSpan.DbSpanBuilder} to use.
	 * @return a {@link DbMongodbSpan} object built from a {@link DbSpan}.
	 */
  public static DbMongodbSpanBuilder createDbMongodbSpanBuilder(DbSpan.DbSpanBuilder builder) {
	  // we accept a builder from Db since DbMongodb "extends" Db
    return new DbMongodbSpanBuilder(builder.getSpanBuilder(), builder.status.getValue());
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
    if (!this.status.isSet(AttributeStatus.DB_MONGODB_COLLECTION)) {
      logger.warning("Wrong usage - Span missing db.mongodb.collection attribute");
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
  public DbMongodbSemanticConvention setDbSystem(String dbSystem) {
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
  public DbMongodbSemanticConvention setDbConnectionString(String dbConnectionString) {
    status.set(AttributeStatus.DB_CONNECTION_STRING);
    delegate.setAttribute("db.connection_string", dbConnectionString);
    return this;
  }

  /**
   * Sets db.user.
   * @param dbUser Username for accessing the database.
   */
  @Override
  public DbMongodbSemanticConvention setDbUser(String dbUser) {
    status.set(AttributeStatus.DB_USER);
    delegate.setAttribute("db.user", dbUser);
    return this;
  }

  /**
   * Sets db.jdbc.driver_classname.
   * @param dbJdbcDriverClassname The fully-qualified class name of the [Java Database Connectivity (JDBC)](https://docs.oracle.com/javase/8/docs/technotes/guides/jdbc/) driver used to connect.
   */
  @Override
  public DbMongodbSemanticConvention setDbJdbcDriverClassname(String dbJdbcDriverClassname) {
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
  public DbMongodbSemanticConvention setDbName(String dbName) {
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
  public DbMongodbSemanticConvention setDbStatement(String dbStatement) {
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
  public DbMongodbSemanticConvention setDbOperation(String dbOperation) {
    status.set(AttributeStatus.DB_OPERATION);
    delegate.setAttribute("db.operation", dbOperation);
    return this;
  }

  /**
   * Sets net.peer.name.
   * @param netPeerName Remote hostname or similar, see note below.
   */
  @Override
  public DbMongodbSemanticConvention setNetPeerName(String netPeerName) {
    status.set(AttributeStatus.NET_PEER_NAME);
    delegate.setAttribute("net.peer.name", netPeerName);
    return this;
  }

  /**
   * Sets net.peer.ip.
   * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
   */
  @Override
  public DbMongodbSemanticConvention setNetPeerIp(String netPeerIp) {
    status.set(AttributeStatus.NET_PEER_IP);
    delegate.setAttribute("net.peer.ip", netPeerIp);
    return this;
  }

  /**
   * Sets net.peer.port.
   * @param netPeerPort Remote port number.
   */
  @Override
  public DbMongodbSemanticConvention setNetPeerPort(long netPeerPort) {
    status.set(AttributeStatus.NET_PEER_PORT);
    delegate.setAttribute("net.peer.port", netPeerPort);
    return this;
  }

  /**
   * Sets net.transport.
   * @param netTransport Transport protocol used. See note below.
   */
  @Override
  public DbMongodbSemanticConvention setNetTransport(String netTransport) {
    status.set(AttributeStatus.NET_TRANSPORT);
    delegate.setAttribute("net.transport", netTransport);
    return this;
  }

  /**
   * Sets db.mongodb.collection.
   * @param dbMongodbCollection The collection being accessed within the database stated in `db.name`.
   */
  @Override
  public DbMongodbSemanticConvention setDbMongodbCollection(String dbMongodbCollection) {
    status.set(AttributeStatus.DB_MONGODB_COLLECTION);
    delegate.setAttribute("db.mongodb.collection", dbMongodbCollection);
    return this;
  }


	/**
	 * Builder class for {@link DbMongodbSpan}.
	 */
	public static class DbMongodbSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Span.Builder internalBuilder;
    protected AttributeStatus status = AttributeStatus.EMPTY;

    protected DbMongodbSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public DbMongodbSpanBuilder(Span.Builder spanBuilder, long attributes) {
      this.internalBuilder = spanBuilder;
      this.status.set(attributes);
    }

    public Span.Builder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** sets the {@link Span} parent. */
    public DbMongodbSpanBuilder setParent(Span parent){
      this.internalBuilder.setParent(parent);
      return this;
    }

    /** sets the {@link Span} parent. */
    public DbMongodbSpanBuilder setParent(SpanContext remoteParent){
      this.internalBuilder.setParent(remoteParent);
      return this;
    }

    /** this method sets the type of the {@link Span} is only available in the builder. */
    public DbMongodbSpanBuilder setKind(Span.Kind kind) {
      internalBuilder.setSpanKind(kind);
      return this;
    }

    /** starts the span */
    public DbMongodbSpan start() {
      // check for sampling relevant field here, but there are none.
      return new DbMongodbSpan(this.internalBuilder.startSpan(), status);
    }

    
    /**
     * Sets db.system.
     * @param dbSystem An identifier for the database management system (DBMS) product being used. See below for a list of well-known identifiers.
     */
    public DbMongodbSpanBuilder setDbSystem(String dbSystem) {
      status.set(AttributeStatus.DB_SYSTEM);
      internalBuilder.setAttribute("db.system", dbSystem);
      return this;
    }

    /**
     * Sets db.connection_string.
     * @param dbConnectionString The connection string used to connect to the database.
     * <p> It is recommended to remove embedded credentials.
     */
    public DbMongodbSpanBuilder setDbConnectionString(String dbConnectionString) {
      status.set(AttributeStatus.DB_CONNECTION_STRING);
      internalBuilder.setAttribute("db.connection_string", dbConnectionString);
      return this;
    }

    /**
     * Sets db.user.
     * @param dbUser Username for accessing the database.
     */
    public DbMongodbSpanBuilder setDbUser(String dbUser) {
      status.set(AttributeStatus.DB_USER);
      internalBuilder.setAttribute("db.user", dbUser);
      return this;
    }

    /**
     * Sets db.jdbc.driver_classname.
     * @param dbJdbcDriverClassname The fully-qualified class name of the [Java Database Connectivity (JDBC)](https://docs.oracle.com/javase/8/docs/technotes/guides/jdbc/) driver used to connect.
     */
    public DbMongodbSpanBuilder setDbJdbcDriverClassname(String dbJdbcDriverClassname) {
      status.set(AttributeStatus.DB_JDBC_DRIVER_CLASSNAME);
      internalBuilder.setAttribute("db.jdbc.driver_classname", dbJdbcDriverClassname);
      return this;
    }

    /**
     * Sets db.name.
     * @param dbName If no tech-specific attribute is defined, this attribute is used to report the name of the database being accessed. For commands that switch the database, this should be set to the target database (even if the command fails).
     * <p> In some SQL databases, the database name to be used is called &#34;schema name&#34;.
     */
    public DbMongodbSpanBuilder setDbName(String dbName) {
      status.set(AttributeStatus.DB_NAME);
      internalBuilder.setAttribute("db.name", dbName);
      return this;
    }

    /**
     * Sets db.statement.
     * @param dbStatement The database statement being executed.
     * <p> The value may be sanitized to exclude sensitive information.
     */
    public DbMongodbSpanBuilder setDbStatement(String dbStatement) {
      status.set(AttributeStatus.DB_STATEMENT);
      internalBuilder.setAttribute("db.statement", dbStatement);
      return this;
    }

    /**
     * Sets db.operation.
     * @param dbOperation The name of the operation being executed, e.g. the [MongoDB command name](https://docs.mongodb.com/manual/reference/command/#database-operations) such as `findAndModify`.
     * <p> While it would semantically make sense to set this, e.g., to a SQL keyword like `SELECT` or `INSERT`, it is not recommended to attempt any client-side parsing of `db.statement` just to get this property (the back end can do that if required).
     */
    public DbMongodbSpanBuilder setDbOperation(String dbOperation) {
      status.set(AttributeStatus.DB_OPERATION);
      internalBuilder.setAttribute("db.operation", dbOperation);
      return this;
    }

    /**
     * Sets net.peer.name.
     * @param netPeerName Remote hostname or similar, see note below.
     */
    public DbMongodbSpanBuilder setNetPeerName(String netPeerName) {
      status.set(AttributeStatus.NET_PEER_NAME);
      internalBuilder.setAttribute("net.peer.name", netPeerName);
      return this;
    }

    /**
     * Sets net.peer.ip.
     * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
     */
    public DbMongodbSpanBuilder setNetPeerIp(String netPeerIp) {
      status.set(AttributeStatus.NET_PEER_IP);
      internalBuilder.setAttribute("net.peer.ip", netPeerIp);
      return this;
    }

    /**
     * Sets net.peer.port.
     * @param netPeerPort Remote port number.
     */
    public DbMongodbSpanBuilder setNetPeerPort(long netPeerPort) {
      status.set(AttributeStatus.NET_PEER_PORT);
      internalBuilder.setAttribute("net.peer.port", netPeerPort);
      return this;
    }

    /**
     * Sets net.transport.
     * @param netTransport Transport protocol used. See note below.
     */
    public DbMongodbSpanBuilder setNetTransport(String netTransport) {
      status.set(AttributeStatus.NET_TRANSPORT);
      internalBuilder.setAttribute("net.transport", netTransport);
      return this;
    }

    /**
     * Sets db.mongodb.collection.
     * @param dbMongodbCollection The collection being accessed within the database stated in `db.name`.
     */
    public DbMongodbSpanBuilder setDbMongodbCollection(String dbMongodbCollection) {
      status.set(AttributeStatus.DB_MONGODB_COLLECTION);
      internalBuilder.setAttribute("db.mongodb.collection", dbMongodbCollection);
      return this;
    }

  }
}