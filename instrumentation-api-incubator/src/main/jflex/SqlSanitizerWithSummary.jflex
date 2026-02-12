/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

%%

%final
%class AutoSqlSanitizerWithSummary
%apiprivate
%int
%buffer 2048

%unicode
%ignorecase

%state DOLLAR_STRING

COMMA                = ","
OPEN_PAREN           = "("
CLOSE_PAREN          = ")"
OPEN_COMMENT         = "/*"
CLOSE_COMMENT        = "*/"
LINE_COMMENT         = "--" [^\r\n]*
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
DOLLAR_TAG_START     = "$" {UNQUOTED_IDENTIFIER} "$"
BACKTICK_QUOTED_STR  = "`" ("``" | [^`])* "`"
BRACKET_QUOTED_STR   = "[" [^\]]* "]"
POSTGRE_PARAM_MARKER = "$"[0-9]*
WHITESPACE           = [ \t\r\n]+

%{
  static SqlQuery sanitize(String statement, SqlDialect dialect) {
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
      return SqlQuery.createWithSummary(null, null, null);
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
  private String dollarTag = null;

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

  // Global set of CTE names defined in the current statement (for filtering CTE references)
  private final Set<String> cteNames = new HashSet<>();

  // Stack to save outer operations when entering subqueries
  private final ArrayDeque<Operation> operationStack = new ArrayDeque<>();
  // Track the paren levels where we pushed operations (to know when to pop)
  private final ArrayDeque<Integer> subqueryStartLevels = new ArrayDeque<>();

  // Pending subquery: when we see ( that might start a subquery, we don't push immediately.
  // Instead, we wait to see if an operation keyword (SELECT, etc.) appears inside.
  // If it does, we do the push. If we see an identifier first, it's a parenthesized table name.
  private boolean pendingSubqueryPush = false;

  private boolean shouldStartNewOperation() {
    return !insideComment && operation == none;
  }

  /** Returns true if we should start a main query operation (no operation yet, or With at main query level). */
  private boolean shouldStartMainOperation() {
    return !insideComment
        && (operation == none || (operation instanceof With && ((With) operation).isMainQueryLevel()));
  }

  private void setOperation(Operation operation) {
    this.operation = operation;
  }

  /** Push current operation onto stack and reset to none for subquery processing. */
  private void pushOperation() {
    operationStack.push(operation);
    subqueryStartLevels.push(parenLevel);
    operation = none;
  }

  /** Called when an operation keyword is seen - confirms pending subquery if any. */
  private void confirmPendingSubqueryIfNeeded() {
    if (pendingSubqueryPush) {
      pushOperation();
      pendingSubqueryPush = false;
    }
  }

  /** Called when an identifier is seen - cancels pending subquery (it's a parenthesized table name). */
  private void cancelPendingSubqueryIfNeeded() {
    pendingSubqueryPush = false;
  }

  /** Pop operation from stack if we're exiting a subquery. */
  private void popOperationIfNeeded() {
    // Cancel pending subquery - any ) while pending means exiting the potential subquery
    pendingSubqueryPush = false;
    if (!subqueryStartLevels.isEmpty() && parenLevel < subqueryStartLevels.peek()) {
      subqueryStartLevels.pop();
      operation = operationStack.pop();
      // Signal to the restored operation that a subquery was processed
      operation.handleSubqueryComplete();
    }
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
    /** Returns true if open paren should start a subquery context. */
    boolean isEnteringSubquery() {
      return false;
    }
    /** Called after a subquery completes and this operation is restored. */
    void handleSubqueryComplete() {}
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
    // Max identifiers in a table reference: "table", "table alias", or "table as alias"
    private static final int TABLE_REF_MAX_IDENTIFIERS = 3;

    // Table capture modes (mutually exclusive)
    boolean captureTableList = false;   // FROM clause: capture tables, comma restarts capture
    boolean captureSingleTable = false; // JOIN clause: capture one table then stop

    // Counts identifiers since we started expecting a table (after FROM/JOIN/comma)
    // Used for: (1) capturing first identifier as table name, (2) detecting implicit joins via comma
    int identifierCount = 0;

    // FROM clause context - tracks paren level where FROM appeared (-1 if not in FROM clause)
    int fromClauseParenLevel = -1;

    // AS keyword tracking: set when AS seen, cleared after alias identifier or open paren
    boolean sawAsKeyword = false;

    // Column alias list paren level: >= 0 means inside "AS alias(col1, col2)" syntax
    int columnAliasListParenLevel = -1;

    void handleFrom() {
      // Enter table list capture mode for FROM clause
      captureTableList = true;
      // Track the paren level where FROM clause started
      // This allows nested SELECTs to track their own FROM clause level
      fromClauseParenLevel = parenLevel;
      identifierCount = 0;
      sawAsKeyword = false;
    }

    void handleJoin() {
      // Enter single table capture mode for JOIN clause
      captureSingleTable = true;
      identifierCount = 0;
      sawAsKeyword = false;
    }

    void handleApply() {
      // APPLY is similar to JOIN in SQL Server (CROSS APPLY, OUTER APPLY)
      captureSingleTable = true;
      identifierCount = 0;
      sawAsKeyword = false;
    }

    void handleAs() {
      // Mark that we saw AS keyword
      // Next identifier is an alias, and open paren after alias might be column alias list
      sawAsKeyword = true;
    }

    boolean isEnteringSubquery() {
      // Entering subquery if we're in table capture mode and haven't seen an identifier yet
      // sawAsKeyword check prevents "AS alias(" from being treated as subquery
      return !sawAsKeyword && (captureTableList || captureSingleTable) && identifierCount == 0;
    }

    void handleOpenParen() {
      // "AS alias(" starts a column alias list for derived tables
      // e.g., SELECT * FROM (SELECT a, b FROM t) AS sub(col1, col2)
      if (sawAsKeyword) {
        columnAliasListParenLevel = parenLevel;
        sawAsKeyword = false;
      }
      // Note: subquery push is handled at the global level before this is called
    }

    void handleCloseParen() {
      // Exit column alias list when its paren closes
      // Note: parenLevel has already been decremented when this is called
      if (columnAliasListParenLevel >= 0 && parenLevel < columnAliasListParenLevel) {
        columnAliasListParenLevel = -1;
      }
      // Note: subquery state restoration is handled at the global level via operation stack
    }

    void handleSubqueryComplete() {
      // A subquery counts as one table reference in the FROM clause
      captureTableList = false;
      captureSingleTable = false;
      if (identifierCount == 0) {
        identifierCount = 1;
      }
    }

    void handleSelect() {
      // Reset FROM clause tracking for nested SELECT (e.g., after UNION/INTERSECT/EXCEPT)
      // This prevents column commas from being treated as implicit joins
      // Called when SELECT keyword is encountered while already in a Select operation
      fromClauseParenLevel = -1;
      captureTableList = false;
      captureSingleTable = false;
      identifierCount = 0;
      sawAsKeyword = false;
      columnAliasListParenLevel = -1;
    }

    void handleIdentifier() {
      // Don't capture identifiers if we're inside a column alias list or after AS (alias name)
      if (columnAliasListParenLevel >= 0 || sawAsKeyword) {
        return;
      }

      ++identifierCount;

      // Check if this is a CTE reference (should be filtered from table list)
      boolean isCteReference = isCteReference(yytext());

      // Handle single table capture (JOIN): capture first identifier then stop
      if (captureSingleTable && identifierCount == 1) {
        if (!isCteReference) {
          appendTargetToSummary();
        }
        captureSingleTable = false;
        identifierCount = 0;
        return;
      }

      // Handle table list capture (FROM): capture first identifier after FROM or comma
      if (captureTableList && identifierCount == 1) {
        if (!isCteReference) {
          appendTargetToSummary();
        }
        captureTableList = false;
        // Don't reset identifierCount - keep counting for implicit join detection
      }
    }

    void handleComma() {
      // Don't process commas if we're inside a column alias list
      if (columnAliasListParenLevel >= 0) {
        return;
      }

      // Reset sawAsKeyword - comma indicates we're not entering a column alias list
      sawAsKeyword = false;

      // Detect implicit join: comma in FROM clause after seeing 1-3 identifiers (a table reference)
      // A table reference can be: "table", "table alias", or "table as alias" (max 3 identifiers)
      // If we see more than 3 identifiers before comma, it's not a table list (e.g., column list)
      // Only treat comma as table separator if we're at the same paren level as the FROM clause
      if (fromClauseParenLevel >= 0 && parenLevel == fromClauseParenLevel
          && identifierCount > 0
          && identifierCount <= TABLE_REF_MAX_IDENTIFIERS) {
        // Comma in FROM clause - restart table list capture for next table
        captureTableList = true;
        identifierCount = 0;
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

  /** TRUNCATE operation - captures TABLE keyword and table name. */
  private class Truncate extends Operation {
    boolean tableCaptured = false;
    boolean identifierCaptured = false;

    void handleIdentifier() {
      String text = yytext();
      // Include TABLE keyword in summary
      if (text.equalsIgnoreCase("TABLE")) {
        if (!tableCaptured) {
          appendTargetToSummary();
          tableCaptured = true;
        }
        return;
      }
      if (!identifierCaptured) {
        appendTargetToSummary();
        identifierCaptured = true;
      }
    }
  }

  /** REPLACE operation (MySQL) - like INSERT, captures table name. */
  private class Replace extends Operation {
    boolean expectingTableName = false;
    boolean tableCaptured = false;

    void handleInto() {
      expectingTableName = true;
    }

    void handleIdentifier() {
      if (!tableCaptured) {
        appendTargetToSummary();
        tableCaptured = true;
        expectingTableName = false;
      }
    }
  }

  /** LOCK operation - captures TABLE/TABLES keyword and table name. */
  private class Lock extends Operation {
    boolean tableCaptured = false;
    boolean identifierCaptured = false;

    void handleIdentifier() {
      String text = yytext();
      // Include TABLE/TABLES keywords in summary
      if (text.equalsIgnoreCase("TABLE") || text.equalsIgnoreCase("TABLES")) {
        if (!tableCaptured) {
          appendTargetToSummary();
          tableCaptured = true;
        }
        return;
      }
      if (!identifierCaptured) {
        appendTargetToSummary();
        identifierCaptured = true;
      }
    }
  }

  /** USE operation - captures database name. */
  private class Use extends SimpleOperation {}

  /** Transaction control operations (BEGIN, COMMIT, ROLLBACK) - captures TRANSACTION if present. */
  private class TransactionControl extends Operation {
    boolean transactionCaptured = false;

    void handleIdentifier() {
      // Capture TRANSACTION keyword if present (e.g., BEGIN TRANSACTION, COMMIT TRANSACTION)
      if (!transactionCaptured && yytext().equalsIgnoreCase("TRANSACTION")) {
        appendTargetToSummary();
        transactionCaptured = true;
      }
    }
  }

  /** GRANT operation - blocks other keywords from being parsed. */
  private class Grant extends Operation {}

  /** REVOKE operation - blocks other keywords from being parsed. */
  private class Revoke extends Operation {}

  /** SHOW operation - blocks other keywords from being parsed. */
  private class Show extends Operation {}

  /**
   * WITH clause operation - handles CTE (Common Table Expression) definitions.
   * CTEs have the form: WITH name AS (query), name2 AS (query2), ... main_query
   */
  private class With extends Operation {
    // State: true when expecting a CTE name (after WITH or after comma between CTEs)
    boolean expectingCteName = true;

    // The paren level when WITH was seen - used to detect main query vs CTE body
    final int baseParenLevel = parenLevel;

    void handleIdentifier() {
      if (expectingCteName) {
        // This is the CTE name - record it in global set for filtering CTE references
        cteNames.add(yytext().toLowerCase(Locale.ROOT));
        expectingCteName = false;
      }
    }

    boolean isEnteringSubquery() {
      // After we've captured a CTE name, ( starts the CTE body
      return !expectingCteName;
    }

    void handleComma() {
      // Comma at base level means another CTE definition follows
      if (isMainQueryLevel()) {
        expectingCteName = true;
      }
    }

    /** Returns true if we're at the main query level (outside any CTE body). */
    boolean isMainQueryLevel() {
      return parenLevel == baseParenLevel;
    }
  }

  /**
   * Check if an identifier is a CTE reference (should be filtered from table list).
   * Uses global cteNames set populated by With operation.
   */
  private boolean isCteReference(String identifier) {
    return cteNames.contains(identifier.toLowerCase(Locale.ROOT));
  }

  private SqlQuery getResult() {
    if (builder.length() > LIMIT) {
      builder.delete(LIMIT, builder.length());
    }
    String fullStatement = builder.toString();

    // Normalize all 'in (?, ?, ...)' statements to in (?) to reduce cardinality
    String normalizedStatement = IN_STATEMENT_PATTERN.matcher(fullStatement).replaceAll(IN_STATEMENT_NORMALIZED);

    String summary = querySummaryBuilder.length() > 0 ? querySummaryBuilder.toString() : null;
    // Remove trailing semicolon if present (no statement after last ;)
    if (summary != null && summary.endsWith(";")) {
      summary = summary.substring(0, summary.length() - 1);
    }

    return SqlQuery.createWithSummary(normalizedStatement, storedProcedureName, summary);
  }

%}

%%

<YYINITIAL> {

  "WITH" {
          if (shouldStartNewOperation()) {
            setOperation(new With());
            // Don't append WITH to summary - only the main query operation will be appended
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "RECURSIVE" {
          // Prevent RECURSIVE keyword from being captured as a CTE name
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "SELECT" {
          if (!insideComment) {
            // Confirm pending subquery if we see SELECT inside parens
            confirmPendingSubqueryIfNeeded();
            if (shouldStartMainOperation()) {
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
          if (shouldStartMainOperation()) {
            setOperation(new Insert());
            appendOperationToSummary("INSERT");
          } else if (!insideComment) {
            cancelPendingSubqueryIfNeeded();
            operation.handleIdentifier();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "DELETE" {
          if (shouldStartMainOperation()) {
            setOperation(new Delete());
            appendOperationToSummary("DELETE");
          } else if (!insideComment) {
            cancelPendingSubqueryIfNeeded();
            operation.handleIdentifier();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "UPDATE" {
          if (shouldStartMainOperation()) {
            setOperation(new Update());
            appendOperationToSummary("UPDATE");
          } else if (!insideComment) {
            cancelPendingSubqueryIfNeeded();
            operation.handleIdentifier();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "CALL" {
          if (shouldStartNewOperation()) {
            setOperation(new Call());
            appendOperationToSummary("CALL");
          } else if (!insideComment) {
            cancelPendingSubqueryIfNeeded();
            operation.handleIdentifier();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "MERGE" {
          if (shouldStartNewOperation()) {
            setOperation(new Merge());
            appendOperationToSummary("MERGE");
          } else if (!insideComment) {
            cancelPendingSubqueryIfNeeded();
            operation.handleIdentifier();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "CREATE" {
          if (shouldStartNewOperation()) {
            setOperation(new Create());
            appendOperationToSummary("CREATE");
          } else if (!insideComment) {
            cancelPendingSubqueryIfNeeded();
            operation.handleIdentifier();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "DROP" {
          if (shouldStartNewOperation()) {
            setOperation(new Drop());
            appendOperationToSummary("DROP");
          } else if (!insideComment) {
            cancelPendingSubqueryIfNeeded();
            operation.handleIdentifier();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "ALTER" {
          if (shouldStartNewOperation()) {
            setOperation(new Alter());
            appendOperationToSummary("ALTER");
          } else if (!insideComment) {
            cancelPendingSubqueryIfNeeded();
            operation.handleIdentifier();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "VALUES" {
          if (!insideComment) {
            // Confirm pending subquery if we see VALUES inside parens (e.g., CTE body)
            confirmPendingSubqueryIfNeeded();
            if (shouldStartNewOperation()) {
              setOperation(new Values());
              // Only append VALUES to summary if at top level (not inside a subquery or CTE body)
              if (operationStack.isEmpty()) {
                appendOperationToSummary("VALUES");
              }
            }
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "EXECUTE" | "EXEC" {
          if (shouldStartNewOperation()) {
            setOperation(new Execute());
            appendOperationToSummary(yytext());
          } else if (!insideComment) {
            cancelPendingSubqueryIfNeeded();
            operation.handleIdentifier();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "TRUNCATE" {
          if (shouldStartNewOperation()) {
            setOperation(new Truncate());
            appendOperationToSummary("TRUNCATE");
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "REPLACE" {
          if (shouldStartNewOperation()) {
            setOperation(new Replace());
            appendOperationToSummary("REPLACE");
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "LOCK" {
          if (shouldStartNewOperation()) {
            setOperation(new Lock());
            appendOperationToSummary("LOCK");
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "USE" {
          if (shouldStartNewOperation()) {
            setOperation(new Use());
            appendOperationToSummary("USE");
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "BEGIN" {
          if (shouldStartNewOperation()) {
            setOperation(new TransactionControl());
            appendOperationToSummary("BEGIN");
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "COMMIT" {
          if (shouldStartNewOperation()) {
            setOperation(new TransactionControl());
            appendOperationToSummary("COMMIT");
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "ROLLBACK" {
          if (shouldStartNewOperation()) {
            setOperation(new TransactionControl());
            appendOperationToSummary("ROLLBACK");
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "GRANT" {
          if (shouldStartNewOperation()) {
            setOperation(new Grant());
            appendOperationToSummary("GRANT");
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "REVOKE" {
          if (shouldStartNewOperation()) {
            setOperation(new Revoke());
            appendOperationToSummary("REVOKE");
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "SHOW" {
          if (shouldStartNewOperation()) {
            setOperation(new Show());
            appendOperationToSummary("SHOW");
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
              // treat such queries as SELECT queries
              setOperation(new Select());
              appendOperationToSummary("SELECT");
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
  "LATERAL" {
          // LATERAL is a keyword used before derived tables - just recognize it, don't capture as identifier
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
  "ONLY" {
          // PostgreSQL: FROM/UPDATE/DELETE ONLY - just skip, don't treat as table name
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  ";" {
          if (!insideComment) {
            // Statement separator - only append separator if we have content and not already ending with semicolon
            if (querySummaryBuilder.length() > 0
                && querySummaryBuilder.charAt(querySummaryBuilder.length() - 1) != ';') {
              querySummaryBuilder.append(';');
            }
            // Reset operation state for next statement
            operation = none;
            parenLevel = 0;
            operationStack.clear();
            subqueryStartLevels.clear();
            cteNames.clear();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "UNION" | "INTERSECT" | "EXCEPT" | "MINUS" {
          // UNION etc. don't reset operation - the next SELECT will be handled
          // by the existing operation via handleSelect(), or if at top level,
          // a new Select will be created.
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "IF" | "NOT" | "EXISTS" {
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  "TABLE" | "INDEX" | "DATABASE" | "PROCEDURE" | "VIEW" {
          if (!insideComment) {
            // If we see a reserved word where we expected a subquery, it's a parenthesized name
            cancelPendingSubqueryIfNeeded();
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
            // If we see an identifier where we expected a subquery, it's a parenthesized table name
            cancelPendingSubqueryIfNeeded();
            operation.handleIdentifier();
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }

  {OPEN_PAREN}  {
          if (!insideComment) {
            // Check if we're entering a subquery BEFORE incrementing parenLevel
            boolean enteringSubquery = operation.isEnteringSubquery();
            parenLevel += 1;
            if (enteringSubquery) {
              // Don't push immediately - mark as pending and wait to see if there's an operation keyword
              pendingSubqueryPush = true;
            } else {
              operation.handleOpenParen();
            }
          }
          appendCurrentFragment();
          if (isOverLimit()) return YYEOF;
      }
  {CLOSE_PAREN} {
          if (!insideComment) {
            parenLevel -= 1;
            operation.handleCloseParen();
            // Pop operation from stack if we're exiting a subquery
            popOperationIfNeeded();
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
          // Line comment - append as-is, don't process keywords or sanitize literals
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

  {DOLLAR_TAG_START} {
          // Start of a tagged dollar-quoted string like $tag$...$tag$
          dollarTag = yytext();
          yybegin(DOLLAR_STRING);
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

<DOLLAR_STRING> {
  {DOLLAR_TAG_START} {
          // Check if this is the closing tag
          if (yytext().equals(dollarTag)) {
            builder.append('?');
            dollarTag = null;
            yybegin(YYINITIAL);
            if (isOverLimit()) return YYEOF;
          }
          // else: different tag, continue consuming
      }

  "$" {
          // Single dollar sign, not part of a tag - continue consuming
      }

  [^$]+ {
          // Consume non-dollar characters
      }

  <<EOF>> {
          // Unterminated dollar-quoted string - output what we have as ?
          builder.append('?');
          return YYEOF;
      }
}
