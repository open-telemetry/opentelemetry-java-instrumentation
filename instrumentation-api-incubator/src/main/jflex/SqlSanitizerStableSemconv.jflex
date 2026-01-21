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
BACKTICK_QUOTED_STR  = "`" ("``" | [^`])* "`"
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
      return SqlStatementInfo.createStableSemconv(null, null, null);
    }
  }

  static final int LIMIT = 32 * 1024;

  private static final Pattern IN_STATEMENT_PATTERN = Pattern.compile("(\\sIN\\s*)\\(\\s*\\?\\s*(?:,\\s*\\?\\s*)*+\\)", Pattern.CASE_INSENSITIVE);
  private static final String IN_STATEMENT_NORMALIZED = "$1(?)";

  private final StringBuilder builder = new StringBuilder();
  private final StringBuilder querySummaryBuilder = new StringBuilder();
  private String storedProcedureName = null;

  private boolean insideComment = false;
  private int parenLevel = 0;
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
    void handleApply() {}
    void handleIdentifier() {}
    void handleComma() {}
    void handleOpenParen() {}
    void handleCloseParen() {}
    void handleSelect() {}
    void handleNext() {}
    void handleAs() {}
    void handleOperationTarget(String target) {}
    boolean expectingOperationTarget() { return false; }
  }

  private static class NoOp extends Operation {
    static final Operation INSTANCE = new NoOp();
  }

  private abstract class DdlOperation extends Operation {
    boolean expectingOperationTarget = true;
    boolean identifierCaptured = false;
    // For CREATE VIEW, track the embedded SELECT state
    boolean inEmbeddedSelect = false;
    boolean expectingTableName = false;
    int identifiersAfterFromClause = 0;

    boolean expectingOperationTarget() { return expectingOperationTarget; }

    void handleOperationTarget(String target) {
      appendOperationToSummary(target);
      expectingOperationTarget = false;
    }

    void handleIdentifier() {
      if (!expectingOperationTarget && !identifierCaptured) {
        appendTargetToSummary();
        identifierCaptured = true;
      } else if (inEmbeddedSelect && expectingTableName) {
        // Capture table name in embedded SELECT (e.g., CREATE VIEW ... AS SELECT ... FROM table)
        appendTargetToSummary();
        expectingTableName = false;
        identifiersAfterFromClause = 1;
      } else if (inEmbeddedSelect && identifiersAfterFromClause > 0) {
        // Track identifiers for alias detection
        ++identifiersAfterFromClause;
      }
    }

    void handleSelect() {
      appendOperationToSummary("SELECT");
      inEmbeddedSelect = true;
      expectingTableName = false;
      identifiersAfterFromClause = 0;
    }

    void handleFrom() {
      if (inEmbeddedSelect) {
        expectingTableName = true;
      }
    }
  }

  private class WithCte extends Operation {
    // Tracks whether we're inside a CTE definition (between AS ( and the matching ))
    boolean inCteDefinition = false;
    // The paren level when we entered the CTE definition
    int cteDefinitionStartParenLevel = 0;
    // Tracks whether we saw AS and are expecting the open paren
    boolean expectingCteOpenParen = false;
    // Tracks whether we're expecting a CTE name (right after WITH or comma)
    boolean expectingCteName = true;
    // Tracks whether we've transitioned to the main query
    boolean inMainQuery = false;
    // Select-like state for handling FROM clause inside CTE or main query
    boolean expectingTableName = false;
    int identifiersAfterFromClause = 0;
    boolean inImplicitJoin = false;
    int identifiersAfterComma = 0;
    boolean expectingJoinTableName = false;
    int identifiersAfterJoin = 0;
    boolean inJoinSubquery = false;
    // Track CTE names to avoid capturing recursive references
    java.util.Set<String> cteNames = new java.util.HashSet<>();
    // Current CTE name being defined
    String currentCteName = null;

    void handleAs() {
      if (!inCteDefinition && !inMainQuery) {
        expectingCteOpenParen = true;
      }
    }

    void handleOpenParen() {
      if (expectingCteOpenParen) {
        // Entering CTE definition
        inCteDefinition = true;
        cteDefinitionStartParenLevel = parenLevel;
        expectingCteOpenParen = false;
        expectingCteName = false;
        // Add the current CTE name to our set of known CTEs
        if (currentCteName != null) {
          cteNames.add(currentCteName.toLowerCase(java.util.Locale.ROOT));
          currentCteName = null;
        }
      }
    }

    void handleCloseParen() {
      if (inCteDefinition && parenLevel < cteDefinitionStartParenLevel) {
        // Exiting CTE definition
        inCteDefinition = false;
        // After closing a CTE, we might see comma (more CTEs) or the main query
        expectingCteName = true;
        resetSelectState();
      }
    }

    void handleComma() {
      if (!inCteDefinition && !inMainQuery) {
        // Comma between CTEs - expect another CTE name
        expectingCteName = true;
      } else if (inCteDefinition || inMainQuery) {
        // Handle comma in FROM clause (implicit join)
        if (identifiersAfterFromClause > 0
            && identifiersAfterFromClause <= FROM_TABLE_REF_MAX_IDENTIFIERS) {
          inImplicitJoin = true;
          identifiersAfterComma = 0;
        } else if (inImplicitJoin) {
          identifiersAfterComma = 0;
        }
      }
    }

    void handleSelect() {
      if (!inCteDefinition && !inMainQuery) {
        // This is the main query's SELECT
        inMainQuery = true;
      }
      // Append SELECT for every SELECT we encounter (CTEs or main query)
      appendOperationToSummary("SELECT");
      resetSelectState();
    }

    void handleFrom() {
      expectingTableName = true;
    }

    void handleJoin() {
      expectingJoinTableName = true;
      identifiersAfterJoin = 0;
    }

    private boolean isCteName(String identifier) {
      return cteNames.contains(identifier.toLowerCase(java.util.Locale.ROOT));
    }

    void handleIdentifier() {
      String identifier = yytext();
      
      if (expectingCteName && !inCteDefinition && !inMainQuery) {
        // This is a CTE name being defined, remember it but don't add to summary
        currentCteName = identifier;
        expectingCteName = false;
        return;
      }

      // Handle JOIN table
      if (expectingJoinTableName) {
        ++identifiersAfterJoin;
        if (identifiersAfterJoin == 1) {
          // Only capture if it's not a recursive CTE reference inside a CTE definition
          if (!inCteDefinition || !isCteName(identifier)) {
            appendTargetToSummary();
          }
        }
        if (identifiersAfterJoin >= FROM_TABLE_REF_MAX_IDENTIFIERS) {
          expectingJoinTableName = false;
        }
        return;
      }

      // Handle implicit join (comma-separated tables)
      if (inImplicitJoin) {
        ++identifiersAfterComma;
        if (identifiersAfterComma == 1) {
          appendTargetToSummary();
        }
        return;
      }

      // Handle table after FROM
      if (expectingTableName) {
        if (identifiersAfterFromClause > 0) {
          ++identifiersAfterFromClause;
        }
        appendTargetToSummary();
        expectingTableName = false;
        identifiersAfterFromClause = 1;
        return;
      }

      // Track identifiers after first table (for alias detection)
      if (identifiersAfterFromClause > 0) {
        ++identifiersAfterFromClause;
      }
    }

    private void resetSelectState() {
      expectingTableName = false;
      identifiersAfterFromClause = 0;
      inImplicitJoin = false;
      identifiersAfterComma = 0;
      expectingJoinTableName = false;
      identifiersAfterJoin = 0;
      inJoinSubquery = false;
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
    boolean inJoinSubquery = false;
    // Track if we may have a subquery after FROM (when open paren follows FROM)
    boolean expectingSubqueryOrTable = false;
    // Track if we're expecting a table name after APPLY
    boolean expectingApplyTableName = false;
    int identifiersAfterApply = 0;
    // Track the paren level when we captured the first table from FROM clause
    // Only capture additional identifiers (for implicit joins) at this same level
    int fromClauseParenLevel = -1;
    // Track if we just saw AS keyword and are expecting potential column alias list
    boolean sawAsKeyword = false;
    // Track if we're inside a column alias list (after derived table AS alias (...))
    boolean inColumnAliasList = false;
    int columnAliasListParenLevel = -1;

    void handleFrom() {
      // If we're in a join subquery, this FROM belongs to the subquery
      if (inJoinSubquery) {
        expectingTableName = true;
        inJoinSubquery = false;
        return;
      }
      expectingTableName = true;
      // If we're at paren level 0, we might see a subquery next
      if (parenLevel == 0) {
        expectingSubqueryOrTable = true;
      }
    }

    void handleJoin() {
      sawAsKeyword = false;
      expectingJoinTableName = true;
      identifiersAfterJoin = 0;
    }

    void handleApply() {
      sawAsKeyword = false;
      // SQL Server CROSS APPLY / OUTER APPLY - expect table name or table-valued function
      expectingApplyTableName = true;
      identifiersAfterApply = 0;
    }

    void handleAs() {
      // Mark that we saw AS - next identifier is alias, then open paren might be column alias list
      sawAsKeyword = true;
    }

    void handleIdentifier() {
      // Don't capture identifiers if we're inside a column alias list
      if (inColumnAliasList) {
        return;
      }

      // If we saw AS, this identifier is the table/subquery alias - keep sawAsKeyword true
      // so we can detect the column alias list paren that might follow
      // (sawAsKeyword will be reset when we see open paren or something else)

      // Only increment identifiersAfterFromClause if we're at the same paren level as FROM clause
      if (identifiersAfterFromClause > 0 && parenLevel == fromClauseParenLevel) {
        ++identifiersAfterFromClause;
      }

      // Handle APPLY table (table-valued function)
      if (expectingApplyTableName) {
        ++identifiersAfterApply;
        if (identifiersAfterApply == 1) {
          appendTargetToSummary();
        }
        if (identifiersAfterApply >= FROM_TABLE_REF_MAX_IDENTIFIERS) {
          expectingApplyTableName = false;
        }
        return;
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
      expectingSubqueryOrTable = false;
      identifiersAfterFromClause = 1;
      fromClauseParenLevel = parenLevel;
    }

    void handleComma() {
      // Don't process commas if we're inside a column alias list
      if (inColumnAliasList) {
        return;
      }

      // Reset sawAsKeyword - comma indicates we're not entering a column alias list
      sawAsKeyword = false;

      // Only treat comma as table separator if we're at the same paren level as FROM clause
      if (identifiersAfterFromClause > 0
          && identifiersAfterFromClause <= FROM_TABLE_REF_MAX_IDENTIFIERS
          && parenLevel == fromClauseParenLevel) {
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
      // If we're expecting a join table name and see SELECT, it's a subquery in JOIN
      if (expectingJoinTableName) {
        inJoinSubquery = true;
        expectingJoinTableName = false;
      }
      appendOperationToSummary("SELECT");
      // Reset state for the new SELECT (subquery)
      expectingTableName = false;
      expectingSubqueryOrTable = false;
      identifiersAfterFromClause = 0;
      inImplicitJoin = false;
      identifiersAfterComma = 0;
      identifiersAfterJoin = 0;
      expectingApplyTableName = false;
      identifiersAfterApply = 0;
    }

    void handleOpenParen() {
      // If we just saw AS keyword and an open paren, this is a column alias list
      if (sawAsKeyword) {
        inColumnAliasList = true;
        columnAliasListParenLevel = parenLevel;
        sawAsKeyword = false;
        return;
      }

      // If we're expecting a table/subquery and see open paren, a subquery may follow
      if (expectingTableName && expectingSubqueryOrTable) {
        // Don't capture table name yet - wait to see if it's a subquery
        expectingTableName = false;
      }
    }

    void handleCloseParen() {
      // If we're closing the column alias list paren, exit that state
      // Note: parenLevel has already been decremented when this is called
      if (inColumnAliasList && parenLevel < columnAliasListParenLevel) {
        inColumnAliasList = false;
        columnAliasListParenLevel = -1;
      }
    }

    void resetForSetOperator() {
      afterSetOperator = true;
      expectingTableName = false;
      expectingSubqueryOrTable = false;
      identifiersAfterFromClause = 0;
      inImplicitJoin = false;
      identifiersAfterComma = 0;
      expectingJoinTableName = false;
      identifiersAfterJoin = 0;
      expectingApplyTableName = false;
      identifiersAfterApply = 0;
      fromClauseParenLevel = -1;
      sawAsKeyword = false;
      inColumnAliasList = false;
      columnAliasListParenLevel = -1;
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
    boolean identifierCaptured = false;

    void handleFrom() {
      expectingTableName = true;
    }

    void handleSelect() {
      // Once we've captured the DELETE table, any SELECT is a subquery
      if (identifierCaptured) {
        operation = new Select();
        appendOperationToSummary("SELECT");
      }
    }

    void handleIdentifier() {
      if (expectingTableName) {
        appendTargetToSummary();
        expectingTableName = false;
        identifierCaptured = true;
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
    // Track "NEXT VALUE FOR sequence" pattern - sequence name comes after FOR
    boolean sawNext = false;
    boolean sawValue = false;
    boolean expectingSequenceName = false;

    void handleIdentifier() {
      if (expectingSequenceName) {
        // This is the sequence name after "NEXT VALUE FOR"
        appendTargetToSummary();
        expectingSequenceName = false;
        identifierCaptured = true;
      } else if (!identifierCaptured && !sawNext) {
        storedProcedureName = yytext();
        appendTargetToSummary();
        identifierCaptured = true;
      } else if (sawNext && !sawValue && yytext().equalsIgnoreCase("value")) {
        // This is "VALUE" in "NEXT VALUE FOR"
        sawValue = true;
      } else if (sawValue && yytext().equalsIgnoreCase("for")) {
        // This is "FOR" in "NEXT VALUE FOR" - expect sequence name next
        expectingSequenceName = true;
      }
    }

    void handleNext() {
      sawNext = true;
      storedProcedureName = null;
    }
  }

  private class Execute extends Operation {
    boolean identifierCaptured = false;

    void handleIdentifier() {
      if (!identifierCaptured) {
        storedProcedureName = yytext();
        appendTargetToSummary();
        identifierCaptured = true;
      }
    }
  }

  private class Update extends Operation {
    boolean identifierCaptured = false;

    void handleSelect() {
      // Once we've captured the UPDATE table, any SELECT is a subquery
      if (identifierCaptured) {
        operation = new Select();
        appendOperationToSummary("SELECT");
      }
    }

    void handleIdentifier() {
      if (!identifierCaptured) {
        appendTargetToSummary();
        identifierCaptured = true;
      }
    }
  }
  private class Merge extends SimpleOperation {}
  private class Create extends DdlOperation {}
  private class Drop extends DdlOperation {}
  private class Alter extends DdlOperation {}

  // Standalone VALUES clause (e.g., VALUES (1, 'a'), (2, 'b') in PostgreSQL)
  private class Values extends Operation {
    // No table to capture, VALUES is the entire summary
  }

  private SqlStatementInfo getResult() {
    if (builder.length() > LIMIT) {
      builder.delete(LIMIT, builder.length());
    }
    String fullStatement = builder.toString();
    String normalizedStatement = IN_STATEMENT_PATTERN.matcher(fullStatement).replaceAll(IN_STATEMENT_NORMALIZED);
    String querySummary = querySummaryBuilder.length() > 0 ? querySummaryBuilder.toString() : null;
    // Remove trailing semicolon if present (no statement after last ;)
    if (querySummary != null && querySummary.endsWith(";")) {
      querySummary = querySummary.substring(0, querySummary.length() - 1);
    }
    return SqlStatementInfo.createStableSemconv(normalizedStatement, storedProcedureName, querySummary);
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
  "VALUES" {
          if (!insideComment && operation == NoOp.INSTANCE) {
            operation = new Values();
            appendOperationToSummary("VALUES");
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
  "EXECUTE" | "EXEC" {
          if (!insideComment && operation == NoOp.INSTANCE) {
            operation = new Execute();
            appendOperationToSummary("EXECUTE");
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
  "WITH" {WHITESPACE} "CHECK" {WHITESPACE} "OPTION" {
          // View clause modifier - should not trigger CTE handling
          // Replace whitespace with single spaces for consistency
          builder.append("WITH CHECK OPTION");
          if (isOverLimit()) return YYEOF;
      }
  "WITH" {
          if (!insideComment && operation == NoOp.INSTANCE) {
            operation = new WithCte();
            // Don't append WITH to summary - we'll append SELECT when we see it
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "RECURSIVE" {
          // Just skip - modifier for WITH, don't need special handling
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "AS" {
          if (!insideComment) {
            if (operation instanceof WithCte || operation instanceof Select) {
              operation.handleAs();
            }
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
  "APPLY" {
          // SQL Server CROSS APPLY / OUTER APPLY - treat like JOIN
          if (!insideComment) {
            operation.handleApply();
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
            parenLevel++;
            operation.handleOpenParen();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  {CLOSE_PAREN} {
          if (!insideComment) {
            parenLevel--;
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

  ";" {
          if (!insideComment) {
            // Statement separator - reset operation for next statement
            // Only append separator if we have content and more might follow
            if (querySummaryBuilder.length() > 0) {
              querySummaryBuilder.append(';');
            }
            operation = NoOp.INSTANCE;
            parenLevel = 0;
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
