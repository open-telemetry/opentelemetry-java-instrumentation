/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.db.normalizer

import spock.lang.Specification
import spock.lang.Timeout

@Timeout(20)
class SqlNormalizerTest extends Specification {

  def "normalize #originalSql"() {
    setup:
    def actualNormalized = SqlNormalizer.normalize(originalSql)

    expect:
    actualNormalized == normalizedSql

    where:
    originalSql                                                                | normalizedSql
    // Numbers
    "SELECT * FROM TABLE WHERE FIELD=1234"                                     | "SELECT * FROM TABLE WHERE FIELD=?"
    "SELECT * FROM TABLE WHERE FIELD = 1234"                                   | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD>=-1234"                                   | "SELECT * FROM TABLE WHERE FIELD>=?"
    "SELECT * FROM TABLE WHERE FIELD<-1234"                                    | "SELECT * FROM TABLE WHERE FIELD<?"
    "SELECT * FROM TABLE WHERE FIELD <.1234"                                   | "SELECT * FROM TABLE WHERE FIELD <?"
    "SELECT 1.2"                                                               | "SELECT ?"
    "SELECT -1.2"                                                              | "SELECT ?"
    "SELECT -1.2e-9"                                                           | "SELECT ?"
    "SELECT 2E+9"                                                              | "SELECT ?"
    "SELECT +0.2"                                                              | "SELECT ?"
    "SELECT .2"                                                                | "SELECT ?"
    "7"                                                                        | "?"
    ".7"                                                                       | "?"
    "-7"                                                                       | "?"
    "+7"                                                                       | "?"
    "SELECT 0x0af764"                                                          | "SELECT ?"
    "SELECT 0xdeadBEEF"                                                        | "SELECT ?"

    // Not numbers but could be confused as such
    "SELECT A + B"                                                             | "SELECT A + B"
    "SELECT -- comment"                                                        | "SELECT -- comment"
    "SELECT * FROM TABLE123"                                                   | "SELECT * FROM TABLE123"
    "SELECT FIELD2 FROM TABLE_123 WHERE X<>7"                                  | "SELECT FIELD2 FROM TABLE_123 WHERE X<>?"

    // Semi-nonsensical almost-numbers to elide or not
    "SELECT --83--...--8e+76e3E-1"                                             | "SELECT ?"
    "SELECT DEADBEEF"                                                          | "SELECT DEADBEEF"
    "SELECT 123-45-6789"                                                       | "SELECT ?"
    "SELECT 1/2/34"                                                            | "SELECT ?/?/?"

    // Basic ' strings
    "SELECT * FROM TABLE WHERE FIELD = ''"                                     | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = 'words and spaces'"                     | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = ' an escaped '' quote mark inside'"     | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = '\\\\'"                                 | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = '\"inside doubles\"'"                   | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = '\"\$\$\$\$\"'"                         | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = 'a single \" doublequote inside'"       | "SELECT * FROM TABLE WHERE FIELD = ?"

    // Some databases support/encourage " instead of ' with same escape rules
    "SELECT * FROM TABLE WHERE FIELD = \"\""                                   | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = \"words and spaces'\""                  | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = \" an escaped \"\" quote mark inside\"" | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = \"\\\\\""                               | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = \"'inside singles'\""                   | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = \"'\$\$\$\$'\""                         | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = \"a single ' singlequote inside\""      | "SELECT * FROM TABLE WHERE FIELD = ?"

    // Some databases allow using dollar-quoted strings
    "SELECT * FROM TABLE WHERE FIELD = \$\$\$\$"                               | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = \$\$words and spaces\$\$"               | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = \$\$quotes '\" inside\$\$"              | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = \$\$\"''\"\$\$"                         | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = \$\$\\\\\$\$"                           | "SELECT * FROM TABLE WHERE FIELD = ?"

    // Unicode, including a unicode identifier with a trailing number
    "SELECT * FROM TABLE\u09137 WHERE FIELD = '\u0194'"                        | "SELECT * FROM TABLE\u09137 WHERE FIELD = ?"

    // whitespace normalization
    "SELECT    *    \t\r\nFROM  TABLE WHERE FIELD1 = 12344 AND FIELD2 = 5678"  | "SELECT * FROM TABLE WHERE FIELD1 = ? AND FIELD2 = ?"
  }

  def "lots and lots of ticks don't cause stack overflow or long runtimes"() {
    setup:
    String s = "'"
    for (int i = 0; i < 10000; i++) {
      assert SqlNormalizer.normalize(s) != null
      s += "'"
    }
  }

  def "very long numbers don't cause a problem"() {
    setup:
    String s = ""
    for (int i = 0; i < 10000; i++) {
      s += String.valueOf(i)
    }
    assert "?" == SqlNormalizer.normalize(s)
  }

  def "very long numbers at end of table name don't cause problem"() {
    setup:
    String s = "A"
    for (int i = 0; i < 10000; i++) {
      s += String.valueOf(i)
    }
    assert s.substring(0, SqlNormalizer.LIMIT) == SqlNormalizer.normalize(s)
  }

  def "test 32k truncation"() {
    setup:
    StringBuffer s = new StringBuffer()
    for (int i = 0; i < 10000; i++) {
      s.append("SELECT * FROM TABLE WHERE FIELD = 1234 AND ")
    }
    String normalized = SqlNormalizer.normalize(s.toString())
    System.out.println(normalized.length())
    assert normalized.length() <= SqlNormalizer.LIMIT
    assert !normalized.contains("1234")
  }

  def "random bytes don't cause exceptions or timeouts"() {
    setup:
    Random r = new Random(0)
    for (int i = 0; i < 1000; i++) {
      StringBuffer sb = new StringBuffer()
      for (int c = 0; c < 1000; c++) {
        sb.append((char) r.nextInt((int) Character.MAX_VALUE))
      }
      SqlNormalizer.normalize(sb.toString())
    }
  }
}
