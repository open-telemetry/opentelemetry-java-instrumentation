/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.typedspan;

import static io.opentelemetry.trace.attributes.SemanticAttributes.*;

import io.grpc.Context;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

public class DbRedisSpan extends DelegatingSpan implements DbRedisSemanticConvention {

  protected DbRedisSpan(Span span) {
    super(span);
  }

  /**
   * Entry point to generate a {@link DbRedisSpan}.
   *
   * @param tracer Tracer to use
   * @param spanName Name for the {@link Span}
   * @return a {@link DbRedisSpan} object.
   */
  public static DbRedisSpanBuilder createDbRedisSpan(Tracer tracer, String spanName) {
    return new DbRedisSpanBuilder(tracer, spanName);
  }

  /**
   * Creates a {@link DbRedisSpan} from a {@link DbSpan}.
   *
   * @param builder {@link DbSpan.DbSpanBuilder} to use.
   * @return a {@link DbRedisSpan} object built from a {@link DbSpan}.
   */
  public static DbRedisSpanBuilder createDbRedisSpan(DbSpan.DbSpanBuilder builder) {
    // we accept a builder from Db since DbRedis "extends" Db
    return new DbRedisSpanBuilder(builder.getSpanBuilder());
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
  public DbRedisSemanticConvention setDbSystem(String dbSystem) {
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
  public DbRedisSemanticConvention setDbConnectionString(String dbConnectionString) {
    delegate.setAttribute("db.connection_string", dbConnectionString);
    return this;
  }

  /**
   * Sets db.user.
   *
   * @param dbUser Username for accessing the database.
   */
  @Override
  public DbRedisSemanticConvention setDbUser(String dbUser) {
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
  public DbRedisSemanticConvention setDbJdbcDriverClassname(String dbJdbcDriverClassname) {
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
  public DbRedisSemanticConvention setDbName(String dbName) {
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
  public DbRedisSemanticConvention setDbStatement(String dbStatement) {
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
  public DbRedisSemanticConvention setDbOperation(String dbOperation) {
    delegate.setAttribute("db.operation", dbOperation);
    return this;
  }

  /**
   * Sets net.peer.name.
   *
   * @param netPeerName Remote hostname or similar, see note below.
   */
  @Override
  public DbRedisSemanticConvention setNetPeerName(String netPeerName) {
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
  public DbRedisSemanticConvention setNetPeerIp(String netPeerIp) {
    delegate.setAttribute(NET_PEER_IP, netPeerIp);
    return this;
  }

  /**
   * Sets net.peer.port.
   *
   * @param netPeerPort Remote port number.
   */
  @Override
  public DbRedisSemanticConvention setNetPeerPort(long netPeerPort) {
    delegate.setAttribute(NET_PEER_PORT, netPeerPort);
    return this;
  }

  /**
   * Sets net.transport.
   *
   * @param netTransport Transport protocol used. See note below.
   */
  @Override
  public DbRedisSemanticConvention setNetTransport(String netTransport) {
    delegate.setAttribute(NET_TRANSPORT, netTransport);
    return this;
  }

  /**
   * Sets db.redis.database_index.
   *
   * @param dbRedisDatabaseIndex The index of the database being accessed as used in the [`SELECT`
   *     command](https://redis.io/commands/select), provided as an integer. To be used instead of
   *     the generic `db.name` attribute.
   */
  @Override
  public DbRedisSemanticConvention setDbRedisDatabaseIndex(long dbRedisDatabaseIndex) {
    delegate.setAttribute("db.redis.database_index", dbRedisDatabaseIndex);
    return this;
  }

  /** Builder class for {@link DbRedisSpan}. */
  public static class DbRedisSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Span.Builder internalBuilder;

    protected DbRedisSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public DbRedisSpanBuilder(Span.Builder spanBuilder) {
      this.internalBuilder = spanBuilder;
    }

    public Span.Builder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** sets the {@link Span} parent. */
    public DbRedisSpanBuilder setParent(Context context) {
      this.internalBuilder.setParent(context);
      return this;
    }

    /** this method sets the type of the {@link Span} is only available in the builder. */
    public DbRedisSpanBuilder setKind(Span.Kind kind) {
      internalBuilder.setSpanKind(kind);
      return this;
    }

    /** starts the span */
    public DbRedisSpan start() {
      // check for sampling relevant field here, but there are none.
      return new DbRedisSpan(this.internalBuilder.startSpan());
    }

    /**
     * Sets db.system.
     *
     * @param dbSystem An identifier for the database management system (DBMS) product being used.
     *     See below for a list of well-known identifiers.
     */
    public DbRedisSpanBuilder setDbSystem(String dbSystem) {
      internalBuilder.setAttribute("db.system", dbSystem);
      return this;
    }

    /**
     * Sets db.connection_string.
     *
     * @param dbConnectionString The connection string used to connect to the database.
     *     <p>It is recommended to remove embedded credentials.
     */
    public DbRedisSpanBuilder setDbConnectionString(String dbConnectionString) {
      internalBuilder.setAttribute("db.connection_string", dbConnectionString);
      return this;
    }

    /**
     * Sets db.user.
     *
     * @param dbUser Username for accessing the database.
     */
    public DbRedisSpanBuilder setDbUser(String dbUser) {
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
    public DbRedisSpanBuilder setDbJdbcDriverClassname(String dbJdbcDriverClassname) {
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
    public DbRedisSpanBuilder setDbName(String dbName) {
      internalBuilder.setAttribute("db.name", dbName);
      return this;
    }

    /**
     * Sets db.statement.
     *
     * @param dbStatement The database statement being executed.
     *     <p>The value may be sanitized to exclude sensitive information.
     */
    public DbRedisSpanBuilder setDbStatement(String dbStatement) {
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
    public DbRedisSpanBuilder setDbOperation(String dbOperation) {
      internalBuilder.setAttribute("db.operation", dbOperation);
      return this;
    }

    /**
     * Sets net.peer.name.
     *
     * @param netPeerName Remote hostname or similar, see note below.
     */
    public DbRedisSpanBuilder setNetPeerName(String netPeerName) {
      internalBuilder.setAttribute(NET_PEER_NAME, netPeerName);
      return this;
    }

    /**
     * Sets net.peer.ip.
     *
     * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or
     *     [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
     */
    public DbRedisSpanBuilder setNetPeerIp(String netPeerIp) {
      internalBuilder.setAttribute(NET_PEER_IP, netPeerIp);
      return this;
    }

    /**
     * Sets net.peer.port.
     *
     * @param netPeerPort Remote port number.
     */
    public DbRedisSpanBuilder setNetPeerPort(long netPeerPort) {
      internalBuilder.setAttribute(NET_PEER_PORT, netPeerPort);
      return this;
    }

    /**
     * Sets net.transport.
     *
     * @param netTransport Transport protocol used. See note below.
     */
    public DbRedisSpanBuilder setNetTransport(String netTransport) {
      internalBuilder.setAttribute(NET_TRANSPORT, netTransport);
      return this;
    }

    /**
     * Sets db.redis.database_index.
     *
     * @param dbRedisDatabaseIndex The index of the database being accessed as used in the [`SELECT`
     *     command](https://redis.io/commands/select), provided as an integer. To be used instead of
     *     the generic `db.name` attribute.
     */
    public DbRedisSpanBuilder setDbRedisDatabaseIndex(long dbRedisDatabaseIndex) {
      internalBuilder.setAttribute("db.redis.database_index", dbRedisDatabaseIndex);
      return this;
    }
  }
}
