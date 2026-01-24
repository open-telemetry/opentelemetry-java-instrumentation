/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import java.util.regex.Pattern;

%%

%final
%class AutoSqlSanitizerWithSummary
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
UNQUOTED_IDENTIFIER  = ([:letter:] | "_") ([:letter:] | [0-9] | "_")*
IDENTIFIER_PART      = {UNQUOTED_IDENTIFIER} | {DOUBLE_QUOTED_STR} | {BACKTICK_QUOTED_STR} | {BRACKET_QUOTED_STR}
// We are using {UNQUOTED_IDENTIFIER} instead of {IDENTIFIER_PART} here because DOUBLE_QUOTED_STR
// and BACKTICK_QUOTED_STR are handled separately. Depending on the context they appear in they will
// either be recorded as the identifier or replaced with ?.
IDENTIFIER           = {UNQUOTED_IDENTIFIER} | ({IDENTIFIER_PART} ("." {IDENTIFIER_PART})+)
BASIC_NUM            = [.+-]* [0-9] ([0-9] | [eE.+-])*
HEX_NUM              = "0x" ([a-f] | [A-F] | [0-9])+
QUOTED_STR           = "'" ("''" | [^'])* "'"
DOUBLE_QUOTED_STR    = "\"" ("\"\"" | [^\"])* "\""
DOLLAR_QUOTED_STR    = "$$" [^$]* "$$"
BACKTICK_QUOTED_STR  = "`" ("``" | [^`])* "`"
BRACKET_QUOTED_STR   = "[" [^\]]* "]"
POSTGRE_PARAM_MARKER = "$"[0-9]*
WHITESPACE           = [ \t\r\n]+

%{
  static SqlStatementInfo sanitize(String statement, SqlDialect dialect) {
    AutoSqlSanitizerWithSummary sanitizer = new AutoSqlSanitizerWithSummary(new java.io.StringReader(statement));
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
      return SqlStatementInfo.createWithSummary(null, null, null);
    }
  }

  // max length of the sanitized statement - SQLs longer than this will be trimmed
  static final int LIMIT = 32 * 1024;

  // Match on strings like "IN(?, ?, ...)"
  private static final Pattern IN_STATEMENT_PATTERN = Pattern.compile("(\\sIN\\s*)\\(\\s*\\?\\s*(?:,\\s*\\?\\s*)*+\\)", Pattern.CASE_INSENSITIVE);
  private static final String IN_STATEMENT_NORMALIZED = "$1(?)";

  private final StringBuilder builder = new StringBuilder();
  private final StringBuilder querySummaryBuilder = new StringBuilder();
  private String storedProcedureName = null;

  private void appendCurrentFragment() {
    builder.append(zzBuffer, zzStartRead, zzMarkedPos - zzStartRead);
  }

  private boolean isOverLimit() {
    return builder.length() > LIMIT;
  }

  /** Appends an operation name (SELECT, INSERT, etc.) to the query summary. */
  private void appendOperationToSummary(String operationName) {
    if (querySummaryBuilder.length() > 0) {
      querySummaryBuilder.append(' ');
    }
    querySummaryBuilder.append(operationName);
  }

  /** Appends a target (table name, procedure name, etc.) to the query summary using current token text. */
  private void appendTargetToSummary() {
    if (querySummaryBuilder.length() > 0) {
      querySummaryBuilder.append(' ');
    }
    querySummaryBuilder.append(yytext());
  }

  private int parenLevel = 0;
  private boolean insideComment = false;
  // using special "none" Operation instead of null to avoid null checking it everywhere
  private final Operation none = new Operation() {};
  private Operation operation = none;
  private SqlDialect dialect;

  private boolean shouldStartNewOperation() {
    return !insideComment && operation == none;
  }

  private void setOperation(Operation operation) {
    this.operation = operation;
  }

  private abstract class Operation {
    void handleFrom() {}
    void handleInto() {}
    void handleJoin() {}
    void handleIdentifier() {}
    void handleComma() {}
    void handleNext() {}
    void handleAs() {}
    void handleOperationTarget(String target) {}
    void handleOpenParen() {}
    void handleCloseParen() {}
    void handleSelect() {}
    boolean expectingOperationTarget() {
      return false;
    }
  }

  private abstract class DdlOperation extends Operation {
    private String operationTarget = "";
    private boolean expectingOperationTarget = true;
    private boolean identifierCaptured = false;
    private boolean inEmbeddedSelect = false;
    private boolean expectingTableName = false;
    private int selectParenLevel = -1;

    boolean expectingOperationTarget() {
      return expectingOperationTarget;
    }

    void handleOperationTarget(String target) {
      operationTarget = target;
      expectingOperationTarget = false;
      appendOperationToSummary(operationTarget);
    }

    void handleIdentifier() {
      if (!identifierCaptured && !inEmbeddedSelect) {
        appendTargetToSummary();
        identifierCaptured = true;
      } else if (inEmbeddedSelect && expectingTableName && parenLevel == selectParenLevel) {
        appendTargetToSummary();
        expectingTableName = false;
      }
    }

    void handleSelect() {
      inEmbeddedSelect = true;
      selectParenLevel = parenLevel;
      appendOperationToSummary("SELECT");
    }

    void handleFrom() {
      if (inEmbeddedSelect) {
        expectingTableName = true;
      }
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
    boolean expectingJoinTableName = false;
    int identifiersAfterJoin = 0;
    boolean inJoinSubquery = false;
    boolean inImplicitJoin = false;
    int identifiersAfterComma = 0;
    int fromClauseParenLevel = -1;
    boolean expectingSubqueryOrTable = false;
    // Track if we just saw AS keyword and are expecting potential column alias list
    boolean sawAsKeyword = false;
    // Track if we're inside a column alias list (after derived table AS alias(...))
    boolean inColumnAliasList = false;
    int columnAliasListParenLevel = -1;

    void handleFrom() {
      // Set expectingTableName to capture tables
      expectingTableName = true;
      expectingSubqueryOrTable = true;
      // Update fromClauseParenLevel for this FROM clause
      // This allows nested SELECTs to track their own FROM clause level
      fromClauseParenLevel = parenLevel;
      sawAsKeyword = false;
    }

    void handleJoin() {
      // After JOIN, expect a table name
      expectingTableName = true;
      expectingJoinTableName = true;
      identifiersAfterJoin = 0;
      identifiersAfterMainFromClause = 0;
      expectingSubqueryOrTable = true;
      sawAsKeyword = false;
    }

    void handleApply() {
      // APPLY is similar to JOIN in SQL Server
      expectingTableName = true;
      expectingJoinTableName = true;
      identifiersAfterJoin = 0;
      identifiersAfterMainFromClause = 0;
      expectingSubqueryOrTable = true;
      sawAsKeyword = false;
    }

    void handleAs() {
      // Mark that we saw AS - next identifier is alias, then open paren might be column alias list
      sawAsKeyword = true;
    }

    void handleOpenParen() {
      // If we just saw AS keyword and an open paren, this is a column alias list
      if (sawAsKeyword) {
        inColumnAliasList = true;
        columnAliasListParenLevel = parenLevel;
        sawAsKeyword = false;
        return;
      }

      // If we just saw JOIN/FROM and now see '(', we're entering a subquery
      if ((expectingJoinTableName || expectingSubqueryOrTable) && identifiersAfterJoin == 0) {
        inJoinSubquery = true;
        expectingSubqueryOrTable = false;
      }
    }

    void handleCloseParen() {
      // If we're closing the column alias list paren, exit that state
      // Note: parenLevel has already been decremented when this is called
      if (inColumnAliasList && parenLevel < columnAliasListParenLevel) {
        inColumnAliasList = false;
        columnAliasListParenLevel = -1;
      }

      // Exiting a JOIN subquery
      if (inJoinSubquery) {
        inJoinSubquery = false;
        expectingJoinTableName = false;
      }
      // Reset FROM clause tracking if we're exiting BELOW the level where it started
      if (fromClauseParenLevel >= 0 && parenLevel < fromClauseParenLevel) {
        fromClauseParenLevel = -1;
        expectingTableName = false;
      }
    }

    void handleSelect() {
      // SELECT inside a JOIN subquery
    }

    void handleIdentifier() {
      // Don't capture identifiers if we're inside a column alias list
      if (inColumnAliasList) {
        return;
      }

      // If we saw AS, this identifier is the table/subquery alias - keep sawAsKeyword true
      // so we can detect the column alias list paren that might follow
      if (sawAsKeyword) {
        return;
      }

      if (identifiersAfterMainFromClause > 0) {
        ++identifiersAfterMainFromClause;
      }

      if (expectingJoinTableName) {
        ++identifiersAfterJoin;
        // Capture first identifier after JOIN (the table name)
        // Skip if we're inside a JOIN subquery (don't capture table names until subquery closes)
        if (!inJoinSubquery && identifiersAfterJoin == 1) {
          appendTargetToSummary();
          expectingJoinTableName = false;
          expectingTableName = false;
          identifiersAfterJoin = 0;
          expectingSubqueryOrTable = false;
          return;
        }
      }

      if (!expectingTableName) {
        return;
      }

      // Skip identifiers if we're not at the FROM clause's paren level
      // This allows capturing tables from nested FROM clauses while skipping aliases
      if (fromClauseParenLevel >= 0 && parenLevel != fromClauseParenLevel) {
        return;
      }

      appendTargetToSummary();
      mainTableSetAlready = true;
      expectingTableName = false;
      expectingSubqueryOrTable = false;
      // start counting identifiers after encountering main from clause
      identifiersAfterMainFromClause = 1;
    }

    void handleComma() {
      // Don't process commas if we're inside a column alias list
      if (inColumnAliasList) {
        return;
      }

      // Reset sawAsKeyword - comma indicates we're not entering a column alias list
      sawAsKeyword = false;

      // comma was encountered in the FROM clause, i.e. implicit join
      // (if less than 3 identifiers have appeared before first comma then it means that it's a table list;
      // any other list that can appear later needs at least 4 idents)
      // Only treat comma as table separator if we're at the same paren level as the FROM clause
      if (fromClauseParenLevel >= 0 && parenLevel == fromClauseParenLevel
          && identifiersAfterMainFromClause > 0
          && identifiersAfterMainFromClause <= FROM_TABLE_REF_MAX_IDENTIFIERS) {
        // Expect next table name after comma in FROM clause
        inImplicitJoin = true;
        expectingTableName = true;
        identifiersAfterMainFromClause = 0;
        identifiersAfterComma = 0;
      }
    }
  }

  private class Insert extends Operation {
    boolean expectingTableName = false;
    boolean tableCaptured = false;
    boolean inEmbeddedSelect = false;
    boolean expectingSelectTableName = false;
    int selectParenLevel = -1;

    void handleInto() {
      expectingTableName = true;
    }

    void handleIdentifier() {
      if (expectingTableName && !tableCaptured) {
        appendTargetToSummary();
        tableCaptured = true;
        expectingTableName = false;
        return;
      }

      if (inEmbeddedSelect && expectingSelectTableName && parenLevel == selectParenLevel) {
        appendTargetToSummary();
        expectingSelectTableName = false;
      }
    }

    void handleSelect() {
      inEmbeddedSelect = true;
      selectParenLevel = parenLevel;
      appendOperationToSummary("SELECT");
    }

    void handleFrom() {
      if (inEmbeddedSelect) {
        expectingSelectTableName = true;
      }
    }
  }

  private class Delete extends Operation {
    boolean expectingTableName = false;
    boolean tableCaptured = false;
    boolean inEmbeddedSelect = false;
    boolean expectingSelectTableName = false;
    int selectParenLevel = -1;

    void handleFrom() {
      if (!inEmbeddedSelect) {
        expectingTableName = true;
      } else {
        expectingSelectTableName = true;
      }
    }

    void handleIdentifier() {
      if (expectingTableName && !tableCaptured) {
        appendTargetToSummary();
        tableCaptured = true;
        expectingTableName = false;
        return;
      }

      if (inEmbeddedSelect && expectingSelectTableName && parenLevel == selectParenLevel) {
        appendTargetToSummary();
        expectingSelectTableName = false;
      }
    }

    void handleSelect() {
      inEmbeddedSelect = true;
      selectParenLevel = parenLevel;
      appendOperationToSummary("SELECT");
    }
  }

  /** Operation that extracts the first identifier as the target. */
  private class SimpleOperation extends Operation {
    boolean identifierCaptured = false;

    void handleIdentifier() {
      if (!identifierCaptured) {
        appendTargetToSummary();
        identifierCaptured = true;
      }
    }
  }

  private class Update extends Operation {
    boolean identifierCaptured = false;
    boolean inEmbeddedSelect = false;
    boolean expectingSelectTableName = false;
    int selectParenLevel = -1;

    void handleIdentifier() {
      if (!identifierCaptured && !inEmbeddedSelect) {
        appendTargetToSummary();
        identifierCaptured = true;
        return;
      }

      if (inEmbeddedSelect && expectingSelectTableName && parenLevel == selectParenLevel) {
        appendTargetToSummary();
        expectingSelectTableName = false;
      }
    }

    void handleSelect() {
      inEmbeddedSelect = true;
      selectParenLevel = parenLevel;
      appendOperationToSummary("SELECT");
    }

    void handleFrom() {
      if (inEmbeddedSelect) {
        expectingSelectTableName = true;
      }
    }
  }

  private class Merge extends SimpleOperation {}

  private class Call extends SimpleOperation {
    private boolean sawNextKeyword = false;

    void handleNext() {
      sawNextKeyword = true;
    }

    void handleIdentifier() {
      if (!identifierCaptured) {
        appendTargetToSummary();
        identifierCaptured = true;
        // Only set storedProcedureName if this is a real procedure call (not "call next value for sequence").
        // Hibernate uses "call next value for sequence" on HSQLDB and H2 to get sequence values,
        // while most other databases use SELECT for this.
        if (!sawNextKeyword) {
          storedProcedureName = yytext();
        }
      }
    }
  }

  /** VALUES operation - no table to capture. */
  private class Values extends Operation {}

  /** EXECUTE/EXEC operation for stored procedures. */
  private class Execute extends SimpleOperation {
    void handleIdentifier() {
      if (!identifierCaptured) {
        appendTargetToSummary();
        identifierCaptured = true;
        storedProcedureName = yytext();
      }
    }
  }

  private class Create extends DdlOperation {}
  private class Drop extends DdlOperation {}
  private class Alter extends DdlOperation {}

  private SqlStatementInfo getResult() {
    if (builder.length() > LIMIT) {
      builder.delete(LIMIT, builder.length());
    }
    String fullStatement = builder.toString();

    // Normalize all 'in (?, ?, ...)' statements to in (?) to reduce cardinality
    String normalizedStatement = IN_STATEMENT_PATTERN.matcher(fullStatement).replaceAll(IN_STATEMENT_NORMALIZED);

    String summary = querySummaryBuilder.length() > 0 ? querySummaryBuilder.toString() : null;
    return SqlStatementInfo.createWithSummary(normalizedStatement, storedProcedureName, summary);
  }

%}

%%

<YYINITIAL> {

  "SELECT" {
          if (!insideComment) {
            if (operation == none) {
              setOperation(new Select());
              appendOperationToSummary("SELECT");
            } else if (operation instanceof Select) {
              // nested SELECT (subquery) - append SELECT to summary
              appendOperationToSummary("SELECT");
            }
            operation.handleSelect();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "INSERT" {
          if (shouldStartNewOperation()) {
            setOperation(new Insert());
            appendOperationToSummary("INSERT");
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "DELETE" {
          if (shouldStartNewOperation()) {
            setOperation(new Delete());
            appendOperationToSummary("DELETE");
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "UPDATE" {
          if (shouldStartNewOperation()) {
            setOperation(new Update());
            appendOperationToSummary("UPDATE");
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "CALL" {
          if (shouldStartNewOperation()) {
            setOperation(new Call());
            appendOperationToSummary("CALL");
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "MERGE" {
          if (shouldStartNewOperation()) {
            setOperation(new Merge());
            appendOperationToSummary("MERGE");
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "CREATE" {
          if (shouldStartNewOperation()) {
            setOperation(new Create());
            appendOperationToSummary("CREATE");
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "DROP" {
          if (shouldStartNewOperation()) {
            setOperation(new Drop());
            appendOperationToSummary("DROP");
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "ALTER" {
          if (shouldStartNewOperation()) {
            setOperation(new Alter());
            appendOperationToSummary("ALTER");
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "VALUES" {
          if (shouldStartNewOperation()) {
            setOperation(new Values());
            appendOperationToSummary("VALUES");
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "EXECUTE" | "EXEC" {
          if (shouldStartNewOperation()) {
            setOperation(new Execute());
            appendOperationToSummary(yytext());
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "CONNECT" {
          appendCurrentFragment();
          // sanitize SAP HANA CONNECT statement
          // https://help.sap.com/docs/SAP_HANA_PLATFORM/4fe29514fd584807ac9f2a04f6754767/20d3b9ad751910148cdccc8205563a87.html?locale=en-US
          // we check that operation is not set to avoid triggering sanitization when a field named
          // connect is used or CONNECT BY clause is used in a SELECT statement
          if (!insideComment && operation == none) {
            // CONNECT statement could contain an unquoted password. We are not going to try
            // figuring out whether that is the case or not, just sanitize the whole statement.
            builder.append(" ?");
            return YYEOF;
          }
          if (isOverLimit()) return YYEOF;
      }
  "FROM" {
          if (!insideComment) {
            if (operation == none) {
              // hql/jpql queries may skip SELECT and start with FROM clause
              // treat such queries as SELECT queries (but don't add SELECT to summary)
              setOperation(new Select());
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
  "APPLY" {
          if (!insideComment && operation instanceof Select) {
            ((Select) operation).handleApply();
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
  "AS" {
          if (!insideComment) {
            operation.handleAs();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  ";" {
          if (!insideComment) {
            // Append semicolon to summary to separate statements
            querySummaryBuilder.append(';');
            // Reset operation state for next statement
            operation = none;
            parenLevel = 0;
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "UNION" | "INTERSECT" | "EXCEPT" | "MINUS" {
          if (!insideComment) {
            // Reset operation so next SELECT starts fresh
            operation = none;
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
            // CREATE USER and ALTER USER statements could contain an unquoted password. We are not
            // going to try figuring out whether that is the case or not, just sanitize the whole
            // statement.
            // https://docs.oracle.com/cd/B13789_01/server.101/b10759/statements_8003.htm
            // https://help.sap.com/docs/SAP_HANA_PLATFORM/4fe29514fd584807ac9f2a04f6754767/20d3b9ad751910148cdccc8205563a87.html?locale=en-US
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
            parenLevel += 1;
            operation.handleOpenParen();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  {CLOSE_PAREN} {
          if (!insideComment) {
            parenLevel -= 1;
            operation.handleCloseParen();
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
