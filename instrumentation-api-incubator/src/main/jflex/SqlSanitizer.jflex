/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import java.util.regex.Pattern;

%%

%final
%class AutoSqlSanitizer
%apiprivate
%int
%buffer 2048

%unicode
%ignorecase

COMMA                = ","
OPEN_PAREN           = "("
CLOSE_PAREN          = ")"
OPEN_COMMENT         = "/*"
CLOSE_COMMENT        = "*/"
IDENTIFIER           = ([:letter:] | "_") ([:letter:] | [0-9] | [_.])*
BASIC_NUM            = [.+-]* [0-9] ([0-9] | [eE.+-])*
HEX_NUM              = "0x" ([a-f] | [A-F] | [0-9])+
QUOTED_STR           = "'" ("''" | [^'])* "'"
DOUBLE_QUOTED_STR    = "\"" ("\"\"" | [^\"])* "\""
DOLLAR_QUOTED_STR    = "$$" [^$]* "$$"
BACKTICK_QUOTED_STR  = "`" [^`]* "`"
POSTGRE_PARAM_MARKER = "$"[0-9]*
WHITESPACE           = [ \t\r\n]+

%{
  static SqlStatementInfo sanitize(String statement, SqlDialect dialect) {
    AutoSqlSanitizer sanitizer = new AutoSqlSanitizer(new java.io.StringReader(statement));
    sanitizer.dialect = dialect;
    try {
      while (!sanitizer.yyatEOF()) {
        int token = sanitizer.yylex();
        // YYEOF token may be used to stop processing
        if (token == YYEOF) {
          break;
        }
      }
      return sanitizer.getResult();
    } catch (java.io.IOException e) {
      // should never happen
      return SqlStatementInfo.create(null, null, null);
    }
  }

  // max length of the sanitized statement - SQLs longer than this will be trimmed
  static final int LIMIT = 32 * 1024;

  // Match on strings like "IN(?, ?, ...)"
  private static final Pattern IN_STATEMENT_PATTERN = Pattern.compile("(\\sIN\\s*)\\(\\s*\\?\\s*(?:,\\s*\\?\\s*)*+\\)", Pattern.CASE_INSENSITIVE);
  private static final String IN_STATEMENT_NORMALIZED = "$1(?)";

  private final StringBuilder builder = new StringBuilder();

  private void appendCurrentFragment() {
    builder.append(zzBuffer, zzStartRead, zzMarkedPos - zzStartRead);
  }

  private boolean isOverLimit() {
    return builder.length() > LIMIT;
  }

  /** @return text matched by current token without enclosing double quotes or backticks */
  private String readIdentifierName() {
    String identifierName = yytext();
    if (identifierName != null && ((identifierName.startsWith("\"") && identifierName.endsWith("\""))
        || (identifierName.startsWith("`") && identifierName.endsWith("`")))) {
      identifierName = identifierName.substring(1, identifierName.length() - 1);
    }
    return identifierName;
  }

  // you can reference a table in the FROM clause in one of the following ways:
  //   table
  //   table t
  //   table as t
  // in other words, you need max 3 identifiers to reference a table
  private static final int FROM_TABLE_REF_MAX_IDENTIFIERS = 3;

  private int parenLevel = 0;
  private boolean insideComment = false;
  private Operation operation = NoOp.INSTANCE;
  private boolean extractionDone = false;
  private SqlDialect dialect;

  private void setOperation(Operation operation) {
    if (this.operation == NoOp.INSTANCE) {
      this.operation = operation;
    }
  }

  private static abstract class Operation {
    String mainIdentifier = null;

    /** @return true if all statement info is gathered */
    boolean handleFrom() {
      return false;
    }

    /** @return true if all statement info is gathered */
    boolean handleInto() {
      return false;
    }

    /** @return true if all statement info is gathered */
    boolean handleJoin() {
      return false;
    }

    /** @return true if all statement info is gathered */
    boolean handleIdentifier() {
      return false;
    }

    /** @return true if all statement info is gathered */
    boolean handleComma() {
      return false;
    }

    /** @return true if all statement info is gathered */
    boolean handleNext() {
      return false;
    }

    /** @return true if all statement info is gathered */
    boolean handleOperationTarget(String target) {
      return false;
    }

    boolean expectingOperationTarget() {
      return false;
    }

    SqlStatementInfo getResult(String fullStatement) {
      return SqlStatementInfo.create(fullStatement, getClass().getSimpleName().toUpperCase(java.util.Locale.ROOT), mainIdentifier);
    }
  }

  private abstract class DdlOperation extends Operation {
    private String operationTarget = "";
    private boolean expectingOperationTarget = true;

    boolean expectingOperationTarget() {
      return expectingOperationTarget;
    }

    boolean handleOperationTarget(String target) {
      operationTarget = target;
      expectingOperationTarget = false;
      return false;
    }

    boolean shouldHandleIdentifier() {
      // Return true only if the provided value corresponds to a table, as it will be used to set the attribute `db.sql.table`.
      return "TABLE".equals(operationTarget);
    }

    boolean handleIdentifier() {
      if (shouldHandleIdentifier()) {
        mainIdentifier = readIdentifierName();
      }
      return true;
    }

    SqlStatementInfo getResult(String fullStatement) {
      if (!"".equals(operationTarget)) {
        return SqlStatementInfo.create(fullStatement, getClass().getSimpleName().toUpperCase(java.util.Locale.ROOT) + " " + operationTarget, mainIdentifier);
      }
      return super.getResult(fullStatement);
    }
  }

  private static class NoOp extends Operation {
    static final Operation INSTANCE = new NoOp();

    SqlStatementInfo getResult(String fullStatement) {
      return SqlStatementInfo.create(fullStatement, null, null);
    }
  }

  private class Select extends Operation {
    // you can reference a table in the FROM clause in one of the following ways:
    //   table
    //   table t
    //   table as t
    // in other words, you need max 3 identifiers to reference a table
    private static final int FROM_TABLE_REF_MAX_IDENTIFIERS = 3;

    boolean expectingTableName = false;
    boolean mainTableSetAlready = false;
    int identifiersAfterMainFromClause = 0;

    boolean handleFrom() {
      if (parenLevel == 0) {
        // main query FROM clause
        expectingTableName = true;
        return false;
      }

      // subquery in WITH or SELECT clause, before main FROM clause; skipping
      mainIdentifier = null;
      return true;
    }

    boolean handleJoin() {
      // for SELECT statements with joined tables there's no main table
      mainIdentifier = null;
      return true;
    }

    boolean handleIdentifier() {
      if (identifiersAfterMainFromClause > 0) {
        ++identifiersAfterMainFromClause;
      }

      if (!expectingTableName) {
        return false;
      }

      // SELECT FROM (subquery) case
      if (parenLevel != 0) {
        mainIdentifier = null;
        return true;
      }

      // whenever >1 table is used there is no main table (e.g. unions)
      if (mainTableSetAlready) {
        mainIdentifier = null;
        return true;
      }

      mainIdentifier = readIdentifierName();
      mainTableSetAlready = true;
      expectingTableName = false;
      // start counting identifiers after encountering main from clause
      identifiersAfterMainFromClause = 1;

      // continue scanning the query, there may be more than one table (e.g. joins)
      return false;
    }

    boolean handleComma() {
      // comma was encountered in the FROM clause, i.e. implicit join
      // (if less than 3 identifiers have appeared before first comma then it means that it's a table list;
      // any other list that can appear later needs at least 4 idents)
      if (identifiersAfterMainFromClause > 0
          && identifiersAfterMainFromClause <= FROM_TABLE_REF_MAX_IDENTIFIERS) {
        mainIdentifier = null;
        return true;
      }
      return false;
    }
  }

  private class Insert extends Operation {
    boolean expectingTableName = false;

    boolean handleInto() {
      expectingTableName = true;
      return false;
    }

    boolean handleIdentifier() {
      if (!expectingTableName) {
        return false;
      }

      mainIdentifier = readIdentifierName();
      return true;
    }
  }

  private class Delete extends Operation {
    boolean expectingTableName = false;

    boolean handleFrom() {
      expectingTableName = true;
      return false;
    }

    boolean handleIdentifier() {
      if (!expectingTableName) {
        return false;
      }

      mainIdentifier = readIdentifierName();
      return true;
    }
  }

  private class Update extends Operation {
    boolean handleIdentifier() {
      mainIdentifier = readIdentifierName();
      return true;
    }
  }

  private class Call extends Operation {
    boolean handleIdentifier() {
      mainIdentifier = readIdentifierName();
      return true;
    }

    boolean handleNext() {
      mainIdentifier = null;
      return true;
    }
  }

  private class Merge extends Operation {
    boolean handleIdentifier() {
      mainIdentifier = readIdentifierName();
      return true;
    }
  }

  private class Create extends DdlOperation {
  }

  private class Drop extends DdlOperation {
  }

  private class Alter extends DdlOperation {
  }

  private SqlStatementInfo getResult() {
    if (builder.length() > LIMIT) {
      builder.delete(LIMIT, builder.length());
    }
    String fullStatement = builder.toString();

    // Normalize all 'in (?, ?, ...)' statements to in (?) to reduce cardinality
    String normalizedStatement = IN_STATEMENT_PATTERN.matcher(fullStatement).replaceAll(IN_STATEMENT_NORMALIZED);

    return operation.getResult(normalizedStatement);
  }

%}

%%

<YYINITIAL> {

  "SELECT" {
          if (!insideComment) {
            setOperation(new Select());
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "INSERT" {
          if (!insideComment) {
            setOperation(new Insert());
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "DELETE" {
          if (!insideComment) {
            setOperation(new Delete());
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "UPDATE" {
          if (!insideComment) {
            setOperation(new Update());
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "CALL" {
          if (!insideComment) {
            setOperation(new Call());
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "MERGE" {
          if (!insideComment) {
            setOperation(new Merge());
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "CREATE" {
          if (!insideComment) {
            setOperation(new Create());
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "DROP" {
          if (!insideComment) {
            setOperation(new Drop());
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "ALTER" {
          if (!insideComment) {
            setOperation(new Alter());
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "FROM" {
          if (!insideComment && !extractionDone) {
            if (operation == NoOp.INSTANCE) {
              // hql/jpql queries may skip SELECT and start with FROM clause
              // treat such queries as SELECT queries
              setOperation(new Select());
            }
            extractionDone = operation.handleFrom();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "INTO" {
          if (!insideComment && !extractionDone) {
            extractionDone = operation.handleInto();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "JOIN" {
          if (!insideComment && !extractionDone) {
            extractionDone = operation.handleJoin();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "NEXT" {
          if (!insideComment && !extractionDone) {
            extractionDone = operation.handleNext();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "IF" | "NOT" | "EXISTS" {
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "TABLE" | "INDEX" | "DATABASE" | "PROCEDURE" | "VIEW" {
          if (!insideComment && !extractionDone) {
            if (operation.expectingOperationTarget()) {
              extractionDone = operation.handleOperationTarget(yytext());
            } else {
              extractionDone = operation.handleIdentifier();
            }
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }

  {COMMA} {
          if (!insideComment && !extractionDone) {
            extractionDone = operation.handleComma();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  {IDENTIFIER} {
          if (!insideComment && !extractionDone) {
            extractionDone = operation.handleIdentifier();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }

  {OPEN_PAREN}  {
          if (!insideComment) {
            parenLevel += 1;
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  {CLOSE_PAREN} {
          if (!insideComment) {
            parenLevel -= 1;
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }

  {OPEN_COMMENT}  {
          insideComment = true;
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  {CLOSE_COMMENT} {
          insideComment = false;
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }

  // here is where the actual sanitization happens
  {BASIC_NUM} | {HEX_NUM} | {QUOTED_STR} | {DOLLAR_QUOTED_STR} {
          builder.append('?');
          if (isOverLimit()) return YYEOF;
      }

  {DOUBLE_QUOTED_STR} {
          if (dialect == SqlDialect.COUCHBASE) {
            builder.append('?');
          } else {
            if (!insideComment && !extractionDone) {
              extractionDone = operation.handleIdentifier();
            }
            appendCurrentFragment();
          }
          if (isOverLimit()) return YYEOF;
      }

  {BACKTICK_QUOTED_STR} | {POSTGRE_PARAM_MARKER} {
        if (!insideComment && !extractionDone) {
          extractionDone = operation.handleIdentifier();
        }
        appendCurrentFragment();
        if (isOverLimit()) return YYEOF;
    }

  {WHITESPACE} {
          builder.append(' ');
          if (isOverLimit()) return YYEOF;
      }
  [^] {
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
}
