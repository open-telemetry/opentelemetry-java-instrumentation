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
 * <p>The table is keyed on the fully qualified action class name so that plugin actions cannot
 * collide with built-in actions that have the same simple name. The names are stored as strings
 * because this module is shared across Elasticsearch 5.0 to 7.17 and many of these classes do not
 * exist in every version in that range.
 *
 * <p>An action is deliberately absent, and so keeps its action class name, when it has no REST API
 * equivalent, such as the internal persistent task actions and {@code AutoCreateAction}, or when it
 * covers several REST APIs that the REST and api-client instrumentations report under different
 * names, such as {@code IndexAction} covering index and create or {@code ResizeAction} covering
 * shrink, split and clone. Reporting the action class name is less specific than the REST API name,
 * but it never attributes an operation to a REST API the caller did not use.
 */
final class ElasticsearchTransportOperationNames {

  private static final Map<String, String> OPERATION_NAMES = buildOperationNames();

  /**
   * Returns the Elasticsearch REST API name for the given action class, falling back to its simple
   * name when the action is not mapped.
   */
  static String operationName(String actionClassName, String actionClassSimpleName) {
    String operationName = OPERATION_NAMES.get(actionClassName);
    return operationName != null ? operationName : actionClassSimpleName;
  }

  private static Map<String, String> buildOperationNames() {
    Map<String, String> operationNames = new HashMap<>();
    operationNames.put(
        "org.elasticsearch.action.admin.indices.readonly.AddIndexBlockAction", "indices.add_block");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.configuration.AddVotingConfigExclusionsAction",
        "cluster.post_voting_config_exclusions");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.alias.exists.AliasesExistAction",
        "indices.exists_alias");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.analyze.AnalyzeAction", "indices.analyze");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.diskusage.AnalyzeIndexDiskUsageAction",
        "indices.disk_usage");
    operationNames.put("org.elasticsearch.action.bulk.BulkAction", "bulk");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.node.tasks.cancel.CancelTasksAction",
        "tasks.cancel");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.repositories.cleanup.CleanupRepositoryAction",
        "snapshot.cleanup_repository");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.cache.clear.ClearIndicesCacheAction",
        "indices.clear_cache");
    operationNames.put("org.elasticsearch.action.search.ClearScrollAction", "clear_scroll");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.configuration.ClearVotingConfigExclusionsAction",
        "cluster.delete_voting_config_exclusions");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.snapshots.clone.CloneSnapshotAction",
        "snapshot.clone");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.close.CloseIndexAction", "indices.close");
    operationNames.put(
        "org.elasticsearch.action.search.ClosePointInTimeAction", "close_point_in_time");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.allocation.ClusterAllocationExplainAction",
        "cluster.allocation_explain");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.health.ClusterHealthAction", "cluster.health");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.reroute.ClusterRerouteAction", "cluster.reroute");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.shards.ClusterSearchShardsAction", "search_shards");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.state.ClusterStateAction", "cluster.state");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.stats.ClusterStatsAction", "cluster.stats");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.settings.ClusterUpdateSettingsAction",
        "cluster.put_settings");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.create.CreateIndexAction", "indices.create");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.snapshots.create.CreateSnapshotAction",
        "snapshot.create");
    operationNames.put("org.elasticsearch.action.delete.DeleteAction", "delete");
    operationNames.put("org.elasticsearch.index.reindex.DeleteByQueryAction", "delete_by_query");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.template.delete.DeleteComponentTemplateAction",
        "cluster.delete_component_template");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.template.delete.DeleteComposableIndexTemplateAction",
        "indices.delete_index_template");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.dangling.delete.DeleteDanglingIndexAction",
        "dangling_indices.delete_dangling_index");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.delete.DeleteIndexAction", "indices.delete");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.template.delete.DeleteIndexTemplateAction",
        "indices.delete_template");
    operationNames.put(
        "org.elasticsearch.action.ingest.DeletePipelineAction", "ingest.delete_pipeline");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.repositories.delete.DeleteRepositoryAction",
        "snapshot.delete_repository");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.snapshots.delete.DeleteSnapshotAction",
        "snapshot.delete");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.storedscripts.DeleteStoredScriptAction",
        "delete_script");
    operationNames.put("org.elasticsearch.action.explain.ExplainAction", "explain");
    operationNames.put("org.elasticsearch.action.fieldcaps.FieldCapabilitiesAction", "field_caps");
    operationNames.put("org.elasticsearch.action.fieldstats.FieldStatsAction", "field_stats");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.stats.FieldUsageStatsAction",
        "indices.field_usage_stats");
    operationNames.put("org.elasticsearch.action.admin.indices.flush.FlushAction", "indices.flush");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.forcemerge.ForceMergeAction", "indices.forcemerge");
    operationNames.put("org.elasticsearch.action.get.GetAction", "get");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.alias.get.GetAliasesAction", "indices.get_alias");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.template.get.GetComponentTemplateAction",
        "cluster.get_component_template");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.template.get.GetComposableIndexTemplateAction",
        "indices.get_index_template");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.migration.GetFeatureUpgradeStatusAction",
        "migration.get_feature_upgrade_status");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.mapping.get.GetFieldMappingsAction",
        "indices.get_field_mapping");
    operationNames.put("org.elasticsearch.action.admin.indices.get.GetIndexAction", "indices.get");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.template.get.GetIndexTemplatesAction",
        "indices.get_template");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.mapping.get.GetMappingsAction",
        "indices.get_mapping");
    operationNames.put("org.elasticsearch.action.ingest.GetPipelineAction", "ingest.get_pipeline");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.repositories.get.GetRepositoriesAction",
        "snapshot.get_repository");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.storedscripts.GetScriptContextAction",
        "get_script_context");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.storedscripts.GetScriptLanguageAction",
        "get_script_languages");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.settings.get.GetSettingsAction",
        "indices.get_settings");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsAction", "snapshot.get");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.storedscripts.GetStoredScriptAction", "get_script");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.node.tasks.get.GetTaskAction", "tasks.get");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.dangling.import_index.ImportDanglingIndexAction",
        "dangling_indices.import_dangling_index");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.alias.IndicesAliasesAction",
        "indices.update_aliases");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsAction",
        "indices.exists");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.segments.IndicesSegmentsAction",
        "indices.segments");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.shards.IndicesShardStoresAction",
        "indices.shard_stores");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.stats.IndicesStatsAction", "indices.stats");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.dangling.list.ListDanglingIndicesAction",
        "dangling_indices.list_dangling_indices");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.node.tasks.list.ListTasksAction", "tasks.list");
    operationNames.put("org.elasticsearch.action.main.MainAction", "info");
    operationNames.put(
        "org.elasticsearch.action.datastreams.ModifyDataStreamsAction",
        "indices.modify_data_stream");
    operationNames.put("org.elasticsearch.action.get.MultiGetAction", "mget");
    operationNames.put("org.elasticsearch.action.search.MultiSearchAction", "msearch");
    operationNames.put(
        "org.elasticsearch.action.termvectors.MultiTermVectorsAction", "mtermvectors");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.node.hotthreads.NodesHotThreadsAction",
        "nodes.hot_threads");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.node.info.NodesInfoAction", "nodes.info");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.node.reload.NodesReloadSecureSettingsAction",
        "nodes.reload_secure_settings");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.node.stats.NodesStatsAction", "nodes.stats");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.node.usage.NodesUsageAction", "nodes.usage");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.open.OpenIndexAction", "indices.open");
    operationNames.put(
        "org.elasticsearch.action.search.OpenPointInTimeAction", "open_point_in_time");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.tasks.PendingClusterTasksAction",
        "cluster.pending_tasks");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.migration.PostFeatureUpgradeAction",
        "migration.post_feature_upgrade");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.template.put.PutComponentTemplateAction",
        "cluster.put_component_template");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.template.put.PutComposableIndexTemplateAction",
        "indices.put_index_template");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateAction",
        "indices.put_template");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.mapping.put.PutMappingAction",
        "indices.put_mapping");
    operationNames.put("org.elasticsearch.action.ingest.PutPipelineAction", "ingest.put_pipeline");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.repositories.put.PutRepositoryAction",
        "snapshot.create_repository");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.storedscripts.PutStoredScriptAction", "put_script");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.recovery.RecoveryAction", "indices.recovery");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.refresh.RefreshAction", "indices.refresh");
    operationNames.put("org.elasticsearch.index.reindex.ReindexAction", "reindex");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.remote.RemoteInfoAction", "cluster.remote_info");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.snapshots.features.ResetFeatureStateAction",
        "features.reset_features");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.resolve.ResolveIndexAction",
        "indices.resolve_index");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.snapshots.restore.RestoreSnapshotAction",
        "snapshot.restore");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.rollover.RolloverAction", "indices.rollover");
    operationNames.put("org.elasticsearch.action.search.SearchAction", "search");
    operationNames.put("org.elasticsearch.action.search.SearchScrollAction", "scroll");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.shrink.ShrinkAction", "indices.shrink");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.template.post.SimulateIndexTemplateAction",
        "indices.simulate_index_template");
    operationNames.put("org.elasticsearch.action.ingest.SimulatePipelineAction", "ingest.simulate");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.template.post.SimulateTemplateAction",
        "indices.simulate_template");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.snapshots.status.SnapshotsStatusAction",
        "snapshot.status");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.snapshots.features.SnapshottableFeaturesAction",
        "features.get_features");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.flush.SyncedFlushAction", "indices.flush_synced");
    operationNames.put("org.elasticsearch.action.termvectors.TermVectorsAction", "termvectors");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.exists.types.TypesExistsAction",
        "indices.exists_type");
    operationNames.put("org.elasticsearch.action.update.UpdateAction", "update");
    operationNames.put("org.elasticsearch.index.reindex.UpdateByQueryAction", "update_by_query");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsAction",
        "indices.put_settings");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.upgrade.post.UpgradeAction", "indices.upgrade");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.upgrade.get.UpgradeStatusAction",
        "indices.get_upgrade");
    operationNames.put(
        "org.elasticsearch.action.admin.indices.validate.query.ValidateQueryAction",
        "indices.validate_query");
    operationNames.put(
        "org.elasticsearch.action.admin.cluster.repositories.verify.VerifyRepositoryAction",
        "snapshot.verify_repository");
    return Collections.unmodifiableMap(operationNames);
  }

  private ElasticsearchTransportOperationNames() {}
}
