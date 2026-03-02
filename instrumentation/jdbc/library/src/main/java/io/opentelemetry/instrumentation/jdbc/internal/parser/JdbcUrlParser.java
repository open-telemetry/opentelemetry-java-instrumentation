/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

/**
 * Interface for JDBC URL parsers.
 *
 * <p><b>IMPORTANT:</b> Implementations should expect that JDBC URLs passed to the {@link
 * #parse(String, ParseContext)} method have been lowercased by the caller. URL parameter names
 * should be checked using lowercase keys (e.g., "user", "servername", "databasename").
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface JdbcUrlParser {

  /**
   * Parse the JDBC URL and populate the context with extracted information.
   *
   * <p>Implementations are responsible for the full parsing lifecycle:
   *
   * <ol>
   *   <li>Set driver-specific defaults (system name, default host/port/user)
   *   <li>Call {@link ParseContext#applyDataSourceProperties()} or {@link
   *       ParseContext#applyUserProperty()} at the appropriate point to match driver-specific
   *       precedence semantics. Use {@code applyUserProperty()} for drivers whose DataSource does
   *       not support the standard serverName/portNumber/databaseName properties.
   *   <li>Parse the URL structure
   * </ol>
   *
   * <p>The placement of {@link ParseContext#applyDataSourceProperties()} controls precedence:
   *
   * <ul>
   *   <li>Before URL parsing: URL values take precedence (e.g., PostgreSQL, MySQL, Oracle)
   *   <li>After URL parsing: DataSource properties take precedence (e.g., Microsoft SQL Server)
   * </ul>
   *
   * @param jdbcUrl the JDBC URL to parse (without the "jdbc:" prefix)
   * @param ctx the parse context containing type and optional DataSource properties
   */
  void parse(String jdbcUrl, ParseContext ctx);
}
