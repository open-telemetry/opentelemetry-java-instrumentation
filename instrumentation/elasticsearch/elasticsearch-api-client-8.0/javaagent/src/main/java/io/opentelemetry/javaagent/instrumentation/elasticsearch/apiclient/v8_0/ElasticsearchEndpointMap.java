/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.apiclient.v8_0;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ElasticsearchEndpointMap {
  private static final Set<String> searchEndpoints =
      Stream.of(
              "search",
              "async_search.submit",
              "msearch",
              "eql.search",
              "terms_enum",
              "search_template",
              "msearch_template",
              "render_search_template")
          .collect(Collectors.toCollection(HashSet::new));

  private static Map<String, String[]> routesMap;

  private ElasticsearchEndpointMap() {}

  public static boolean isSearchEndpoint(String endpointId) {
    return searchEndpoints.contains(endpointId);
  }

  public static Map<String, String[]> get() {
    if (routesMap == null) {
      routesMap = new HashMap<>(415);
      routesMap.put("async_search.status", new String[] {"/_async_search/status/{id}"});
      routesMap.put("indices.analyze", new String[] {"/_analyze", "/{index}/_analyze"});
      routesMap.put("sql.clear_cursor", new String[] {"/_sql/close"});
      routesMap.put("ml.delete_datafeed", new String[] {"/_ml/datafeeds/{datafeed_id}"});
      routesMap.put("explain", new String[] {"/{index}/_explain/{id}"});
      routesMap.put(
          "cat.thread_pool",
          new String[] {"/_cat/thread_pool", "/_cat/thread_pool/{thread_pool_patterns}"});
      routesMap.put("ml.delete_calendar", new String[] {"/_ml/calendars/{calendar_id}"});
      routesMap.put("indices.create_data_stream", new String[] {"/_data_stream/{name}"});
      routesMap.put("cat.fielddata", new String[] {"/_cat/fielddata", "/_cat/fielddata/{fields}"});
      routesMap.put("security.enroll_node", new String[] {"/_security/enroll/node"});
      routesMap.put("slm.get_status", new String[] {"/_slm/status"});
      routesMap.put("ml.put_calendar", new String[] {"/_ml/calendars/{calendar_id}"});
      routesMap.put("create", new String[] {"/{index}/_create/{id}"});
      routesMap.put(
          "ml.preview_datafeed",
          new String[] {"/_ml/datafeeds/{datafeed_id}/_preview", "/_ml/datafeeds/_preview"});
      routesMap.put("indices.put_template", new String[] {"/_template/{name}"});
      routesMap.put(
          "nodes.reload_secure_settings",
          new String[] {
            "/_nodes/reload_secure_settings", "/_nodes/{node_id}/reload_secure_settings"
          });
      routesMap.put("indices.delete_data_stream", new String[] {"/_data_stream/{name}"});
      routesMap.put(
          "transform.schedule_now_transform",
          new String[] {"/_transform/{transform_id}/_schedule_now"});
      routesMap.put("slm.stop", new String[] {"/_slm/stop"});
      routesMap.put("rollup.delete_job", new String[] {"/_rollup/job/{id}"});
      routesMap.put("cluster.put_component_template", new String[] {"/_component_template/{name}"});
      routesMap.put("delete_script", new String[] {"/_scripts/{id}"});
      routesMap.put("ml.delete_trained_model", new String[] {"/_ml/trained_models/{model_id}"});
      routesMap.put(
          "indices.simulate_template",
          new String[] {"/_index_template/_simulate", "/_index_template/_simulate/{name}"});
      routesMap.put("slm.get_lifecycle", new String[] {"/_slm/policy/{policy_id}", "/_slm/policy"});
      routesMap.put("security.enroll_kibana", new String[] {"/_security/enroll/kibana"});
      routesMap.put("fleet.search", new String[] {"/{index}/_fleet/_fleet_search"});
      routesMap.put("reindex_rethrottle", new String[] {"/_reindex/{task_id}/_rethrottle"});
      routesMap.put("ml.update_filter", new String[] {"/_ml/filters/{filter_id}/_update"});
      routesMap.put("rollup.get_rollup_caps", new String[] {"/_rollup/data/{id}", "/_rollup/data"});
      routesMap.put(
          "ccr.resume_auto_follow_pattern", new String[] {"/_ccr/auto_follow/{name}/resume"});
      routesMap.put("features.get_features", new String[] {"/_features"});
      routesMap.put("slm.get_stats", new String[] {"/_slm/stats"});
      routesMap.put("indices.clear_cache", new String[] {"/_cache/clear", "/{index}/_cache/clear"});
      routesMap.put(
          "cluster.post_voting_config_exclusions",
          new String[] {"/_cluster/voting_config_exclusions"});
      routesMap.put("index", new String[] {"/{index}/_doc/{id}", "/{index}/_doc"});
      routesMap.put("cat.pending_tasks", new String[] {"/_cat/pending_tasks"});
      routesMap.put("indices.promote_data_stream", new String[] {"/_data_stream/_promote/{name}"});
      routesMap.put("ml.delete_filter", new String[] {"/_ml/filters/{filter_id}"});
      routesMap.put("sql.query", new String[] {"/_sql"});
      routesMap.put("ccr.follow_stats", new String[] {"/{index}/_ccr/stats"});
      routesMap.put("transform.stop_transform", new String[] {"/_transform/{transform_id}/_stop"});
      routesMap.put(
          "security.has_privileges_user_profile",
          new String[] {"/_security/profile/_has_privileges"});
      routesMap.put(
          "autoscaling.delete_autoscaling_policy", new String[] {"/_autoscaling/policy/{name}"});
      routesMap.put("scripts_painless_execute", new String[] {"/_scripts/painless/_execute"});
      routesMap.put("indices.delete", new String[] {"/{index}"});
      routesMap.put(
          "security.clear_cached_roles", new String[] {"/_security/role/{name}/_clear_cache"});
      routesMap.put("eql.delete", new String[] {"/_eql/search/{id}"});
      routesMap.put("update", new String[] {"/{index}/_update/{id}"});
      routesMap.put(
          "snapshot.clone",
          new String[] {"/_snapshot/{repository}/{snapshot}/_clone/{target_snapshot}"});
      routesMap.put("license.get_basic_status", new String[] {"/_license/basic_status"});
      routesMap.put("indices.close", new String[] {"/{index}/_close"});
      routesMap.put("security.saml_authenticate", new String[] {"/_security/saml/authenticate"});
      routesMap.put(
          "search_application.put", new String[] {"/_application/search_application/{name}"});
      routesMap.put("count", new String[] {"/_count", "/{index}/_count"});
      routesMap.put(
          "migration.deprecations",
          new String[] {"/_migration/deprecations", "/{index}/_migration/deprecations"});
      routesMap.put("indices.segments", new String[] {"/_segments", "/{index}/_segments"});
      routesMap.put("security.suggest_user_profiles", new String[] {"/_security/profile/_suggest"});
      routesMap.put("security.get_user_privileges", new String[] {"/_security/user/_privileges"});
      routesMap.put(
          "indices.delete_alias",
          new String[] {"/{index}/_alias/{name}", "/{index}/_aliases/{name}"});
      routesMap.put("indices.get_mapping", new String[] {"/_mapping", "/{index}/_mapping"});
      routesMap.put("indices.put_index_template", new String[] {"/_index_template/{name}"});
      routesMap.put(
          "searchable_snapshots.stats",
          new String[] {"/_searchable_snapshots/stats", "/{index}/_searchable_snapshots/stats"});
      routesMap.put("security.disable_user", new String[] {"/_security/user/{username}/_disable"});
      routesMap.put(
          "ml.upgrade_job_snapshot",
          new String[] {"/_ml/anomaly_detectors/{job_id}/model_snapshots/{snapshot_id}/_upgrade"});
      routesMap.put("delete", new String[] {"/{index}/_doc/{id}"});
      routesMap.put("async_search.delete", new String[] {"/_async_search/{id}"});
      routesMap.put(
          "cat.transforms", new String[] {"/_cat/transforms", "/_cat/transforms/{transform_id}"});
      routesMap.put("ping", new String[] {"/"});
      routesMap.put(
          "ccr.pause_auto_follow_pattern", new String[] {"/_ccr/auto_follow/{name}/pause"});
      routesMap.put(
          "indices.shard_stores", new String[] {"/_shard_stores", "/{index}/_shard_stores"});
      routesMap.put(
          "ml.update_data_frame_analytics",
          new String[] {"/_ml/data_frame/analytics/{id}/_update"});
      routesMap.put("logstash.delete_pipeline", new String[] {"/_logstash/pipeline/{id}"});
      routesMap.put("sql.translate", new String[] {"/_sql/translate"});
      routesMap.put("exists", new String[] {"/{index}/_doc/{id}"});
      routesMap.put(
          "snapshot.get_repository", new String[] {"/_snapshot", "/_snapshot/{repository}"});
      routesMap.put("snapshot.verify_repository", new String[] {"/_snapshot/{repository}/_verify"});
      routesMap.put("indices.put_data_lifecycle", new String[] {"/_data_stream/{name}/_lifecycle"});
      routesMap.put("ml.open_job", new String[] {"/_ml/anomaly_detectors/{job_id}/_open"});
      routesMap.put(
          "security.update_user_profile_data", new String[] {"/_security/profile/{uid}/_data"});
      routesMap.put("enrich.put_policy", new String[] {"/_enrich/policy/{name}"});
      routesMap.put(
          "ml.get_datafeed_stats",
          new String[] {"/_ml/datafeeds/{datafeed_id}/_stats", "/_ml/datafeeds/_stats"});
      routesMap.put("open_point_in_time", new String[] {"/{index}/_pit"});
      routesMap.put("get_source", new String[] {"/{index}/_source/{id}"});
      routesMap.put("delete_by_query", new String[] {"/{index}/_delete_by_query"});
      routesMap.put("security.create_api_key", new String[] {"/_security/api_key"});
      routesMap.put("cat.tasks", new String[] {"/_cat/tasks"});
      routesMap.put("watcher.delete_watch", new String[] {"/_watcher/watch/{id}"});
      routesMap.put("ingest.processor_grok", new String[] {"/_ingest/processor/grok"});
      routesMap.put("ingest.put_pipeline", new String[] {"/_ingest/pipeline/{id}"});
      routesMap.put(
          "ml.get_data_frame_analytics_stats",
          new String[] {
            "/_ml/data_frame/analytics/_stats", "/_ml/data_frame/analytics/{id}/_stats"
          });
      routesMap.put(
          "indices.data_streams_stats",
          new String[] {"/_data_stream/_stats", "/_data_stream/{name}/_stats"});
      routesMap.put(
          "security.clear_cached_realms", new String[] {"/_security/realm/{realms}/_clear_cache"});
      routesMap.put("field_caps", new String[] {"/_field_caps", "/{index}/_field_caps"});
      routesMap.put("ml.evaluate_data_frame", new String[] {"/_ml/data_frame/_evaluate"});
      routesMap.put(
          "ml.delete_forecast",
          new String[] {
            "/_ml/anomaly_detectors/{job_id}/_forecast",
            "/_ml/anomaly_detectors/{job_id}/_forecast/{forecast_id}"
          });
      routesMap.put(
          "enrich.get_policy", new String[] {"/_enrich/policy/{name}", "/_enrich/policy"});
      routesMap.put("rollup.start_job", new String[] {"/_rollup/job/{id}/_start"});
      routesMap.put("tasks.cancel", new String[] {"/_tasks/_cancel", "/_tasks/{task_id}/_cancel"});
      routesMap.put("security.saml_logout", new String[] {"/_security/saml/logout"});
      routesMap.put(
          "render_search_template", new String[] {"/_render/template", "/_render/template/{id}"});
      routesMap.put("ml.get_calendar_events", new String[] {"/_ml/calendars/{calendar_id}/events"});
      routesMap.put(
          "security.enable_user_profile", new String[] {"/_security/profile/{uid}/_enable"});
      routesMap.put(
          "logstash.get_pipeline",
          new String[] {"/_logstash/pipeline", "/_logstash/pipeline/{id}"});
      routesMap.put(
          "cat.snapshots", new String[] {"/_cat/snapshots", "/_cat/snapshots/{repository}"});
      routesMap.put("indices.add_block", new String[] {"/{index}/_block/{block}"});
      routesMap.put("terms_enum", new String[] {"/{index}/_terms_enum"});
      routesMap.put("ml.forecast", new String[] {"/_ml/anomaly_detectors/{job_id}/_forecast"});
      routesMap.put(
          "cluster.stats", new String[] {"/_cluster/stats", "/_cluster/stats/nodes/{node_id}"});
      routesMap.put("search_application.list", new String[] {"/_application/search_application"});
      routesMap.put("cat.count", new String[] {"/_cat/count", "/_cat/count/{index}"});
      routesMap.put("cat.segments", new String[] {"/_cat/segments", "/_cat/segments/{index}"});
      routesMap.put("ccr.resume_follow", new String[] {"/{index}/_ccr/resume_follow"});
      routesMap.put(
          "search_application.get", new String[] {"/_application/search_application/{name}"});
      routesMap.put(
          "security.saml_service_provider_metadata",
          new String[] {"/_security/saml/metadata/{realm_name}"});
      routesMap.put("update_by_query", new String[] {"/{index}/_update_by_query"});
      routesMap.put("ml.stop_datafeed", new String[] {"/_ml/datafeeds/{datafeed_id}/_stop"});
      routesMap.put("ilm.explain_lifecycle", new String[] {"/{index}/_ilm/explain"});
      routesMap.put(
          "ml.put_trained_model_vocabulary",
          new String[] {"/_ml/trained_models/{model_id}/vocabulary"});
      routesMap.put("indices.exists", new String[] {"/{index}"});
      routesMap.put("ml.set_upgrade_mode", new String[] {"/_ml/set_upgrade_mode"});
      routesMap.put("security.saml_invalidate", new String[] {"/_security/saml/invalidate"});
      routesMap.put(
          "ml.get_job_stats",
          new String[] {"/_ml/anomaly_detectors/_stats", "/_ml/anomaly_detectors/{job_id}/_stats"});
      routesMap.put("cluster.allocation_explain", new String[] {"/_cluster/allocation/explain"});
      routesMap.put(
          "watcher.activate_watch", new String[] {"/_watcher/watch/{watch_id}/_activate"});
      routesMap.put(
          "searchable_snapshots.clear_cache",
          new String[] {
            "/_searchable_snapshots/cache/clear", "/{index}/_searchable_snapshots/cache/clear"
          });
      routesMap.put(
          "msearch_template", new String[] {"/_msearch/template", "/{index}/_msearch/template"});
      routesMap.put("bulk", new String[] {"/_bulk", "/{index}/_bulk"});
      routesMap.put("cat.nodeattrs", new String[] {"/_cat/nodeattrs"});
      routesMap.put(
          "indices.get_index_template",
          new String[] {"/_index_template", "/_index_template/{name}"});
      routesMap.put("license.get", new String[] {"/_license"});
      routesMap.put("ccr.forget_follower", new String[] {"/{index}/_ccr/forget_follower"});
      routesMap.put("security.delete_role", new String[] {"/_security/role/{name}"});
      routesMap.put(
          "indices.validate_query", new String[] {"/_validate/query", "/{index}/_validate/query"});
      routesMap.put("tasks.get", new String[] {"/_tasks/{task_id}"});
      routesMap.put(
          "ml.start_data_frame_analytics", new String[] {"/_ml/data_frame/analytics/{id}/_start"});
      routesMap.put("indices.create", new String[] {"/{index}"});
      routesMap.put(
          "cluster.delete_voting_config_exclusions",
          new String[] {"/_cluster/voting_config_exclusions"});
      routesMap.put("info", new String[] {"/"});
      routesMap.put("watcher.stop", new String[] {"/_watcher/_stop"});
      routesMap.put("enrich.delete_policy", new String[] {"/_enrich/policy/{name}"});
      routesMap.put(
          "cat.ml_data_frame_analytics",
          new String[] {"/_cat/ml/data_frame/analytics", "/_cat/ml/data_frame/analytics/{id}"});
      routesMap.put(
          "security.change_password",
          new String[] {"/_security/user/{username}/_password", "/_security/user/_password"});
      routesMap.put("put_script", new String[] {"/_scripts/{id}", "/_scripts/{id}/{context}"});
      routesMap.put("ml.put_datafeed", new String[] {"/_ml/datafeeds/{datafeed_id}"});
      routesMap.put("cat.master", new String[] {"/_cat/master"});
      routesMap.put("features.reset_features", new String[] {"/_features/_reset"});
      routesMap.put("indices.get_data_lifecycle", new String[] {"/_data_stream/{name}/_lifecycle"});
      routesMap.put(
          "ml.get_data_frame_analytics",
          new String[] {"/_ml/data_frame/analytics/{id}", "/_ml/data_frame/analytics"});
      routesMap.put(
          "security.delete_service_token",
          new String[] {"/_security/service/{namespace}/{service}/credential/token/{name}"});
      routesMap.put("indices.recovery", new String[] {"/_recovery", "/{index}/_recovery"});
      routesMap.put("cat.recovery", new String[] {"/_cat/recovery", "/_cat/recovery/{index}"});
      routesMap.put("indices.downsample", new String[] {"/{index}/_downsample/{target_index}"});
      routesMap.put("ingest.delete_pipeline", new String[] {"/_ingest/pipeline/{id}"});
      routesMap.put("async_search.get", new String[] {"/_async_search/{id}"});
      routesMap.put("eql.get", new String[] {"/_eql/search/{id}"});
      routesMap.put("cat.aliases", new String[] {"/_cat/aliases", "/_cat/aliases/{name}"});
      routesMap.put(
          "security.get_service_credentials",
          new String[] {"/_security/service/{namespace}/{service}/credential"});
      routesMap.put(
          "cat.allocation", new String[] {"/_cat/allocation", "/_cat/allocation/{node_id}"});
      routesMap.put(
          "ml.stop_data_frame_analytics", new String[] {"/_ml/data_frame/analytics/{id}/_stop"});
      routesMap.put("indices.open", new String[] {"/{index}/_open"});
      routesMap.put("ilm.get_lifecycle", new String[] {"/_ilm/policy/{policy}", "/_ilm/policy"});
      routesMap.put("ilm.remove_policy", new String[] {"/{index}/_ilm/remove"});
      routesMap.put(
          "security.get_role_mapping",
          new String[] {"/_security/role_mapping/{name}", "/_security/role_mapping"});
      routesMap.put("snapshot.create", new String[] {"/_snapshot/{repository}/{snapshot}"});
      routesMap.put("watcher.get_watch", new String[] {"/_watcher/watch/{id}"});
      routesMap.put("license.post_start_trial", new String[] {"/_license/start_trial"});
      routesMap.put(
          "snapshot.restore", new String[] {"/_snapshot/{repository}/{snapshot}/_restore"});
      routesMap.put("indices.put_mapping", new String[] {"/{index}/_mapping"});
      routesMap.put(
          "ml.delete_calendar_job", new String[] {"/_ml/calendars/{calendar_id}/jobs/{job_id}"});
      routesMap.put(
          "security.clear_api_key_cache", new String[] {"/_security/api_key/{ids}/_clear_cache"});
      routesMap.put("slm.start", new String[] {"/_slm/start"});
      routesMap.put(
          "cat.component_templates",
          new String[] {"/_cat/component_templates", "/_cat/component_templates/{name}"});
      routesMap.put("security.enable_user", new String[] {"/_security/user/{username}/_enable"});
      routesMap.put(
          "cluster.delete_component_template", new String[] {"/_component_template/{name}"});
      routesMap.put(
          "security.get_role", new String[] {"/_security/role/{name}", "/_security/role"});
      routesMap.put(
          "ingest.get_pipeline", new String[] {"/_ingest/pipeline", "/_ingest/pipeline/{id}"});
      routesMap.put(
          "ml.delete_expired_data",
          new String[] {"/_ml/_delete_expired_data/{job_id}", "/_ml/_delete_expired_data"});
      routesMap.put(
          "indices.get_settings",
          new String[] {
            "/_settings", "/{index}/_settings", "/{index}/_settings/{name}", "/_settings/{name}"
          });
      routesMap.put("ccr.follow", new String[] {"/{index}/_ccr/follow"});
      routesMap.put(
          "termvectors", new String[] {"/{index}/_termvectors/{id}", "/{index}/_termvectors"});
      routesMap.put("ml.post_data", new String[] {"/_ml/anomaly_detectors/{job_id}/_data"});
      routesMap.put("eql.search", new String[] {"/{index}/_eql/search"});
      routesMap.put(
          "ml.get_trained_models",
          new String[] {"/_ml/trained_models/{model_id}", "/_ml/trained_models"});
      routesMap.put(
          "security.disable_user_profile", new String[] {"/_security/profile/{uid}/_disable"});
      routesMap.put("security.put_privileges", new String[] {"/_security/privilege"});
      routesMap.put("cat.nodes", new String[] {"/_cat/nodes"});
      routesMap.put(
          "nodes.info",
          new String[] {
            "/_nodes", "/_nodes/{node_id}", "/_nodes/{metric}", "/_nodes/{node_id}/{metric}"
          });
      routesMap.put("graph.explore", new String[] {"/{index}/_graph/explore"});
      routesMap.put(
          "autoscaling.put_autoscaling_policy", new String[] {"/_autoscaling/policy/{name}"});
      routesMap.put("cat.templates", new String[] {"/_cat/templates", "/_cat/templates/{name}"});
      routesMap.put("cluster.remote_info", new String[] {"/_remote/info"});
      routesMap.put("rank_eval", new String[] {"/_rank_eval", "/{index}/_rank_eval"});
      routesMap.put(
          "security.delete_privileges", new String[] {"/_security/privilege/{application}/{name}"});
      routesMap.put(
          "security.get_privileges",
          new String[] {
            "/_security/privilege",
            "/_security/privilege/{application}",
            "/_security/privilege/{application}/{name}"
          });
      routesMap.put("scroll", new String[] {"/_search/scroll"});
      routesMap.put("license.delete", new String[] {"/_license"});
      routesMap.put("indices.disk_usage", new String[] {"/{index}/_disk_usage"});
      routesMap.put("msearch", new String[] {"/_msearch", "/{index}/_msearch"});
      routesMap.put("indices.field_usage_stats", new String[] {"/{index}/_field_usage_stats"});
      routesMap.put(
          "indices.rollover",
          new String[] {"/{alias}/_rollover", "/{alias}/_rollover/{new_index}"});
      routesMap.put(
          "cat.ml_trained_models",
          new String[] {"/_cat/ml/trained_models", "/_cat/ml/trained_models/{model_id}"});
      routesMap.put(
          "ml.delete_trained_model_alias",
          new String[] {"/_ml/trained_models/{model_id}/model_aliases/{model_alias}"});
      routesMap.put("indices.get", new String[] {"/{index}"});
      routesMap.put("sql.get_async_status", new String[] {"/_sql/async/status/{id}"});
      routesMap.put("ilm.stop", new String[] {"/_ilm/stop"});
      routesMap.put("security.put_user", new String[] {"/_security/user/{username}"});
      routesMap.put(
          "cluster.state",
          new String[] {
            "/_cluster/state", "/_cluster/state/{metric}", "/_cluster/state/{metric}/{index}"
          });
      routesMap.put("indices.put_settings", new String[] {"/_settings", "/{index}/_settings"});
      routesMap.put("knn_search", new String[] {"/{index}/_knn_search"});
      routesMap.put("get", new String[] {"/{index}/_doc/{id}"});
      routesMap.put("eql.get_status", new String[] {"/_eql/search/status/{id}"});
      routesMap.put("ssl.certificates", new String[] {"/_ssl/certificates"});
      routesMap.put(
          "ml.get_model_snapshots",
          new String[] {
            "/_ml/anomaly_detectors/{job_id}/model_snapshots/{snapshot_id}",
            "/_ml/anomaly_detectors/{job_id}/model_snapshots"
          });
      routesMap.put(
          "nodes.clear_repositories_metering_archive",
          new String[] {"/_nodes/{node_id}/_repositories_metering/{max_archive_version}"});
      routesMap.put("security.put_role", new String[] {"/_security/role/{name}"});
      routesMap.put(
          "ml.get_influencers",
          new String[] {"/_ml/anomaly_detectors/{job_id}/results/influencers"});
      routesMap.put("transform.upgrade_transforms", new String[] {"/_transform/_upgrade"});
      routesMap.put(
          "ml.delete_calendar_event",
          new String[] {"/_ml/calendars/{calendar_id}/events/{event_id}"});
      routesMap.put(
          "indices.get_field_mapping",
          new String[] {"/_mapping/field/{fields}", "/{index}/_mapping/field/{fields}"});
      routesMap.put(
          "transform.preview_transform",
          new String[] {"/_transform/{transform_id}/_preview", "/_transform/_preview"});
      routesMap.put("tasks.list", new String[] {"/_tasks"});
      routesMap.put(
          "ml.clear_trained_model_deployment_cache",
          new String[] {"/_ml/trained_models/{model_id}/deployment/cache/_clear"});
      routesMap.put("cluster.reroute", new String[] {"/_cluster/reroute"});
      routesMap.put(
          "security.saml_complete_logout", new String[] {"/_security/saml/complete_logout"});
      routesMap.put(
          "indices.simulate_index_template",
          new String[] {"/_index_template/_simulate_index/{name}"});
      routesMap.put("snapshot.get", new String[] {"/_snapshot/{repository}/{snapshot}"});
      routesMap.put("ccr.put_auto_follow_pattern", new String[] {"/_ccr/auto_follow/{name}"});
      routesMap.put(
          "nodes.hot_threads",
          new String[] {"/_nodes/hot_threads", "/_nodes/{node_id}/hot_threads"});
      routesMap.put(
          "ml.preview_data_frame_analytics",
          new String[] {
            "/_ml/data_frame/analytics/_preview", "/_ml/data_frame/analytics/{id}/_preview"
          });
      routesMap.put("indices.flush", new String[] {"/_flush", "/{index}/_flush"});
      routesMap.put(
          "cluster.exists_component_template", new String[] {"/_component_template/{name}"});
      routesMap.put(
          "snapshot.status",
          new String[] {
            "/_snapshot/_status",
            "/_snapshot/{repository}/_status",
            "/_snapshot/{repository}/{snapshot}/_status"
          });
      routesMap.put("ml.update_datafeed", new String[] {"/_ml/datafeeds/{datafeed_id}/_update"});
      routesMap.put("indices.update_aliases", new String[] {"/_aliases"});
      routesMap.put(
          "autoscaling.get_autoscaling_capacity", new String[] {"/_autoscaling/capacity"});
      routesMap.put("migration.post_feature_upgrade", new String[] {"/_migration/system_features"});
      routesMap.put(
          "ml.get_records", new String[] {"/_ml/anomaly_detectors/{job_id}/results/records"});
      routesMap.put(
          "indices.get_alias",
          new String[] {"/_alias", "/_alias/{name}", "/{index}/_alias/{name}", "/{index}/_alias"});
      routesMap.put("logstash.put_pipeline", new String[] {"/_logstash/pipeline/{id}"});
      routesMap.put("snapshot.delete_repository", new String[] {"/_snapshot/{repository}"});
      routesMap.put(
          "security.has_privileges",
          new String[] {
            "/_security/user/_has_privileges", "/_security/user/{user}/_has_privileges"
          });
      routesMap.put("cat.indices", new String[] {"/_cat/indices", "/_cat/indices/{index}"});
      routesMap.put(
          "ccr.get_auto_follow_pattern",
          new String[] {"/_ccr/auto_follow", "/_ccr/auto_follow/{name}"});
      routesMap.put("ml.start_datafeed", new String[] {"/_ml/datafeeds/{datafeed_id}/_start"});
      routesMap.put("indices.clone", new String[] {"/{index}/_clone/{target}"});
      routesMap.put(
          "search_application.delete", new String[] {"/_application/search_application/{name}"});
      routesMap.put("security.query_api_keys", new String[] {"/_security/_query/api_key"});
      routesMap.put("ml.flush_job", new String[] {"/_ml/anomaly_detectors/{job_id}/_flush"});
      routesMap.put(
          "security.clear_cached_privileges",
          new String[] {"/_security/privilege/{application}/_clear_cache"});
      routesMap.put("indices.exists_index_template", new String[] {"/_index_template/{name}"});
      routesMap.put("indices.explain_data_lifecycle", new String[] {"/{index}/_lifecycle/explain"});
      routesMap.put(
          "indices.put_alias", new String[] {"/{index}/_alias/{name}", "/{index}/_aliases/{name}"});
      routesMap.put(
          "ml.get_buckets",
          new String[] {
            "/_ml/anomaly_detectors/{job_id}/results/buckets/{timestamp}",
            "/_ml/anomaly_detectors/{job_id}/results/buckets"
          });
      routesMap.put(
          "ml.put_trained_model_definition_part",
          new String[] {"/_ml/trained_models/{model_id}/definition/{part}"});
      routesMap.put("get_script", new String[] {"/_scripts/{id}"});
      routesMap.put(
          "ingest.simulate",
          new String[] {"/_ingest/pipeline/_simulate", "/_ingest/pipeline/{id}/_simulate"});
      routesMap.put(
          "indices.migrate_to_data_stream", new String[] {"/_data_stream/_migrate/{name}"});
      routesMap.put("enrich.execute_policy", new String[] {"/_enrich/policy/{name}/_execute"});
      routesMap.put("indices.split", new String[] {"/{index}/_split/{target}"});
      routesMap.put(
          "ml.delete_model_snapshot",
          new String[] {"/_ml/anomaly_detectors/{job_id}/model_snapshots/{snapshot_id}"});
      routesMap.put(
          "nodes.usage",
          new String[] {
            "/_nodes/usage",
            "/_nodes/{node_id}/usage",
            "/_nodes/usage/{metric}",
            "/_nodes/{node_id}/usage/{metric}"
          });
      routesMap.put("cat.help", new String[] {"/_cat"});
      routesMap.put(
          "ml.estimate_model_memory",
          new String[] {"/_ml/anomaly_detectors/_estimate_model_memory"});
      routesMap.put("exists_source", new String[] {"/{index}/_source/{id}"});
      routesMap.put("ml.put_data_frame_analytics", new String[] {"/_ml/data_frame/analytics/{id}"});
      routesMap.put("security.put_role_mapping", new String[] {"/_security/role_mapping/{name}"});
      routesMap.put("rollup.get_rollup_index_caps", new String[] {"/{index}/_rollup/data"});
      routesMap.put(
          "transform.reset_transform", new String[] {"/_transform/{transform_id}/_reset"});
      routesMap.put(
          "ml.infer_trained_model",
          new String[] {
            "/_ml/trained_models/{model_id}/_infer",
            "/_ml/trained_models/{model_id}/deployment/_infer"
          });
      routesMap.put("reindex", new String[] {"/_reindex"});
      routesMap.put("ml.put_trained_model", new String[] {"/_ml/trained_models/{model_id}"});
      routesMap.put(
          "cat.ml_jobs",
          new String[] {"/_cat/ml/anomaly_detectors", "/_cat/ml/anomaly_detectors/{job_id}"});
      routesMap.put(
          "search_application.search",
          new String[] {"/_application/search_application/{name}/_search"});
      routesMap.put("ilm.put_lifecycle", new String[] {"/_ilm/policy/{policy}"});
      routesMap.put("security.get_token", new String[] {"/_security/oauth2/token"});
      routesMap.put("ilm.move_to_step", new String[] {"/_ilm/move/{index}"});
      routesMap.put(
          "search_template", new String[] {"/_search/template", "/{index}/_search/template"});
      routesMap.put(
          "indices.delete_data_lifecycle", new String[] {"/_data_stream/{name}/_lifecycle"});
      routesMap.put(
          "indices.get_data_stream", new String[] {"/_data_stream", "/_data_stream/{name}"});
      routesMap.put("ml.get_filters", new String[] {"/_ml/filters", "/_ml/filters/{filter_id}"});
      routesMap.put(
          "cat.ml_datafeeds",
          new String[] {"/_cat/ml/datafeeds", "/_cat/ml/datafeeds/{datafeed_id}"});
      routesMap.put("rollup.rollup_search", new String[] {"/{index}/_rollup_search"});
      routesMap.put("ml.put_job", new String[] {"/_ml/anomaly_detectors/{job_id}"});
      routesMap.put(
          "update_by_query_rethrottle", new String[] {"/_update_by_query/{task_id}/_rethrottle"});
      routesMap.put("indices.delete_index_template", new String[] {"/_index_template/{name}"});
      routesMap.put(
          "indices.reload_search_analyzers", new String[] {"/{index}/_reload_search_analyzers"});
      routesMap.put("cluster.get_settings", new String[] {"/_cluster/settings"});
      routesMap.put("cluster.put_settings", new String[] {"/_cluster/settings"});
      routesMap.put("transform.put_transform", new String[] {"/_transform/{transform_id}"});
      routesMap.put("watcher.stats", new String[] {"/_watcher/stats", "/_watcher/stats/{metric}"});
      routesMap.put("ccr.delete_auto_follow_pattern", new String[] {"/_ccr/auto_follow/{name}"});
      routesMap.put("mtermvectors", new String[] {"/_mtermvectors", "/{index}/_mtermvectors"});
      routesMap.put("license.post", new String[] {"/_license"});
      routesMap.put("xpack.info", new String[] {"/_xpack"});
      routesMap.put(
          "dangling_indices.import_dangling_index", new String[] {"/_dangling/{index_uuid}"});
      routesMap.put(
          "nodes.get_repositories_metering_info",
          new String[] {"/_nodes/{node_id}/_repositories_metering"});
      routesMap.put(
          "transform.get_transform_stats", new String[] {"/_transform/{transform_id}/_stats"});
      routesMap.put("mget", new String[] {"/_mget", "/{index}/_mget"});
      routesMap.put(
          "security.get_builtin_privileges", new String[] {"/_security/privilege/_builtin"});
      routesMap.put(
          "ml.update_model_snapshot",
          new String[] {"/_ml/anomaly_detectors/{job_id}/model_snapshots/{snapshot_id}/_update"});
      routesMap.put("ml.info", new String[] {"/_ml/info"});
      routesMap.put("indices.exists_template", new String[] {"/_template/{name}"});
      routesMap.put(
          "watcher.ack_watch",
          new String[] {
            "/_watcher/watch/{watch_id}/_ack", "/_watcher/watch/{watch_id}/_ack/{action_id}"
          });
      routesMap.put(
          "security.get_user", new String[] {"/_security/user/{username}", "/_security/user"});
      routesMap.put(
          "shutdown.get_node", new String[] {"/_nodes/shutdown", "/_nodes/{node_id}/shutdown"});
      routesMap.put("watcher.start", new String[] {"/_watcher/_start"});
      routesMap.put("indices.shrink", new String[] {"/{index}/_shrink/{target}"});
      routesMap.put("license.post_start_basic", new String[] {"/_license/start_basic"});
      routesMap.put("xpack.usage", new String[] {"/_xpack/usage"});
      routesMap.put("ilm.delete_lifecycle", new String[] {"/_ilm/policy/{policy}"});
      routesMap.put("ccr.follow_info", new String[] {"/{index}/_ccr/info"});
      routesMap.put(
          "ml.put_calendar_job", new String[] {"/_ml/calendars/{calendar_id}/jobs/{job_id}"});
      routesMap.put("rollup.put_job", new String[] {"/_rollup/job/{id}"});
      routesMap.put("clear_scroll", new String[] {"/_search/scroll"});
      routesMap.put(
          "ml.delete_data_frame_analytics", new String[] {"/_ml/data_frame/analytics/{id}"});
      routesMap.put("security.get_api_key", new String[] {"/_security/api_key"});
      routesMap.put("cat.health", new String[] {"/_cat/health"});
      routesMap.put("security.invalidate_token", new String[] {"/_security/oauth2/token"});
      routesMap.put("slm.delete_lifecycle", new String[] {"/_slm/policy/{policy_id}"});
      routesMap.put(
          "ml.stop_trained_model_deployment",
          new String[] {"/_ml/trained_models/{model_id}/deployment/_stop"});
      routesMap.put(
          "monitoring.bulk", new String[] {"/_monitoring/bulk", "/_monitoring/{type}/bulk"});
      routesMap.put(
          "indices.stats",
          new String[] {
            "/_stats", "/_stats/{metric}", "/{index}/_stats", "/{index}/_stats/{metric}"
          });
      routesMap.put(
          "searchable_snapshots.cache_stats",
          new String[] {
            "/_searchable_snapshots/cache/stats", "/_searchable_snapshots/{node_id}/cache/stats"
          });
      routesMap.put(
          "async_search.submit", new String[] {"/_async_search", "/{index}/_async_search"});
      routesMap.put("rollup.get_jobs", new String[] {"/_rollup/job/{id}", "/_rollup/job"});
      routesMap.put(
          "ml.revert_model_snapshot",
          new String[] {"/_ml/anomaly_detectors/{job_id}/model_snapshots/{snapshot_id}/_revert"});
      routesMap.put("transform.delete_transform", new String[] {"/_transform/{transform_id}"});
      routesMap.put("cluster.pending_tasks", new String[] {"/_cluster/pending_tasks"});
      routesMap.put(
          "ml.get_model_snapshot_upgrade_stats",
          new String[] {
            "/_ml/anomaly_detectors/{job_id}/model_snapshots/{snapshot_id}/_upgrade/_stats"
          });
      routesMap.put(
          "ml.get_categories",
          new String[] {
            "/_ml/anomaly_detectors/{job_id}/results/categories/{category_id}",
            "/_ml/anomaly_detectors/{job_id}/results/categories"
          });
      routesMap.put("ccr.pause_follow", new String[] {"/{index}/_ccr/pause_follow"});
      routesMap.put("security.authenticate", new String[] {"/_security/_authenticate"});
      routesMap.put("enrich.stats", new String[] {"/_enrich/_stats"});
      routesMap.put(
          "ml.put_trained_model_alias",
          new String[] {"/_ml/trained_models/{model_id}/model_aliases/{model_alias}"});
      routesMap.put(
          "ml.get_overall_buckets",
          new String[] {"/_ml/anomaly_detectors/{job_id}/results/overall_buckets"});
      routesMap.put("indices.get_template", new String[] {"/_template", "/_template/{name}"});
      routesMap.put(
          "security.delete_role_mapping", new String[] {"/_security/role_mapping/{name}"});
      routesMap.put(
          "ml.get_datafeeds", new String[] {"/_ml/datafeeds/{datafeed_id}", "/_ml/datafeeds"});
      routesMap.put("slm.execute_lifecycle", new String[] {"/_slm/policy/{policy_id}/_execute"});
      routesMap.put("close_point_in_time", new String[] {"/_pit"});
      routesMap.put(
          "snapshot.cleanup_repository", new String[] {"/_snapshot/{repository}/_cleanup"});
      routesMap.put(
          "autoscaling.get_autoscaling_policy", new String[] {"/_autoscaling/policy/{name}"});
      routesMap.put("slm.put_lifecycle", new String[] {"/_slm/policy/{policy_id}"});
      routesMap.put(
          "ml.get_jobs",
          new String[] {"/_ml/anomaly_detectors/{job_id}", "/_ml/anomaly_detectors"});
      routesMap.put(
          "ml.get_trained_models_stats",
          new String[] {"/_ml/trained_models/{model_id}/_stats", "/_ml/trained_models/_stats"});
      routesMap.put(
          "ml.validate_detector", new String[] {"/_ml/anomaly_detectors/_validate/detector"});
      routesMap.put("watcher.put_watch", new String[] {"/_watcher/watch/{id}"});
      routesMap.put(
          "transform.update_transform", new String[] {"/_transform/{transform_id}/_update"});
      routesMap.put(
          "ml.post_calendar_events", new String[] {"/_ml/calendars/{calendar_id}/events"});
      routesMap.put(
          "migration.get_feature_upgrade_status", new String[] {"/_migration/system_features"});
      routesMap.put("get_script_context", new String[] {"/_script_context"});
      routesMap.put("ml.put_filter", new String[] {"/_ml/filters/{filter_id}"});
      routesMap.put("ml.update_job", new String[] {"/_ml/anomaly_detectors/{job_id}/_update"});
      routesMap.put("ingest.geo_ip_stats", new String[] {"/_ingest/geoip/stats"});
      routesMap.put("security.delete_user", new String[] {"/_security/user/{username}"});
      routesMap.put("indices.unfreeze", new String[] {"/{index}/_unfreeze"});
      routesMap.put("snapshot.create_repository", new String[] {"/_snapshot/{repository}"});
      routesMap.put(
          "cluster.get_component_template",
          new String[] {"/_component_template", "/_component_template/{name}"});
      routesMap.put("ilm.migrate_to_data_tiers", new String[] {"/_ilm/migrate_to_data_tiers"});
      routesMap.put("indices.refresh", new String[] {"/_refresh", "/{index}/_refresh"});
      routesMap.put(
          "ml.get_calendars", new String[] {"/_ml/calendars", "/_ml/calendars/{calendar_id}"});
      routesMap.put(
          "watcher.deactivate_watch", new String[] {"/_watcher/watch/{watch_id}/_deactivate"});
      routesMap.put(
          "cluster.health", new String[] {"/_cluster/health", "/_cluster/health/{index}"});
      routesMap.put(
          "dangling_indices.delete_dangling_index", new String[] {"/_dangling/{index_uuid}"});
      routesMap.put("health_report", new String[] {"/_health_report", "/_health_report/{feature}"});
      routesMap.put("watcher.query_watches", new String[] {"/_watcher/_query/watches"});
      routesMap.put("ccr.unfollow", new String[] {"/{index}/_ccr/unfollow"});
      routesMap.put("ml.validate", new String[] {"/_ml/anomaly_detectors/_validate"});
      routesMap.put("cat.plugins", new String[] {"/_cat/plugins"});
      routesMap.put(
          "watcher.execute_watch",
          new String[] {"/_watcher/watch/{id}/_execute", "/_watcher/watch/_execute"});
      routesMap.put("search_shards", new String[] {"/_search_shards", "/{index}/_search_shards"});
      routesMap.put("cat.shards", new String[] {"/_cat/shards", "/_cat/shards/{index}"});
      routesMap.put("ml.delete_job", new String[] {"/_ml/anomaly_detectors/{job_id}"});
      routesMap.put("ilm.start", new String[] {"/_ilm/start"});
      routesMap.put("security.get_user_profile", new String[] {"/_security/profile/{uid}"});
      routesMap.put("indices.modify_data_stream", new String[] {"/_data_stream/_modify"});
      routesMap.put(
          "indices.exists_alias", new String[] {"/_alias/{name}", "/{index}/_alias/{name}"});
      routesMap.put("rollup.stop_job", new String[] {"/_rollup/job/{id}/_stop"});
      routesMap.put("dangling_indices.list_dangling_indices", new String[] {"/_dangling"});
      routesMap.put("snapshot.delete", new String[] {"/_snapshot/{repository}/{snapshot}"});
      routesMap.put(
          "security.activate_user_profile", new String[] {"/_security/profile/_activate"});
      routesMap.put(
          "ml.start_trained_model_deployment",
          new String[] {"/_ml/trained_models/{model_id}/deployment/_start"});
      routesMap.put(
          "transform.start_transform", new String[] {"/_transform/{transform_id}/_start"});
      routesMap.put("cat.repositories", new String[] {"/_cat/repositories"});
      routesMap.put("ilm.get_status", new String[] {"/_ilm/status"});
      routesMap.put("shutdown.delete_node", new String[] {"/_nodes/{node_id}/shutdown"});
      routesMap.put(
          "nodes.stats",
          new String[] {
            "/_nodes/stats",
            "/_nodes/{node_id}/stats",
            "/_nodes/stats/{metric}",
            "/_nodes/{node_id}/stats/{metric}",
            "/_nodes/stats/{metric}/{index_metric}",
            "/_nodes/{node_id}/stats/{metric}/{index_metric}"
          });
      routesMap.put("get_script_languages", new String[] {"/_script_language"});
      routesMap.put("slm.execute_retention", new String[] {"/_slm/_execute_retention"});
      routesMap.put(
          "security.get_service_accounts",
          new String[] {
            "/_security/service/{namespace}/{service}",
            "/_security/service/{namespace}",
            "/_security/service"
          });
      routesMap.put("shutdown.put_node", new String[] {"/_nodes/{node_id}/shutdown"});
      routesMap.put("indices.resolve_index", new String[] {"/_resolve/index/{name}"});
      routesMap.put("search", new String[] {"/_search", "/{index}/_search"});
      routesMap.put("sql.get_async", new String[] {"/_sql/async/{id}"});
      routesMap.put(
          "delete_by_query_rethrottle", new String[] {"/_delete_by_query/{task_id}/_rethrottle"});
      routesMap.put(
          "transform.get_transform", new String[] {"/_transform/{transform_id}", "/_transform"});
      routesMap.put("security.invalidate_api_key", new String[] {"/_security/api_key"});
      routesMap.put(
          "security.saml_prepare_authentication", new String[] {"/_security/saml/prepare"});
      routesMap.put(
          "ml.get_memory_stats",
          new String[] {"/_ml/memory/_stats", "/_ml/memory/{node_id}/_stats"});
      routesMap.put("ccr.stats", new String[] {"/_ccr/stats"});
      routesMap.put("indices.forcemerge", new String[] {"/_forcemerge", "/{index}/_forcemerge"});
      routesMap.put("indices.delete_template", new String[] {"/_template/{name}"});
      routesMap.put("sql.delete_async", new String[] {"/_sql/async/delete/{id}"});
      routesMap.put("security.update_api_key", new String[] {"/_security/api_key/{id}"});
      routesMap.put(
          "security.create_service_token",
          new String[] {
            "/_security/service/{namespace}/{service}/credential/token/{name}",
            "/_security/service/{namespace}/{service}/credential/token"
          });
      routesMap.put("license.get_trial_status", new String[] {"/_license/trial_status"});
      routesMap.put(
          "searchable_snapshots.mount", new String[] {"/_snapshot/{repository}/{snapshot}/_mount"});
      routesMap.put("security.grant_api_key", new String[] {"/_security/api_key/grant"});
      routesMap.put("ilm.retry", new String[] {"/{index}/_ilm/retry"});
      routesMap.put("ml.reset_job", new String[] {"/_ml/anomaly_detectors/{job_id}/_reset"});
      routesMap.put("ml.close_job", new String[] {"/_ml/anomaly_detectors/{job_id}/_close"});
      routesMap.put(
          "ml.explain_data_frame_analytics",
          new String[] {
            "/_ml/data_frame/analytics/_explain", "/_ml/data_frame/analytics/{id}/_explain"
          });
      routesMap.put(
          "security.clear_cached_service_tokens",
          new String[] {
            "/_security/service/{namespace}/{service}/credential/token/{name}/_clear_cache"
          });
      routesMap.put("search_mvt", new String[] {"/{index}/_mvt/{field}/{zoom}/{x}/{y}"});
    }
    return routesMap;
  }
}
