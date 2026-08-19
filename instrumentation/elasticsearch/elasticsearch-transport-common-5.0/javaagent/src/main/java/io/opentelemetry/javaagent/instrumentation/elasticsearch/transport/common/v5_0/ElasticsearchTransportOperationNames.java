/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps Elasticsearch transport action classes to the Elasticsearch REST API names reported by the
 * Elasticsearch REST and api-client instrumentations, so that the same logical operation gets the
 * same {@code db.operation.name} no matter which client the application uses.
 *
 * <p>The table is keyed on the action class simple name, which is the identifier this
 * instrumentation already reports for an action in the {@code elasticsearch.action} attribute.
 * Referring to the action classes themselves is not an option, because this module is shared across
 * Elasticsearch 5.0 to 7.17 and many of these classes do not exist in every version in that range.
 *
 * <p>An action is deliberately absent, and so keeps its action class name, when it has no REST API
 * equivalent, such as the internal persistent task actions and {@code AutoCreateAction}, or when it
 * covers several REST APIs that the REST and api-client instrumentations report under different
 * names, such as {@code ResizeAction} covering shrink, split and clone. Reporting the action class
 * name is less specific than the REST API name, but it never attributes an operation to a REST API
 * the caller did not use.
 */
final class ElasticsearchTransportOperationNames {

  private static final Map<String, String> OPERATION_NAMES = buildOperationNames();

  /**
   * Returns the Elasticsearch REST API name for the given action class simple name, falling back to
   * the action class simple name when the action is not mapped.
   */
  static String operationName(String actionClassName) {
    String operationName = OPERATION_NAMES.get(actionClassName);
    return operationName != null ? operationName : actionClassName;
  }

  private static Map<String, String> buildOperationNames() {
    Map<String, String> operationNames = new HashMap<>();
    operationNames.put("AddIndexBlockAction", "indices.add_block");
    operationNames.put("AddVotingConfigExclusionsAction", "cluster.post_voting_config_exclusions");
    operationNames.put("AliasesExistAction", "indices.exists_alias");
    operationNames.put("AnalyzeAction", "indices.analyze");
    operationNames.put("AnalyzeIndexDiskUsageAction", "indices.disk_usage");
    operationNames.put("BulkAction", "bulk");
    operationNames.put("CancelTasksAction", "tasks.cancel");
    operationNames.put("CleanupRepositoryAction", "snapshot.cleanup_repository");
    operationNames.put("ClearIndicesCacheAction", "indices.clear_cache");
    operationNames.put("ClearScrollAction", "clear_scroll");
    operationNames.put(
        "ClearVotingConfigExclusionsAction", "cluster.delete_voting_config_exclusions");
    operationNames.put("CloneSnapshotAction", "snapshot.clone");
    operationNames.put("CloseIndexAction", "indices.close");
    operationNames.put("ClosePointInTimeAction", "close_point_in_time");
    operationNames.put("ClusterAllocationExplainAction", "cluster.allocation_explain");
    operationNames.put("ClusterHealthAction", "cluster.health");
    operationNames.put("ClusterRerouteAction", "cluster.reroute");
    operationNames.put("ClusterSearchShardsAction", "search_shards");
    operationNames.put("ClusterStateAction", "cluster.state");
    operationNames.put("ClusterStatsAction", "cluster.stats");
    operationNames.put("ClusterUpdateSettingsAction", "cluster.put_settings");
    operationNames.put("CreateIndexAction", "indices.create");
    operationNames.put("CreateSnapshotAction", "snapshot.create");
    operationNames.put("DeleteAction", "delete");
    operationNames.put("DeleteByQueryAction", "delete_by_query");
    operationNames.put("DeleteComponentTemplateAction", "cluster.delete_component_template");
    operationNames.put("DeleteComposableIndexTemplateAction", "indices.delete_index_template");
    operationNames.put("DeleteDanglingIndexAction", "dangling_indices.delete_dangling_index");
    operationNames.put("DeleteIndexAction", "indices.delete");
    operationNames.put("DeleteIndexTemplateAction", "indices.delete_template");
    operationNames.put("DeletePipelineAction", "ingest.delete_pipeline");
    operationNames.put("DeleteRepositoryAction", "snapshot.delete_repository");
    operationNames.put("DeleteSnapshotAction", "snapshot.delete");
    operationNames.put("DeleteStoredScriptAction", "delete_script");
    operationNames.put("ExplainAction", "explain");
    operationNames.put("FieldCapabilitiesAction", "field_caps");
    operationNames.put("FieldStatsAction", "field_stats");
    operationNames.put("FieldUsageStatsAction", "indices.field_usage_stats");
    operationNames.put("FlushAction", "indices.flush");
    operationNames.put("ForceMergeAction", "indices.forcemerge");
    operationNames.put("GetAction", "get");
    operationNames.put("GetAliasesAction", "indices.get_alias");
    operationNames.put("GetComponentTemplateAction", "cluster.get_component_template");
    operationNames.put("GetComposableIndexTemplateAction", "indices.get_index_template");
    operationNames.put("GetFeatureUpgradeStatusAction", "migration.get_feature_upgrade_status");
    operationNames.put("GetFieldMappingsAction", "indices.get_field_mapping");
    operationNames.put("GetIndexAction", "indices.get");
    operationNames.put("GetIndexTemplatesAction", "indices.get_template");
    operationNames.put("GetMappingsAction", "indices.get_mapping");
    operationNames.put("GetPipelineAction", "ingest.get_pipeline");
    operationNames.put("GetRepositoriesAction", "snapshot.get_repository");
    operationNames.put("GetScriptContextAction", "get_script_context");
    operationNames.put("GetScriptLanguageAction", "get_script_languages");
    operationNames.put("GetSettingsAction", "indices.get_settings");
    operationNames.put("GetSnapshotsAction", "snapshot.get");
    operationNames.put("GetStoredScriptAction", "get_script");
    operationNames.put("GetTaskAction", "tasks.get");
    operationNames.put("ImportDanglingIndexAction", "dangling_indices.import_dangling_index");
    operationNames.put("IndexAction", "index");
    operationNames.put("IndicesAliasesAction", "indices.update_aliases");
    operationNames.put("IndicesExistsAction", "indices.exists");
    operationNames.put("IndicesSegmentsAction", "indices.segments");
    operationNames.put("IndicesShardStoresAction", "indices.shard_stores");
    operationNames.put("IndicesStatsAction", "indices.stats");
    operationNames.put("ListDanglingIndicesAction", "dangling_indices.list_dangling_indices");
    operationNames.put("ListTasksAction", "tasks.list");
    operationNames.put("MainAction", "info");
    operationNames.put("ModifyDataStreamsAction", "indices.modify_data_stream");
    operationNames.put("MultiGetAction", "mget");
    operationNames.put("MultiSearchAction", "msearch");
    operationNames.put("MultiTermVectorsAction", "mtermvectors");
    operationNames.put("NodesHotThreadsAction", "nodes.hot_threads");
    operationNames.put("NodesInfoAction", "nodes.info");
    operationNames.put("NodesReloadSecureSettingsAction", "nodes.reload_secure_settings");
    operationNames.put("NodesStatsAction", "nodes.stats");
    operationNames.put("NodesUsageAction", "nodes.usage");
    operationNames.put("OpenIndexAction", "indices.open");
    operationNames.put("OpenPointInTimeAction", "open_point_in_time");
    operationNames.put("PendingClusterTasksAction", "cluster.pending_tasks");
    operationNames.put("PostFeatureUpgradeAction", "migration.post_feature_upgrade");
    operationNames.put("PutComponentTemplateAction", "cluster.put_component_template");
    operationNames.put("PutComposableIndexTemplateAction", "indices.put_index_template");
    operationNames.put("PutIndexTemplateAction", "indices.put_template");
    operationNames.put("PutMappingAction", "indices.put_mapping");
    operationNames.put("PutPipelineAction", "ingest.put_pipeline");
    operationNames.put("PutRepositoryAction", "snapshot.create_repository");
    operationNames.put("PutStoredScriptAction", "put_script");
    operationNames.put("RecoveryAction", "indices.recovery");
    operationNames.put("RefreshAction", "indices.refresh");
    operationNames.put("ReindexAction", "reindex");
    operationNames.put("RemoteInfoAction", "cluster.remote_info");
    operationNames.put("ResetFeatureStateAction", "features.reset_features");
    operationNames.put("ResolveIndexAction", "indices.resolve_index");
    operationNames.put("RestoreSnapshotAction", "snapshot.restore");
    operationNames.put("RolloverAction", "indices.rollover");
    operationNames.put("SearchAction", "search");
    operationNames.put("SearchScrollAction", "scroll");
    operationNames.put("ShrinkAction", "indices.shrink");
    operationNames.put("SimulateIndexTemplateAction", "indices.simulate_index_template");
    operationNames.put("SimulatePipelineAction", "ingest.simulate");
    operationNames.put("SimulateTemplateAction", "indices.simulate_template");
    operationNames.put("SnapshotsStatusAction", "snapshot.status");
    operationNames.put("SnapshottableFeaturesAction", "features.get_features");
    operationNames.put("SyncedFlushAction", "indices.flush_synced");
    operationNames.put("TermVectorsAction", "termvectors");
    operationNames.put("TypesExistsAction", "indices.exists_type");
    operationNames.put("UpdateAction", "update");
    operationNames.put("UpdateByQueryAction", "update_by_query");
    operationNames.put("UpdateSettingsAction", "indices.put_settings");
    operationNames.put("UpgradeAction", "indices.upgrade");
    operationNames.put("UpgradeStatusAction", "indices.get_upgrade");
    operationNames.put("ValidateQueryAction", "indices.validate_query");
    operationNames.put("VerifyRepositoryAction", "snapshot.verify_repository");
    return Collections.unmodifiableMap(operationNames);
  }

  private ElasticsearchTransportOperationNames() {}
}
