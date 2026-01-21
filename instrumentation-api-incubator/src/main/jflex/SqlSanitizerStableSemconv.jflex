/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import java.util.regex.Pattern;

%%

%final
%class AutoSqlSanitizerStableSemconv
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
LINE_COMMENT         = "--" [ \t] [^\r\n]*
UNQUOTED_IDENTIFIER  = ([:letter:] | "_") ([:letter:] | [0-9] | "_")*
IDENTIFIER_PART      = {UNQUOTED_IDENTIFIER} | {DOUBLE_QUOTED_STR} | {BACKTICK_QUOTED_STR}
// We are using {UNQUOTED_IDENTIFIER} instead of {IDENTIFIER_PART} here because DOUBLE_QUOTED_STR
// and BACKTICK_QUOTED_STR are handled separately. Depending on the context they appear in they will
// either be recorded as the identifier or replaced with ?.
IDENTIFIER           = {UNQUOTED_IDENTIFIER} | ({IDENTIFIER_PART} ("." {IDENTIFIER_PART})+)
BASIC_NUM            = [.+-]* [0-9] ([0-9] | [eE.+-])*
HEX_NUM              = "0x" ([a-f] | [A-F] | [0-9])+
QUOTED_STR           = "'" ("''" | [^'])* "'"
DOUBLE_QUOTED_STR    = "\"" ("\"\"" | [^\"])* "\""
DOLLAR_QUOTED_STR    = "$$" [^$]* "$$"
BACKTICK_QUOTED_STR  = "`" [^`]* "`"
BRACKET_QUOTED_STR   = "[" [^\]]* "]"
POSTGRE_PARAM_MARKER = "$"[0-9]*
WHITESPACE           = [ \t\r\n]+

%{
  static SqlStatementInfo sanitize(String statement, SqlDialect dialect) {
    AutoSqlSanitizerStableSemconv sanitizer = new AutoSqlSanitizerStableSemconv(new java.io.StringReader(statement));
    sanitizer.dialect = dialect;
    try {
      while (!sanitizer.yyatEOF()) {
        int token = sanitizer.yylex();
        if (token == YYEOF) {
          break;
        }
      }
      return sanitizer.getResult();
    } catch (java.io.IOException e) {
      return SqlStatementInfo.create(null, null, null, null);
    }
  }

  static final int LIMIT = 32 * 1024;

  private static final Pattern IN_STATEMENT_PATTERN = Pattern.compile("(\\sIN\\s*)\\(\\s*\\?\\s*(?:,\\s*\\?\\s*)*+\\)", Pattern.CASE_INSENSITIVE);
  private static final String IN_STATEMENT_NORMALIZED = "$1(?)";

  private final StringBuilder builder = new StringBuilder();
  private final StringBuilder querySummaryBuilder = new StringBuilder();
  private String storedProcedureName = null;

  private boolean insideComment = false;
  private Operation operation = NoOp.INSTANCE;
  private SqlDialect dialect;

  private void appendCurrentFragment() {
    builder.append(zzBuffer, zzStartRead, zzMarkedPos - zzStartRead);
  }

  private boolean isOverLimit() {
    return builder.length() > LIMIT;
  }

  private void appendOperationToSummary(String op) {
    if (querySummaryBuilder.length() > 0) {
      querySummaryBuilder.append(' ');
    }
    querySummaryBuilder.append(op);
  }

  private void appendTargetToSummary() {
    if (querySummaryBuilder.length() > 0) {
      querySummaryBuilder.append(' ');
    }
    querySummaryBuilder.append(yytext());
  }

  private static final int FROM_TABLE_REF_MAX_IDENTIFIERS = 3;

  private static abstract class Operation {
    void handleFrom() {}
    void handleInto() {}
    void handleJoin() {}
    void handleIdentifier() {}
    void handleComma() {}
    void handleOpenParen() {}
    void handleSelect() {}
    void handleNext() {}
    void handleOperationTarget(String target) {}
    boolean expectingOperationTarget() { return false; }
  }

  private static class NoOp extends Operation {
    static final Operation INSTANCE = new NoOp();
  }

  private abstract class DdlOperation extends Operation {
    boolean expectingOperationTarget = true;
    boolean identifierCaptured = false;

    boolean expectingOperationTarget() { return expectingOperationTarget; }

    void handleOperationTarget(String target) {
      appendOperationToSummary(target);
      expectingOperationTarget = false;
    }

    void handleIdentifier() {
      if (!expectingOperationTarget && !identifierCaptured) {
        appendTargetToSummary();
        identifierCaptured = true;
      }
    }

    void handleSelect() {
      appendOperationToSummary("SELECT");
    }
  }

  private class Select extends Operation {
    boolean expectingTableName = false;
    int identifiersAfterFromClause = 0;
    boolean inImplicitJoin = false;
    int identifiersAfterComma = 0;
    boolean expectingJoinTableName = false;
    int identifiersAfterJoin = 0;
    boolean afterSetOperator = false;

    void handleFrom() {
      expectingTableName = true;
    }

    void handleJoin() {
      expectingJoinTableName = true;
      identifiersAfterJoin = 0;
    }

    void handleIdentifier() {
      if (identifiersAfterFromClause > 0) {
        ++identifiersAfterFromClause;
      }

      if (expectingJoinTableName) {
        ++identifiersAfterJoin;
        if (identifiersAfterJoin == 1) {
          appendTargetToSummary();
        }
        if (identifiersAfterJoin >= FROM_TABLE_REF_MAX_IDENTIFIERS) {
          expectingJoinTableName = false;
        }
        return;
      }

      if (inImplicitJoin) {
        ++identifiersAfterComma;
        if (identifiersAfterComma == 1) {
          appendTargetToSummary();
        }
        return;
      }

      if (!expectingTableName) {
        return;
      }

      appendTargetToSummary();
      expectingTableName = false;
      identifiersAfterFromClause = 1;
    }

    void handleComma() {
      if (identifiersAfterFromClause > 0
          && identifiersAfterFromClause <= FROM_TABLE_REF_MAX_IDENTIFIERS) {
        inImplicitJoin = true;
        identifiersAfterComma = 0;
      } else if (inImplicitJoin) {
        identifiersAfterComma = 0;
      }
    }

    void handleSelect() {
      if (afterSetOperator) {
        // This is a SELECT after UNION/INTERSECT/EXCEPT/MINUS
        afterSetOperator = false;
      }
      appendOperationToSummary("SELECT");
      // Reset state for the new SELECT
      expectingTableName = false;
      identifiersAfterFromClause = 0;
      inImplicitJoin = false;
      identifiersAfterComma = 0;
      expectingJoinTableName = false;
      identifiersAfterJoin = 0;
    }

    void handleOpenParen() {
      if (expectingTableName) {
        expectingTableName = false;
      }
    }

    void resetForSetOperator() {
      afterSetOperator = true;
      expectingTableName = false;
      identifiersAfterFromClause = 0;
      inImplicitJoin = false;
      identifiersAfterComma = 0;
      expectingJoinTableName = false;
      identifiersAfterJoin = 0;
    }
  }

  private class Insert extends Operation {
    boolean expectingTableName = false;

    void handleInto() {
      expectingTableName = true;
    }

    void handleSelect() {
      operation = new Select();
      appendOperationToSummary("SELECT");
    }

    void handleIdentifier() {
      if (expectingTableName) {
        appendTargetToSummary();
        expectingTableName = false;
      }
    }
  }

  private class Delete extends Operation {
    boolean expectingTableName = false;

    void handleFrom() {
      expectingTableName = true;
    }

    void handleIdentifier() {
      if (expectingTableName) {
        appendTargetToSummary();
        expectingTableName = false;
      }
    }
  }

  private class SimpleOperation extends Operation {
    boolean identifierCaptured = false;

    void handleIdentifier() {
      if (!identifierCaptured) {
        appendTargetToSummary();
        identifierCaptured = true;
      }
    }
  }

  private class Call extends Operation {
    boolean identifierCaptured = false;

    void handleIdentifier() {
      if (!identifierCaptured) {
        storedProcedureName = yytext();
        appendTargetToSummary();
        identifierCaptured = true;
      }
    }

    void handleNext() {
      storedProcedureName = null;
    }
  }

  private class Update extends SimpleOperation {}
  private class Merge extends SimpleOperation {}
  private class Create extends DdlOperation {}
  private class Drop extends DdlOperation {}
  private class Alter extends DdlOperation {}

  private SqlStatementInfo getResult() {
    if (builder.length() > LIMIT) {
      builder.delete(LIMIT, builder.length());
    }
    String fullStatement = builder.toString();
    String normalizedStatement = IN_STATEMENT_PATTERN.matcher(fullStatement).replaceAll(IN_STATEMENT_NORMALIZED);
    String querySummary = querySummaryBuilder.length() > 0 ? querySummaryBuilder.toString() : null;
    return SqlStatementInfo.create(normalizedStatement, null, storedProcedureName, querySummary);
  }

%}

%%

<YYINITIAL> {

  "SELECT" {
          if (!insideComment) {
            if (operation == NoOp.INSTANCE) {
              operation = new Select();
              appendOperationToSummary("SELECT");
            } else {
              operation.handleSelect();
            }
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "INSERT" {
          if (!insideComment && operation == NoOp.INSTANCE) {
            operation = new Insert();
            appendOperationToSummary("INSERT");
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "DELETE" {
          if (!insideComment && operation == NoOp.INSTANCE) {
            operation = new Delete();
            appendOperationToSummary("DELETE");
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "UPDATE" {
          if (!insideComment && operation == NoOp.INSTANCE) {
            operation = new Update();
            appendOperationToSummary("UPDATE");
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "CALL" {
          if (!insideComment && operation == NoOp.INSTANCE) {
            operation = new Call();
            appendOperationToSummary("CALL");
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "MERGE" {
          if (!insideComment && operation == NoOp.INSTANCE) {
            operation = new Merge();
            appendOperationToSummary("MERGE");
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "CREATE" {
          if (!insideComment && operation == NoOp.INSTANCE) {
            operation = new Create();
            appendOperationToSummary("CREATE");
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "DROP" {
          if (!insideComment && operation == NoOp.INSTANCE) {
            operation = new Drop();
            appendOperationToSummary("DROP");
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "ALTER" {
          if (!insideComment && operation == NoOp.INSTANCE) {
            operation = new Alter();
            appendOperationToSummary("ALTER");
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "UNION" | "INTERSECT" | "EXCEPT" | "MINUS" {
          if (!insideComment && operation instanceof Select) {
            // Reset Select state to capture next SELECT operation's table
            ((Select) operation).resetForSetOperator();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "CONNECT" {
          appendCurrentFragment();
          if (!insideComment && operation == NoOp.INSTANCE) {
            builder.append(" ?");
            return YYEOF;
          }
          if (isOverLimit()) return YYEOF;
      }
  "FROM" {
          if (!insideComment) {
            if (operation == NoOp.INSTANCE) {
              operation = new Select();
            }
            operation.handleFrom();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "INTO" {
          if (!insideComment) {
            operation.handleInto();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "JOIN" {
          if (!insideComment) {
            operation.handleJoin();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "NEXT" {
          if (!insideComment) {
            operation.handleNext();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "IF" | "NOT" | "EXISTS" {
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "TABLE" | "INDEX" | "DATABASE" | "PROCEDURE" | "VIEW" {
          if (!insideComment) {
            if (operation.expectingOperationTarget()) {
              operation.handleOperationTarget(yytext());
            } else {
              operation.handleIdentifier();
            }
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "USER" {
          appendCurrentFragment();
          if (!insideComment && (operation instanceof Create || operation instanceof Alter)) {
            builder.append(" ?");
            return YYEOF;
          }
          if (isOverLimit()) return YYEOF;
      }

  {COMMA} {
          if (!insideComment) {
            operation.handleComma();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  {IDENTIFIER} {
          if (!insideComment) {
            operation.handleIdentifier();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }

  {OPEN_PAREN}  {
          if (!insideComment) {
            operation.handleOpenParen();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  {CLOSE_PAREN} {
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

  {LINE_COMMENT} {
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }

  {BASIC_NUM} | {HEX_NUM} | {QUOTED_STR} | {DOLLAR_QUOTED_STR} {
          builder.append('?');
          if (isOverLimit()) return YYEOF;
      }

  {DOUBLE_QUOTED_STR} {
          if (dialect == SqlDialect.COUCHBASE) {
            builder.append('?');
          } else {
            if (!insideComment) {
              operation.handleIdentifier();
            }
            appendCurrentFragment();
          }
          if (isOverLimit()) return YYEOF;
      }

  {BACKTICK_QUOTED_STR} | {BRACKET_QUOTED_STR} | {POSTGRE_PARAM_MARKER} {
          if (!insideComment) {
            operation.handleIdentifier();
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
