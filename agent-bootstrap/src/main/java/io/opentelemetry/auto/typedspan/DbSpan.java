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

public class DbSpan extends DelegatingSpan implements DbSemanticConvention {

  protected DbSpan(Span span) {
    super(span);
  }

  /**
   * Entry point to generate a {@link DbSpan}.
   *
   * @param tracer Tracer to use
   * @param spanName Name for the {@link Span}
   * @return a {@link DbSpan} object.
   */
  public static DbSpanBuilder createDbSpan(Tracer tracer, String spanName) {
    return new DbSpanBuilder(tracer, spanName).setKind(Span.Kind.CLIENT);
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
  public DbSemanticConvention setDbSystem(String dbSystem) {
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
  public DbSemanticConvention setDbConnectionString(String dbConnectionString) {
    delegate.setAttribute("db.connection_string", dbConnectionString);
    return this;
  }

  /**
   * Sets db.user.
   *
   * @param dbUser Username for accessing the database.
   */
  @Override
  public DbSemanticConvention setDbUser(String dbUser) {
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
  public DbSemanticConvention setDbJdbcDriverClassname(String dbJdbcDriverClassname) {
    delegate.setAttribute("db.jdbc.driver_classname", dbJdbcDriverClassname);
    return this;
  }

  /**
   * Sets db.name.
   *
   * @param dbName If no tech-specific attribute is defined, this attribute is used to report the
   *     name of the database being accessed. For commands that switch the database, this should be
   *     set to the target database (even if the command fails).
   *     <p>In some SQL databases, the database name to be used is called &#34;schema name&#34;.
   */
  @Override
  public DbSemanticConvention setDbName(String dbName) {
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
  public DbSemanticConvention setDbStatement(String dbStatement) {
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
  public DbSemanticConvention setDbOperation(String dbOperation) {
    delegate.setAttribute("db.operation", dbOperation);
    return this;
  }

  /**
   * Sets net.peer.name.
   *
   * @param netPeerName Remote hostname or similar, see note below.
   */
  @Override
  public DbSemanticConvention setNetPeerName(String netPeerName) {
    delegate.setAttribute("net.peer.name", netPeerName);
    return this;
  }

  /**
   * Sets net.peer.ip.
   *
   * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or
   *     [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
   */
  @Override
  public DbSemanticConvention setNetPeerIp(String netPeerIp) {
    delegate.setAttribute("net.peer.ip", netPeerIp);
    return this;
  }

  /**
   * Sets net.peer.port.
   *
   * @param netPeerPort Remote port number.
   */
  @Override
  public DbSemanticConvention setNetPeerPort(long netPeerPort) {
    delegate.setAttribute("net.peer.port", netPeerPort);
    return this;
  }

  /**
   * Sets net.transport.
   *
   * @param netTransport Transport protocol used. See note below.
   */
  @Override
  public DbSemanticConvention setNetTransport(String netTransport) {
    delegate.setAttribute("net.transport", netTransport);
    return this;
  }

  /** Builder class for {@link DbSpan}. */
  public static class DbSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Span.Builder internalBuilder;

    protected DbSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public DbSpanBuilder(Span.Builder spanBuilder) {
      this.internalBuilder = spanBuilder;
    }

    public Span.Builder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** sets the {@link Span} parent. */
    public DbSpanBuilder setParent(Span parent) {
      this.internalBuilder.setParent(parent);
      return this;
    }

    /** sets the {@link Span} parent. */
    public DbSpanBuilder setParent(SpanContext remoteParent) {
      this.internalBuilder.setParent(remoteParent);
      return this;
    }

    /** this method sets the type of the {@link Span} is only available in the builder. */
    public DbSpanBuilder setKind(Span.Kind kind) {
      internalBuilder.setSpanKind(kind);
      return this;
    }

    /** starts the span */
    public DbSpan start() {
      // check for sampling relevant field here, but there are none.
      return new DbSpan(this.internalBuilder.startSpan());
    }

    /**
     * Sets db.system.
     *
     * @param dbSystem An identifier for the database management system (DBMS) product being used.
     *     See below for a list of well-known identifiers.
     */
    public DbSpanBuilder setDbSystem(String dbSystem) {
      internalBuilder.setAttribute("db.system", dbSystem);
      return this;
    }

    /**
     * Sets db.connection_string.
     *
     * @param dbConnectionString The connection string used to connect to the database.
     *     <p>It is recommended to remove embedded credentials.
     */
    public DbSpanBuilder setDbConnectionString(String dbConnectionString) {
      internalBuilder.setAttribute("db.connection_string", dbConnectionString);
      return this;
    }

    /**
     * Sets db.user.
     *
     * @param dbUser Username for accessing the database.
     */
    public DbSpanBuilder setDbUser(String dbUser) {
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
    public DbSpanBuilder setDbJdbcDriverClassname(String dbJdbcDriverClassname) {
      internalBuilder.setAttribute("db.jdbc.driver_classname", dbJdbcDriverClassname);
      return this;
    }

    /**
     * Sets db.name.
     *
     * @param dbName If no tech-specific attribute is defined, this attribute is used to report the
     *     name of the database being accessed. For commands that switch the database, this should
     *     be set to the target database (even if the command fails).
     *     <p>In some SQL databases, the database name to be used is called &#34;schema name&#34;.
     */
    public DbSpanBuilder setDbName(String dbName) {
      internalBuilder.setAttribute("db.name", dbName);
      return this;
    }

    /**
     * Sets db.statement.
     *
     * @param dbStatement The database statement being executed.
     *     <p>The value may be sanitized to exclude sensitive information.
     */
    public DbSpanBuilder setDbStatement(String dbStatement) {
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
    public DbSpanBuilder setDbOperation(String dbOperation) {
      internalBuilder.setAttribute("db.operation", dbOperation);
      return this;
    }

    /**
     * Sets net.peer.name.
     *
     * @param netPeerName Remote hostname or similar, see note below.
     */
    public DbSpanBuilder setNetPeerName(String netPeerName) {
      internalBuilder.setAttribute("net.peer.name", netPeerName);
      return this;
    }

    /**
     * Sets net.peer.ip.
     *
     * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or
     *     [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
     */
    public DbSpanBuilder setNetPeerIp(String netPeerIp) {
      internalBuilder.setAttribute("net.peer.ip", netPeerIp);
      return this;
    }

    /**
     * Sets net.peer.port.
     *
     * @param netPeerPort Remote port number.
     */
    public DbSpanBuilder setNetPeerPort(long netPeerPort) {
      internalBuilder.setAttribute("net.peer.port", netPeerPort);
      return this;
    }

    /**
     * Sets net.transport.
     *
     * @param netTransport Transport protocol used. See note below.
     */
    public DbSpanBuilder setNetTransport(String netTransport) {
      internalBuilder.setAttribute("net.transport", netTransport);
      return this;
    }
  }
}
