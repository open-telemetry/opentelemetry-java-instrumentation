/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import static io.opentelemetry.javaagent.instrumentation.cassandra.v4_0.CassandraTableNameExtractor.extractTableNameFromQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CassandraTableNameExtractorTest {

  /**
   * cycling cql examples from
   * https://docs.datastax.com/en/cql-oss/3.3/cql/cql_reference/cqlCommandsTOC.html
   */
  @Test
  void extractTableNameFromQueryTest() {
    assertEquals("table1", extractTableNameFromQuery("SELECT test FROM table1"));
    assertEquals("table1", extractTableNameFromQuery("SELECT test FROM keyspace.table1"));
    assertEquals("table1", extractTableNameFromQuery("SELECT test FROM table1 WHERE 1=1"));
    assertEquals(
        "table1",
        extractTableNameFromQuery(
            "ALTER TABLE table1\n"
                + "  WITH comment = 'ID, name, birthdate and country'\n"
                + "     AND read_repair_chance = 0.2"));
    assertEquals(
        "table1",
        extractTableNameFromQuery(
            "CREATE TABLE IF NOT EXISTS cycling.table1 ( \n"
                + "   category text, \n"
                + "   points int, \n"
                + "   id UUID, \n"
                + "   lastname text, \n"
                + "   PRIMARY KEY (category, points)) \n"
                + "WITH CLUSTERING ORDER BY (points DESC)\n"
                + "   AND COMPACT STORAGE;"));
    assertEquals("table1", extractTableNameFromQuery("DROP TABLE table1"));
    assertEquals("table1", extractTableNameFromQuery("DROP TABLE IF EXISTS k1.table1"));
    assertEquals(
        "table1",
        extractTableNameFromQuery(
            "UPDATE cycling.table1\n"
                + "SET comments ='='Rides hard, gets along with others, a real winner'\n"
                + "WHERE id = fb372533-eb95-4bb4-8685-6ef61e994caa IF EXISTS;"));
    assertEquals(
        "table1",
        extractTableNameFromQuery(
            "INSERT INTO cycling.table1 (id, lastname, firstname)\n"
                + "  VALUES (6ab09bec-e68e-48d9-a5f8-97e6fb4c9b47, 'KRUIKSWIJK','Steven')\n"
                + "  USING TTL 86400 AND TIMESTAMP 123456789;"));
  }
}
