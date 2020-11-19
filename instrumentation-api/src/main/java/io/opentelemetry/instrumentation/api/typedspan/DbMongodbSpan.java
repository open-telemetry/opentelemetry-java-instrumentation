/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.typedspan;

import static io.opentelemetry.api.trace.attributes.SemanticAttributes.NET_PEER_IP;
import static io.opentelemetry.api.trace.attributes.SemanticAttributes.NET_PEER_NAME;
import static io.opentelemetry.api.trace.attributes.SemanticAttributes.NET_PEER_PORT;
import static io.opentelemetry.api.trace.attributes.SemanticAttributes.NET_TRANSPORT;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

public class DbMongodbSpan extends DelegatingSpan implements DbMongodbSemanticConvention {

  protected DbMongodbSpan(Span span) {
    super(span);
  }

  /**
   * Entry point to generate a {@link DbMongodbSpan}.
   *
   * @param tracer Tracer to use
   * @param spanName Name for the {@link Span}
   * @return a {@link DbMongodbSpan} object.
   */
  public static DbMongodbSpanBuilder createDbMongodbSpan(Tracer tracer, String spanName) {
    return new DbMongodbSpanBuilder(tracer, spanName);
  }

  /**
   * Creates a {@link DbMongodbSpan} from a {@link DbSpan}.
   *
   * @param builder {@link DbSpan.DbSpanBuilder} to use.
   * @return a {@link DbMongodbSpan} object built from a {@link DbSpan}.
   */
  public static DbMongodbSpanBuilder createDbMongodbSpan(DbSpan.DbSpanBuilder builder) {
    // we accept a builder from Db since DbMongodb "extends" Db
    return new DbMongodbSpanBuilder(builder.getSpanBuilder());
  }

  /** @return the Span used internally */
  @Override
  public Span getSpan() {
    return this.delegate;
  }

  /** Terminates the Span. Here there is the checking for required attributes. */
  @Override
  public void end() {
    delegate.end();
  }

  /**
   * Sets db.system.
   *
   * @param dbSystem An identifier for the database management system (DBMS) product being used. See
   *     below for a list of well-known identifiers.
   */
  @Override
  public DbMongodbSemanticConvention setDbSystem(String dbSystem) {
    delegate.setAttribute("db.system", dbSystem);
    return this;
  }

  /**
   * Sets db.connection_string.
   *
   * @param dbConnectionString The connection string used to connect to the database.
   *     <p>It is recommended to remove embedded credentials.
   */
  @Override
  public DbMongodbSemanticConvention setDbConnectionString(String dbConnectionString) {
    delegate.setAttribute("db.connection_string", dbConnectionString);
    return this;
  }

  /**
   * Sets db.user.
   *
   * @param dbUser Username for accessing the database.
   */
  @Override
  public DbMongodbSemanticConvention setDbUser(String dbUser) {
    delegate.setAttribute("db.user", dbUser);
    return this;
  }

  /**
   * Sets db.jdbc.driver_classname.
   *
   * @param dbJdbcDriverClassname The fully-qualified class name of the [Java Database Connectivity
   *     (JDBC)](https://docs.oracle.com/javase/8/docs/technotes/guides/jdbc/) driver used to
   *     connect.
   */
  @Override
  public DbMongodbSemanticConvention setDbJdbcDriverClassname(String dbJdbcDriverClassname) {
    delegate.setAttribute("db.jdbc.driver_classname", dbJdbcDriverClassname);
    return this;
  }

  /**
   * Sets db.name.
   *
   * @param dbName If no tech-specific attribute is defined, this attribute is used to report the
   *     name of the database being accessed. For commands that switch the database, this should be
   *     set to the target database (even if the command fails).
   *     <p>In some SQL databases, the database name to be used is called "schema name".
   */
  @Override
  public DbMongodbSemanticConvention setDbName(String dbName) {
    delegate.setAttribute("db.name", dbName);
    return this;
  }

  /**
   * Sets db.statement.
   *
   * @param dbStatement The database statement being executed.
   *     <p>The value may be sanitized to exclude sensitive information.
   */
  @Override
  public DbMongodbSemanticConvention setDbStatement(String dbStatement) {
    delegate.setAttribute("db.statement", dbStatement);
    return this;
  }

  /**
   * Sets db.operation.
   *
   * @param dbOperation The name of the operation being executed, e.g. the [MongoDB command
   *     name](https://docs.mongodb.com/manual/reference/command/#database-operations) such as
   *     `findAndModify`.
   *     <p>While it would semantically make sense to set this, e.g., to a SQL keyword like `SELECT`
   *     or `INSERT`, it is not recommended to attempt any client-side parsing of `db.statement`
   *     just to get this property (the back end can do that if required).
   */
  @Override
  public DbMongodbSemanticConvention setDbOperation(String dbOperation) {
    delegate.setAttribute("db.operation", dbOperation);
    return this;
  }

  /**
   * Sets net.peer.name.
   *
   * @param netPeerName Remote hostname or similar, see note below.
   */
  @Override
  public DbMongodbSemanticConvention setNetPeerName(String netPeerName) {
    delegate.setAttribute(NET_PEER_NAME, netPeerName);
    return this;
  }

  /**
   * Sets net.peer.ip.
   *
   * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or
   *     [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
   */
  @Override
  public DbMongodbSemanticConvention setNetPeerIp(String netPeerIp) {
    delegate.setAttribute(NET_PEER_IP, netPeerIp);
    return this;
  }

  /**
   * Sets net.peer.port.
   *
   * @param netPeerPort Remote port number.
   */
  @Override
  public DbMongodbSemanticConvention setNetPeerPort(long netPeerPort) {
    delegate.setAttribute(NET_PEER_PORT, netPeerPort);
    return this;
  }

  /**
   * Sets net.transport.
   *
   * @param netTransport Transport protocol used. See note below.
   */
  @Override
  public DbMongodbSemanticConvention setNetTransport(String netTransport) {
    delegate.setAttribute(NET_TRANSPORT, netTransport);
    return this;
  }

  /**
   * Sets db.mongodb.collection.
   *
   * @param dbMongodbCollection The collection being accessed within the database stated in
   *     `db.name`.
   */
  @Override
  public DbMongodbSemanticConvention setDbMongodbCollection(String dbMongodbCollection) {
    delegate.setAttribute("db.mongodb.collection", dbMongodbCollection);
    return this;
  }

  /** Builder class for {@link DbMongodbSpan}. */
  public static class DbMongodbSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected SpanBuilder internalBuilder;

    protected DbMongodbSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public DbMongodbSpanBuilder(SpanBuilder spanBuilder) {
      this.internalBuilder = spanBuilder;
    }

    public SpanBuilder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** sets the {@link Span} parent. */
    public DbMongodbSpanBuilder setParent(Context context) {
      this.internalBuilder.setParent(context);
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
      return new DbMongodbSpan(this.internalBuilder.startSpan());
    }

    /**
     * Sets db.system.
     *
     * @param dbSystem An identifier for the database management system (DBMS) product being used.
     *     See below for a list of well-known identifiers.
     */
    public DbMongodbSpanBuilder setDbSystem(String dbSystem) {
      internalBuilder.setAttribute("db.system", dbSystem);
      return this;
    }

    /**
     * Sets db.connection_string.
     *
     * @param dbConnectionString The connection string used to connect to the database.
     *     <p>It is recommended to remove embedded credentials.
     */
    public DbMongodbSpanBuilder setDbConnectionString(String dbConnectionString) {
      internalBuilder.setAttribute("db.connection_string", dbConnectionString);
      return this;
    }

    /**
     * Sets db.user.
     *
     * @param dbUser Username for accessing the database.
     */
    public DbMongodbSpanBuilder setDbUser(String dbUser) {
      internalBuilder.setAttribute("db.user", dbUser);
      return this;
    }

    /**
     * Sets db.jdbc.driver_classname.
     *
     * @param dbJdbcDriverClassname The fully-qualified class name of the [Java Database
     *     Connectivity (JDBC)](https://docs.oracle.com/javase/8/docs/technotes/guides/jdbc/) driver
     *     used to connect.
     */
    public DbMongodbSpanBuilder setDbJdbcDriverClassname(String dbJdbcDriverClassname) {
      internalBuilder.setAttribute("db.jdbc.driver_classname", dbJdbcDriverClassname);
      return this;
    }

    /**
     * Sets db.name.
     *
     * @param dbName If no tech-specific attribute is defined, this attribute is used to report the
     *     name of the database being accessed. For commands that switch the database, this should
     *     be set to the target database (even if the command fails).
     *     <p>In some SQL databases, the database name to be used is called "schema name".
     */
    public DbMongodbSpanBuilder setDbName(String dbName) {
      internalBuilder.setAttribute("db.name", dbName);
      return this;
    }

    /**
     * Sets db.statement.
     *
     * @param dbStatement The database statement being executed.
     *     <p>The value may be sanitized to exclude sensitive information.
     */
    public DbMongodbSpanBuilder setDbStatement(String dbStatement) {
      internalBuilder.setAttribute("db.statement", dbStatement);
      return this;
    }

    /**
     * Sets db.operation.
     *
     * @param dbOperation The name of the operation being executed, e.g. the [MongoDB command
     *     name](https://docs.mongodb.com/manual/reference/command/#database-operations) such as
     *     `findAndModify`.
     *     <p>While it would semantically make sense to set this, e.g., to a SQL keyword like
     *     `SELECT` or `INSERT`, it is not recommended to attempt any client-side parsing of
     *     `db.statement` just to get this property (the back end can do that if required).
     */
    public DbMongodbSpanBuilder setDbOperation(String dbOperation) {
      internalBuilder.setAttribute("db.operation", dbOperation);
      return this;
    }

    /**
     * Sets net.peer.name.
     *
     * @param netPeerName Remote hostname or similar, see note below.
     */
    public DbMongodbSpanBuilder setNetPeerName(String netPeerName) {
      internalBuilder.setAttribute(NET_PEER_NAME, netPeerName);
      return this;
    }

    /**
     * Sets net.peer.ip.
     *
     * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or
     *     [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
     */
    public DbMongodbSpanBuilder setNetPeerIp(String netPeerIp) {
      internalBuilder.setAttribute(NET_PEER_IP, netPeerIp);
      return this;
    }

    /**
     * Sets net.peer.port.
     *
     * @param netPeerPort Remote port number.
     */
    public DbMongodbSpanBuilder setNetPeerPort(long netPeerPort) {
      internalBuilder.setAttribute(NET_PEER_PORT, netPeerPort);
      return this;
    }

    /**
     * Sets net.transport.
     *
     * @param netTransport Transport protocol used. See note below.
     */
    public DbMongodbSpanBuilder setNetTransport(String netTransport) {
      internalBuilder.setAttribute(NET_TRANSPORT, netTransport);
      return this;
    }

    /**
     * Sets db.mongodb.collection.
     *
     * @param dbMongodbCollection The collection being accessed within the database stated in
     *     `db.name`.
     */
    public DbMongodbSpanBuilder setDbMongodbCollection(String dbMongodbCollection) {
      internalBuilder.setAttribute("db.mongodb.collection", dbMongodbCollection);
      return this;
    }
  }
}
