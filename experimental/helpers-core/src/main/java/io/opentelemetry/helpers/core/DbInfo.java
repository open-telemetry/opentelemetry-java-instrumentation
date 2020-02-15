package io.opentelemetry.helpers.core;

/**
 * An adaptor to extract information from database connections or metadata.
 *
 * <p>Please refer to this <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/data-database.md">document</a>
 * for more information about the DB attributes recorded in Open Telemetry.
 */
public interface DbInfo {

  String getDbType();

  String getDbInstance();

  String getDbUser();

  String getDbUrl();
}
