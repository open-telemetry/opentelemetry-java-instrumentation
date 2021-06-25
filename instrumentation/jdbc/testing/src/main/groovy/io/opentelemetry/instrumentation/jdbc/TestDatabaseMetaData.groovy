/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test

import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.RowIdLifetime
import java.sql.SQLException

class TestDatabaseMetaData implements DatabaseMetaData {
  @Override
  boolean allProceduresAreCallable() throws SQLException {
    return false
  }

  @Override
  boolean allTablesAreSelectable() throws SQLException {
    return false
  }

  @Override
  String getURL() throws SQLException {
    return "jdbc:testdb://localhost"
  }

  @Override
  String getUserName() throws SQLException {
    return null
  }

  @Override
  boolean isReadOnly() throws SQLException {
    return false
  }

  @Override
  boolean nullsAreSortedHigh() throws SQLException {
    return false
  }

  @Override
  boolean nullsAreSortedLow() throws SQLException {
    return false
  }

  @Override
  boolean nullsAreSortedAtStart() throws SQLException {
    return false
  }

  @Override
  boolean nullsAreSortedAtEnd() throws SQLException {
    return false
  }

  @Override
  String getDatabaseProductName() throws SQLException {
    return null
  }

  @Override
  String getDatabaseProductVersion() throws SQLException {
    return null
  }

  @Override
  String getDriverName() throws SQLException {
    return null
  }

  @Override
  String getDriverVersion() throws SQLException {
    return null
  }

  @Override
  int getDriverMajorVersion() {
    return 0
  }

  @Override
  int getDriverMinorVersion() {
    return 0
  }

  @Override
  boolean usesLocalFiles() throws SQLException {
    return false
  }

  @Override
  boolean usesLocalFilePerTable() throws SQLException {
    return false
  }

  @Override
  boolean supportsMixedCaseIdentifiers() throws SQLException {
    return false
  }

  @Override
  boolean storesUpperCaseIdentifiers() throws SQLException {
    return false
  }

  @Override
  boolean storesLowerCaseIdentifiers() throws SQLException {
    return false
  }

  @Override
  boolean storesMixedCaseIdentifiers() throws SQLException {
    return false
  }

  @Override
  boolean supportsMixedCaseQuotedIdentifiers() throws SQLException {
    return false
  }

  @Override
  boolean storesUpperCaseQuotedIdentifiers() throws SQLException {
    return false
  }

  @Override
  boolean storesLowerCaseQuotedIdentifiers() throws SQLException {
    return false
  }

  @Override
  boolean storesMixedCaseQuotedIdentifiers() throws SQLException {
    return false
  }

  @Override
  String getIdentifierQuoteString() throws SQLException {
    return null
  }

  @Override
  String getSQLKeywords() throws SQLException {
    return null
  }

  @Override
  String getNumericFunctions() throws SQLException {
    return null
  }

  @Override
  String getStringFunctions() throws SQLException {
    return null
  }

  @Override
  String getSystemFunctions() throws SQLException {
    return null
  }

  @Override
  String getTimeDateFunctions() throws SQLException {
    return null
  }

  @Override
  String getSearchStringEscape() throws SQLException {
    return null
  }

  @Override
  String getExtraNameCharacters() throws SQLException {
    return null
  }

  @Override
  boolean supportsAlterTableWithAddColumn() throws SQLException {
    return false
  }

  @Override
  boolean supportsAlterTableWithDropColumn() throws SQLException {
    return false
  }

  @Override
  boolean supportsColumnAliasing() throws SQLException {
    return false
  }

  @Override
  boolean nullPlusNonNullIsNull() throws SQLException {
    return false
  }

  @Override
  boolean supportsConvert() throws SQLException {
    return false
  }

  @Override
  boolean supportsConvert(int fromType, int toType) throws SQLException {
    return false
  }

  @Override
  boolean supportsTableCorrelationNames() throws SQLException {
    return false
  }

  @Override
  boolean supportsDifferentTableCorrelationNames() throws SQLException {
    return false
  }

  @Override
  boolean supportsExpressionsInOrderBy() throws SQLException {
    return false
  }

  @Override
  boolean supportsOrderByUnrelated() throws SQLException {
    return false
  }

  @Override
  boolean supportsGroupBy() throws SQLException {
    return false
  }

  @Override
  boolean supportsGroupByUnrelated() throws SQLException {
    return false
  }

  @Override
  boolean supportsGroupByBeyondSelect() throws SQLException {
    return false
  }

  @Override
  boolean supportsLikeEscapeClause() throws SQLException {
    return false
  }

  @Override
  boolean supportsMultipleResultSets() throws SQLException {
    return false
  }

  @Override
  boolean supportsMultipleTransactions() throws SQLException {
    return false
  }

  @Override
  boolean supportsNonNullableColumns() throws SQLException {
    return false
  }

  @Override
  boolean supportsMinimumSQLGrammar() throws SQLException {
    return false
  }

  @Override
  boolean supportsCoreSQLGrammar() throws SQLException {
    return false
  }

  @Override
  boolean supportsExtendedSQLGrammar() throws SQLException {
    return false
  }

  @Override
  boolean supportsANSI92EntryLevelSQL() throws SQLException {
    return false
  }

  @Override
  boolean supportsANSI92IntermediateSQL() throws SQLException {
    return false
  }

  @Override
  boolean supportsANSI92FullSQL() throws SQLException {
    return false
  }

  @Override
  boolean supportsIntegrityEnhancementFacility() throws SQLException {
    return false
  }

  @Override
  boolean supportsOuterJoins() throws SQLException {
    return false
  }

  @Override
  boolean supportsFullOuterJoins() throws SQLException {
    return false
  }

  @Override
  boolean supportsLimitedOuterJoins() throws SQLException {
    return false
  }

  @Override
  String getSchemaTerm() throws SQLException {
    return null
  }

  @Override
  String getProcedureTerm() throws SQLException {
    return null
  }

  @Override
  String getCatalogTerm() throws SQLException {
    return null
  }

  @Override
  boolean isCatalogAtStart() throws SQLException {
    return false
  }

  @Override
  String getCatalogSeparator() throws SQLException {
    return null
  }

  @Override
  boolean supportsSchemasInDataManipulation() throws SQLException {
    return false
  }

  @Override
  boolean supportsSchemasInProcedureCalls() throws SQLException {
    return false
  }

  @Override
  boolean supportsSchemasInTableDefinitions() throws SQLException {
    return false
  }

  @Override
  boolean supportsSchemasInIndexDefinitions() throws SQLException {
    return false
  }

  @Override
  boolean supportsSchemasInPrivilegeDefinitions() throws SQLException {
    return false
  }

  @Override
  boolean supportsCatalogsInDataManipulation() throws SQLException {
    return false
  }

  @Override
  boolean supportsCatalogsInProcedureCalls() throws SQLException {
    return false
  }

  @Override
  boolean supportsCatalogsInTableDefinitions() throws SQLException {
    return false
  }

  @Override
  boolean supportsCatalogsInIndexDefinitions() throws SQLException {
    return false
  }

  @Override
  boolean supportsCatalogsInPrivilegeDefinitions() throws SQLException {
    return false
  }

  @Override
  boolean supportsPositionedDelete() throws SQLException {
    return false
  }

  @Override
  boolean supportsPositionedUpdate() throws SQLException {
    return false
  }

  @Override
  boolean supportsSelectForUpdate() throws SQLException {
    return false
  }

  @Override
  boolean supportsStoredProcedures() throws SQLException {
    return false
  }

  @Override
  boolean supportsSubqueriesInComparisons() throws SQLException {
    return false
  }

  @Override
  boolean supportsSubqueriesInExists() throws SQLException {
    return false
  }

  @Override
  boolean supportsSubqueriesInIns() throws SQLException {
    return false
  }

  @Override
  boolean supportsSubqueriesInQuantifieds() throws SQLException {
    return false
  }

  @Override
  boolean supportsCorrelatedSubqueries() throws SQLException {
    return false
  }

  @Override
  boolean supportsUnion() throws SQLException {
    return false
  }

  @Override
  boolean supportsUnionAll() throws SQLException {
    return false
  }

  @Override
  boolean supportsOpenCursorsAcrossCommit() throws SQLException {
    return false
  }

  @Override
  boolean supportsOpenCursorsAcrossRollback() throws SQLException {
    return false
  }

  @Override
  boolean supportsOpenStatementsAcrossCommit() throws SQLException {
    return false
  }

  @Override
  boolean supportsOpenStatementsAcrossRollback() throws SQLException {
    return false
  }

  @Override
  int getMaxBinaryLiteralLength() throws SQLException {
    return 0
  }

  @Override
  int getMaxCharLiteralLength() throws SQLException {
    return 0
  }

  @Override
  int getMaxColumnNameLength() throws SQLException {
    return 0
  }

  @Override
  int getMaxColumnsInGroupBy() throws SQLException {
    return 0
  }

  @Override
  int getMaxColumnsInIndex() throws SQLException {
    return 0
  }

  @Override
  int getMaxColumnsInOrderBy() throws SQLException {
    return 0
  }

  @Override
  int getMaxColumnsInSelect() throws SQLException {
    return 0
  }

  @Override
  int getMaxColumnsInTable() throws SQLException {
    return 0
  }

  @Override
  int getMaxConnections() throws SQLException {
    return 0
  }

  @Override
  int getMaxCursorNameLength() throws SQLException {
    return 0
  }

  @Override
  int getMaxIndexLength() throws SQLException {
    return 0
  }

  @Override
  int getMaxSchemaNameLength() throws SQLException {
    return 0
  }

  @Override
  int getMaxProcedureNameLength() throws SQLException {
    return 0
  }

  @Override
  int getMaxCatalogNameLength() throws SQLException {
    return 0
  }

  @Override
  int getMaxRowSize() throws SQLException {
    return 0
  }

  @Override
  boolean doesMaxRowSizeIncludeBlobs() throws SQLException {
    return false
  }

  @Override
  int getMaxStatementLength() throws SQLException {
    return 0
  }

  @Override
  int getMaxStatements() throws SQLException {
    return 0
  }

  @Override
  int getMaxTableNameLength() throws SQLException {
    return 0
  }

  @Override
  int getMaxTablesInSelect() throws SQLException {
    return 0
  }

  @Override
  int getMaxUserNameLength() throws SQLException {
    return 0
  }

  @Override
  int getDefaultTransactionIsolation() throws SQLException {
    return 0
  }

  @Override
  boolean supportsTransactions() throws SQLException {
    return false
  }

  @Override
  boolean supportsTransactionIsolationLevel(int level) throws SQLException {
    return false
  }

  @Override
  boolean supportsDataDefinitionAndDataManipulationTransactions() throws SQLException {
    return false
  }

  @Override
  boolean supportsDataManipulationTransactionsOnly() throws SQLException {
    return false
  }

  @Override
  boolean dataDefinitionCausesTransactionCommit() throws SQLException {
    return false
  }

  @Override
  boolean dataDefinitionIgnoredInTransactions() throws SQLException {
    return false
  }

  @Override
  ResultSet getProcedures(String catalog, String schemaPattern, String procedureNamePattern) throws SQLException {
    return null
  }

  @Override
  ResultSet getProcedureColumns(String catalog, String schemaPattern, String procedureNamePattern, String columnNamePattern) throws SQLException {
    return null
  }

  @Override
  ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types) throws SQLException {
    return null
  }

  @Override
  ResultSet getSchemas() throws SQLException {
    return null
  }

  @Override
  ResultSet getCatalogs() throws SQLException {
    return null
  }

  @Override
  ResultSet getTableTypes() throws SQLException {
    return null
  }

  @Override
  ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException {
    return null
  }

  @Override
  ResultSet getColumnPrivileges(String catalog, String schema, String table, String columnNamePattern) throws SQLException {
    return null
  }

  @Override
  ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern) throws SQLException {
    return null
  }

  @Override
  ResultSet getBestRowIdentifier(String catalog, String schema, String table, int scope, boolean nullable) throws SQLException {
    return null
  }

  @Override
  ResultSet getVersionColumns(String catalog, String schema, String table) throws SQLException {
    return null
  }

  @Override
  ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException {
    return null
  }

  @Override
  ResultSet getImportedKeys(String catalog, String schema, String table) throws SQLException {
    return null
  }

  @Override
  ResultSet getExportedKeys(String catalog, String schema, String table) throws SQLException {
    return null
  }

  @Override
  ResultSet getCrossReference(String parentCatalog, String parentSchema, String parentTable, String foreignCatalog, String foreignSchema, String foreignTable) throws SQLException {
    return null
  }

  @Override
  ResultSet getTypeInfo() throws SQLException {
    return null
  }

  @Override
  ResultSet getIndexInfo(String catalog, String schema, String table, boolean unique, boolean approximate) throws SQLException {
    return null
  }

  @Override
  boolean supportsResultSetType(int type) throws SQLException {
    return false
  }

  @Override
  boolean supportsResultSetConcurrency(int type, int concurrency) throws SQLException {
    return false
  }

  @Override
  boolean ownUpdatesAreVisible(int type) throws SQLException {
    return false
  }

  @Override
  boolean ownDeletesAreVisible(int type) throws SQLException {
    return false
  }

  @Override
  boolean ownInsertsAreVisible(int type) throws SQLException {
    return false
  }

  @Override
  boolean othersUpdatesAreVisible(int type) throws SQLException {
    return false
  }

  @Override
  boolean othersDeletesAreVisible(int type) throws SQLException {
    return false
  }

  @Override
  boolean othersInsertsAreVisible(int type) throws SQLException {
    return false
  }

  @Override
  boolean updatesAreDetected(int type) throws SQLException {
    return false
  }

  @Override
  boolean deletesAreDetected(int type) throws SQLException {
    return false
  }

  @Override
  boolean insertsAreDetected(int type) throws SQLException {
    return false
  }

  @Override
  boolean supportsBatchUpdates() throws SQLException {
    return false
  }

  @Override
  ResultSet getUDTs(String catalog, String schemaPattern, String typeNamePattern, int[] types) throws SQLException {
    return null
  }

  @Override
  Connection getConnection() throws SQLException {
    return null
  }

  @Override
  boolean supportsSavepoints() throws SQLException {
    return false
  }

  @Override
  boolean supportsNamedParameters() throws SQLException {
    return false
  }

  @Override
  boolean supportsMultipleOpenResults() throws SQLException {
    return false
  }

  @Override
  boolean supportsGetGeneratedKeys() throws SQLException {
    return false
  }

  @Override
  ResultSet getSuperTypes(String catalog, String schemaPattern, String typeNamePattern) throws SQLException {
    return null
  }

  @Override
  ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern) throws SQLException {
    return null
  }

  @Override
  ResultSet getAttributes(String catalog, String schemaPattern, String typeNamePattern, String attributeNamePattern) throws SQLException {
    return null
  }

  @Override
  boolean supportsResultSetHoldability(int holdability) throws SQLException {
    return false
  }

  @Override
  int getResultSetHoldability() throws SQLException {
    return 0
  }

  @Override
  int getDatabaseMajorVersion() throws SQLException {
    return 0
  }

  @Override
  int getDatabaseMinorVersion() throws SQLException {
    return 0
  }

  @Override
  int getJDBCMajorVersion() throws SQLException {
    return 0
  }

  @Override
  int getJDBCMinorVersion() throws SQLException {
    return 0
  }

  @Override
  int getSQLStateType() throws SQLException {
    return 0
  }

  @Override
  boolean locatorsUpdateCopy() throws SQLException {
    return false
  }

  @Override
  boolean supportsStatementPooling() throws SQLException {
    return false
  }

  @Override
  RowIdLifetime getRowIdLifetime() throws SQLException {
    return null
  }

  @Override
  ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException {
    return null
  }

  @Override
  boolean supportsStoredFunctionsUsingCallSyntax() throws SQLException {
    return false
  }

  @Override
  boolean autoCommitFailureClosesAllResultSets() throws SQLException {
    return false
  }

  @Override
  ResultSet getClientInfoProperties() throws SQLException {
    return null
  }

  @Override
  ResultSet getFunctions(String catalog, String schemaPattern, String functionNamePattern) throws SQLException {
    return null
  }

  @Override
  ResultSet getFunctionColumns(String catalog, String schemaPattern, String functionNamePattern, String columnNamePattern) throws SQLException {
    return null
  }

  @Override
  ResultSet getPseudoColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException {
    return null
  }

  @Override
  boolean generatedKeyAlwaysReturned() throws SQLException {
    return false
  }

  @Override
  def <T> T unwrap(Class<T> iface) throws SQLException {
    return null
  }

  @Override
  boolean isWrapperFor(Class<?> iface) throws SQLException {
    return false
  }
}
