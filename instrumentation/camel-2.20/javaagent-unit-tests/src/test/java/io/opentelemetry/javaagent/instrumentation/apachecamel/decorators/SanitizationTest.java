/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.decorators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SanitizationTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "FROM TABLE WHERE FIELD>=-1234 | FROM TABLE WHERE FIELD>=?",
        "SELECT Name, Phone.Number FROM Contact WHERE Address.State = 'NY' | SELECT Name, Phone.Number FROM Contact WHERE Address.State = ?",
        "FROM col WHERE @Tag='Something' | FROM col WHERE @Tag=?"
      })
  void sanitizeCql(String original, String expected) {
    DbSpanDecorator decorator = new DbSpanDecorator("cql", "");

    Exchange exchange = mock(Exchange.class);
    Message message = mock(Message.class);
    when(message.getHeader("CamelCqlQuery")).thenReturn(original);
    when(exchange.getIn()).thenReturn(message);

    String actualSanitized = decorator.getStatement(exchange, null);
    assertThat(actualSanitized).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "SELECT 3, SELECT ?",
    "SELECT * FROM TABLE WHERE FIELD = 1234, SELECT * FROM TABLE WHERE FIELD = ?",
    "SELECT * FROM TABLE WHERE FIELD<-1234, SELECT * FROM TABLE WHERE FIELD<?",
    "SELECT col1 AS col2 FROM users WHERE field=1234, SELECT col1 AS col2 FROM users WHERE field=?"
  })
  void sanitizeJdbc(String original, String expected) {
    DbSpanDecorator decorator = new DbSpanDecorator("jdbc", "");

    Exchange exchange = mock(Exchange.class);
    Message message = mock(Message.class);
    when(message.getBody()).thenReturn(original);
    when(exchange.getIn()).thenReturn(message);

    String actualSanitized = decorator.getStatement(exchange, null);
    assertThat(actualSanitized).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "SELECT * FROM table WHERE col1=1234 AND col2>3, SELECT * FROM table WHERE col1=? AND col2>?",
    "UPDATE table SET col=12, UPDATE table SET col=?",
    "insert into table where col=321, insert into table where col=?"
  })
  void sanitizeSql(String original, String expected) {

    DbSpanDecorator decorator = new DbSpanDecorator("sql", "");

    Exchange exchange = mock(Exchange.class);
    Message message = mock(Message.class);
    when(message.getHeader("CamelSqlQuery")).thenReturn(original);
    when(exchange.getIn()).thenReturn(message);

    String actualSanitized = decorator.getStatement(exchange, null);
    assertThat(actualSanitized).isEqualTo(expected);
  }
}
