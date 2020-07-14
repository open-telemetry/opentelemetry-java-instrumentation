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

public interface DbMongodbSemanticConvention {
  void end();

  Span getSpan();

  /**
   * Sets a value for db.system
   *
   * @param dbSystem An identifier for the database management system (DBMS) product being used. See
   *     below for a list of well-known identifiers..
   */
  public DbMongodbSemanticConvention setDbSystem(String dbSystem);

  /**
   * Sets a value for db.connection_string
   *
   * @param dbConnectionString The connection string used to connect to the database..
   *     <p>It is recommended to remove embedded credentials.
   */
  public DbMongodbSemanticConvention setDbConnectionString(String dbConnectionString);

  /**
   * Sets a value for db.user
   *
   * @param dbUser Username for accessing the database..
   */
  public DbMongodbSemanticConvention setDbUser(String dbUser);

  /**
   * Sets a value for db.mssql.instance_name
   *
   * @param dbMssqlInstanceName The Microsoft SQL Server [instance
   *     name](https://docs.microsoft.com/en-us/sql/connect/jdbc/building-the-connection-url?view=sql-server-ver15)
   *     connecting to. This name is used to determine the port of a named instance..
   *     <p>If setting a `db.mssql.instance_name`, `net.peer.port` is no longer required (but still
   *     recommended if non-standard).
   */
  public DbMongodbSemanticConvention setDbMssqlInstanceName(String dbMssqlInstanceName);

  /**
   * Sets a value for db.jdbc.driver_classname
   *
   * @param dbJdbcDriverClassname The fully-qualified class name of the JDBC driver used to
   *     connect..
   */
  public DbMongodbSemanticConvention setDbJdbcDriverClassname(String dbJdbcDriverClassname);

  /**
   * Sets a value for db.name
   *
   * @param dbName If no tech-specific attribute is defined, this attribute is used to report the
   *     name of the database being accessed. For commands that switch the database, this should be
   *     set to the target database (even if the command fails)..
   *     <p>In some SQL databases, the database name to be used is called "schema name".
   */
  public DbMongodbSemanticConvention setDbName(String dbName);

  /**
   * Sets a value for db.statement
   *
   * @param dbStatement The database statement being executed..
   *     <p>The value may be sanitized to exclude sensitive information.
   */
  public DbMongodbSemanticConvention setDbStatement(String dbStatement);

  /**
   * Sets a value for db.operation
   *
   * @param dbOperation The name of the operation being executed, e.g. the [MongoDB command
   *     name](https://docs.mongodb.com/manual/reference/command/#database-operations) such as
   *     `findAndModify`..
   *     <p>While it would semantically make sense to set this, e.g., to a SQL keyword like `SELECT`
   *     or `INSERT`, it is not recommended to attempt any client-side parsing of `db.statement`
   *     just to get this property (the back end can do that if required).
   */
  public DbMongodbSemanticConvention setDbOperation(String dbOperation);

  /**
   * Sets a value for net.peer.name
   *
   * @param netPeerName Remote hostname or similar, see note below..
   */
  public DbMongodbSemanticConvention setNetPeerName(String netPeerName);

  /**
   * Sets a value for net.peer.ip
   *
   * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or
   *     [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
   */
  public DbMongodbSemanticConvention setNetPeerIp(String netPeerIp);

  /**
   * Sets a value for net.peer.port
   *
   * @param netPeerPort Remote port number..
   */
  public DbMongodbSemanticConvention setNetPeerPort(long netPeerPort);

  /**
   * Sets a value for net.transport
   *
   * @param netTransport Transport protocol used. See note below..
   */
  public DbMongodbSemanticConvention setNetTransport(String netTransport);

  /**
   * Sets a value for db.mongodb.collection
   *
   * @param dbMongodbCollection The collection being accessed within the database stated in
   *     `db.name`..
   */
  public DbMongodbSemanticConvention setDbMongodbCollection(String dbMongodbCollection);
}
