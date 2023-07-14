/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.decorators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.jupiter.api.Test;

class SanitizationTest {

  @Test
  void sanitizeCql() {
    String[] originalCql = {
      "FROM TABLE WHERE FIELD>=-1234",
      "SELECT Name, Phone.Number FROM Contact WHERE Address.State = 'NY'",
      "FROM col WHERE @Tag='Something'"
    };
    String[] sanitizedCql = {
      "FROM TABLE WHERE FIELD>=?",
      "SELECT Name, Phone.Number FROM Contact WHERE Address.State = ?",
      "FROM col WHERE @Tag=?"
    };

    DbSpanDecorator decorator = new DbSpanDecorator("cql", "");

    for (int i = 0; i < originalCql.length; i++) {
      Exchange exchange = mock(Exchange.class);
      Message message = mock(Message.class);
      when(message.getHeader("CamelCqlQuery")).thenReturn(originalCql[i]);
      when(exchange.getIn()).thenReturn(message);

      String actualSanitized = decorator.getStatement(exchange, null);
      assertEquals(actualSanitized, sanitizedCql[i]);
    }
  }

  @Test
  void sanitizeJdbc() {
    String[] originalSql = {
      "SELECT 3",
      "SELECT * FROM TABLE WHERE FIELD = 1234",
      "SELECT * FROM TABLE WHERE FIELD<-1234",
      "SELECT col1 AS col2 FROM users WHERE field=1234"
    };
    String[] sanitizedSql = {
      "SELECT ?",
      "SELECT * FROM TABLE WHERE FIELD = ?",
      "SELECT * FROM TABLE WHERE FIELD<?",
      "SELECT col1 AS col2 FROM users WHERE field=?"
    };

    DbSpanDecorator decorator = new DbSpanDecorator("jdbc", "");

    for (int i = 0; i < originalSql.length; i++) {
      Exchange exchange = mock(Exchange.class);
      Message message = mock(Message.class);
      when(message.getBody()).thenReturn(originalSql[i]);
      when(exchange.getIn()).thenReturn(message);

      String actualSanitized = decorator.getStatement(exchange, null);
      assertEquals(actualSanitized, sanitizedSql[i]);
    }
  }

  @Test
  void sanitizeSql() {
    String[] originalSql = {
      "SELECT * FROM table WHERE col1=1234 AND col2>3",
      "UPDATE table SET col=12",
      "insert into table where col=321"
    };
    String[] sanitizedSql = {
      "SELECT * FROM table WHERE col1=? AND col2>?",
      "UPDATE table SET col=?",
      "insert into table where col=?"
    };

    DbSpanDecorator decorator = new DbSpanDecorator("sql", "");

    for (int i = 0; i < originalSql.length; i++) {
      Exchange exchange = mock(Exchange.class);
      Message message = mock(Message.class);
      when(message.getHeader("CamelSqlQuery")).thenReturn(originalSql[i]);
      when(exchange.getIn()).thenReturn(message);

      String actualSanitized = decorator.getStatement(exchange, null);
      assertEquals(actualSanitized, sanitizedSql[i]);
    }
  }
}
