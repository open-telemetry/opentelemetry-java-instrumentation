/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.apiclient;

import io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.ElasticsearchEndpointDefinition;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

public class ElasticsearchEndpointMap {

  private static final Map<String, ElasticsearchEndpointDefinition> routesMap = new HashMap<>(415);

  static {
    initEndpoint("async_search.status", false, "/_async_search/status/{id}");
    initEndpoint("indices.analyze", false, "/_analyze", "/{index}/_analyze");
    initEndpoint("sql.clear_cursor", false, "/_sql/close");
    initEndpoint("ml.delete_datafeed", false, "/_ml/datafeeds/{datafeed_id}");
    initEndpoint("explain", false, "/{index}/_explain/{id}");
    initEndpoint(
        "cat.thread_pool", false, "/_cat/thread_pool", "/_cat/thread_pool/{thread_pool_patterns}");
    initEndpoint("ml.delete_calendar", false, "/_ml/calendars/{calendar_id}");
    initEndpoint("indices.create_data_stream", false, "/_data_stream/{name}");
    initEndpoint("cat.fielddata", false, "/_cat/fielddata", "/_cat/fielddata/{fields}");
    initEndpoint("security.enroll_node", false, "/_security/enroll/node");
    initEndpoint("slm.get_status", false, "/_slm/status");
    initEndpoint("ml.put_calendar", false, "/_ml/calendars/{calendar_id}");
    initEndpoint("create", false, "/{index}/_create/{id}");
    initEndpoint(
        "ml.preview_datafeed",
        false,
        "/_ml/datafeeds/{datafeed_id}/_preview",
        "/_ml/datafeeds/_preview");
    initEndpoint("indices.put_template", false, "/_template/{name}");
    initEndpoint(
        "nodes.reload_secure_settings",
        false,
        "/_nodes/reload_secure_settings",
        "/_nodes/{node_id}/reload_secure_settings");
    initEndpoint("indices.delete_data_stream", false, "/_data_stream/{name}");
    initEndpoint(
        "transform.schedule_now_transform", false, "/_transform/{transform_id}/_schedule_now");
    initEndpoint("slm.stop", false, "/_slm/stop");
    initEndpoint("rollup.delete_job", false, "/_rollup/job/{id}");
    initEndpoint("cluster.put_component_template", false, "/_component_template/{name}");
    initEndpoint("delete_script", false, "/_scripts/{id}");
    initEndpoint("ml.delete_trained_model", false, "/_ml/trained_models/{model_id}");
    initEndpoint(
        "indices.simulate_template",
        false,
        "/_index_template/_simulate",
        "/_index_template/_simulate/{name}");
    initEndpoint("slm.get_lifecycle", false, "/_slm/policy/{policy_id}", "/_slm/policy");
    initEndpoint("security.enroll_kibana", false, "/_security/enroll/kibana");
    initEndpoint("fleet.search", false, "/{index}/_fleet/_fleet_search");
    initEndpoint("reindex_rethrottle", false, "/_reindex/{task_id}/_rethrottle");
    initEndpoint("ml.update_filter", false, "/_ml/filters/{filter_id}/_update");
    initEndpoint("rollup.get_rollup_caps", false, "/_rollup/data/{id}", "/_rollup/data");
    initEndpoint("ccr.resume_auto_follow_pattern", false, "/_ccr/auto_follow/{name}/resume");
    initEndpoint("features.get_features", false, "/_features");
    initEndpoint("slm.get_stats", false, "/_slm/stats");
    initEndpoint("indices.clear_cache", false, "/_cache/clear", "/{index}/_cache/clear");
    initEndpoint(
        "cluster.post_voting_config_exclusions", false, "/_cluster/voting_config_exclusions");
    initEndpoint("index", false, "/{index}/_doc/{id}", "/{index}/_doc");
    initEndpoint("cat.pending_tasks", false, "/_cat/pending_tasks");
    initEndpoint("indices.promote_data_stream", false, "/_data_stream/_promote/{name}");
    initEndpoint("ml.delete_filter", false, "/_ml/filters/{filter_id}");
    initEndpoint("sql.query", false, "/_sql");
    initEndpoint("ccr.follow_stats", false, "/{index}/_ccr/stats");
    initEndpoint("transform.stop_transform", false, "/_transform/{transform_id}/_stop");
    initEndpoint(
        "security.has_privileges_user_profile", false, "/_security/profile/_has_privileges");
    initEndpoint("autoscaling.delete_autoscaling_policy", false, "/_autoscaling/policy/{name}");
    initEndpoint("scripts_painless_execute", false, "/_scripts/painless/_execute");
    initEndpoint("indices.delete", false, "/{index}");
    initEndpoint("security.clear_cached_roles", false, "/_security/role/{name}/_clear_cache");
    initEndpoint("eql.delete", false, "/_eql/search/{id}");
    initEndpoint("update", false, "/{index}/_update/{id}");
    initEndpoint(
        "snapshot.clone", false, "/_snapshot/{repository}/{snapshot}/_clone/{target_snapshot}");
    initEndpoint("license.get_basic_status", false, "/_license/basic_status");
    initEndpoint("indices.close", false, "/{index}/_close");
    initEndpoint("security.saml_authenticate", false, "/_security/saml/authenticate");
    initEndpoint("search_application.put", false, "/_application/search_application/{name}");
    initEndpoint("count", false, "/_count", "/{index}/_count");
    initEndpoint(
        "migration.deprecations",
        false,
        "/_migration/deprecations",
        "/{index}/_migration/deprecations");
    initEndpoint("indices.segments", false, "/_segments", "/{index}/_segments");
    initEndpoint("security.suggest_user_profiles", false, "/_security/profile/_suggest");
    initEndpoint("security.get_user_privileges", false, "/_security/user/_privileges");
    initEndpoint(
        "indices.delete_alias", false, "/{index}/_alias/{name}", "/{index}/_aliases/{name}");
    initEndpoint("indices.get_mapping", false, "/_mapping", "/{index}/_mapping");
    initEndpoint("indices.put_index_template", false, "/_index_template/{name}");
    initEndpoint(
        "searchable_snapshots.stats",
        false,
        "/_searchable_snapshots/stats",
        "/{index}/_searchable_snapshots/stats");
    initEndpoint("security.disable_user", false, "/_security/user/{username}/_disable");
    initEndpoint(
        "ml.upgrade_job_snapshot",
        false,
        "/_ml/anomaly_detectors/{job_id}/model_snapshots/{snapshot_id}/_upgrade");
    initEndpoint("delete", false, "/{index}/_doc/{id}");
    initEndpoint("async_search.delete", false, "/_async_search/{id}");
    initEndpoint("cat.transforms", false, "/_cat/transforms", "/_cat/transforms/{transform_id}");
    initEndpoint("ping", false, "/");
    initEndpoint("ccr.pause_auto_follow_pattern", false, "/_ccr/auto_follow/{name}/pause");
    initEndpoint("indices.shard_stores", false, "/_shard_stores", "/{index}/_shard_stores");
    initEndpoint("ml.update_data_frame_analytics", false, "/_ml/data_frame/analytics/{id}/_update");
    initEndpoint("logstash.delete_pipeline", false, "/_logstash/pipeline/{id}");
    initEndpoint("sql.translate", false, "/_sql/translate");
    initEndpoint("exists", false, "/{index}/_doc/{id}");
    initEndpoint("snapshot.get_repository", false, "/_snapshot", "/_snapshot/{repository}");
    initEndpoint("snapshot.verify_repository", false, "/_snapshot/{repository}/_verify");
    initEndpoint("indices.put_data_lifecycle", false, "/_data_stream/{name}/_lifecycle");
    initEndpoint("ml.open_job", false, "/_ml/anomaly_detectors/{job_id}/_open");
    initEndpoint("security.update_user_profile_data", false, "/_security/profile/{uid}/_data");
    initEndpoint("enrich.put_policy", false, "/_enrich/policy/{name}");
    initEndpoint(
        "ml.get_datafeed_stats",
        false,
        "/_ml/datafeeds/{datafeed_id}/_stats",
        "/_ml/datafeeds/_stats");
    initEndpoint("open_point_in_time", false, "/{index}/_pit");
    initEndpoint("get_source", false, "/{index}/_source/{id}");
    initEndpoint("delete_by_query", false, "/{index}/_delete_by_query");
    initEndpoint("security.create_api_key", false, "/_security/api_key");
    initEndpoint("cat.tasks", false, "/_cat/tasks");
    initEndpoint("watcher.delete_watch", false, "/_watcher/watch/{id}");
    initEndpoint("ingest.processor_grok", false, "/_ingest/processor/grok");
    initEndpoint("ingest.put_pipeline", false, "/_ingest/pipeline/{id}");
    initEndpoint(
        "ml.get_data_frame_analytics_stats",
        false,
        "/_ml/data_frame/analytics/_stats",
        "/_ml/data_frame/analytics/{id}/_stats");
    initEndpoint(
        "indices.data_streams_stats", false, "/_data_stream/_stats", "/_data_stream/{name}/_stats");
    initEndpoint("security.clear_cached_realms", false, "/_security/realm/{realms}/_clear_cache");
    initEndpoint("field_caps", false, "/_field_caps", "/{index}/_field_caps");
    initEndpoint("ml.evaluate_data_frame", false, "/_ml/data_frame/_evaluate");
    initEndpoint(
        "ml.delete_forecast",
        false,
        "/_ml/anomaly_detectors/{job_id}/_forecast",
        "/_ml/anomaly_detectors/{job_id}/_forecast/{forecast_id}");
    initEndpoint("enrich.get_policy", false, "/_enrich/policy/{name}", "/_enrich/policy");
    initEndpoint("rollup.start_job", false, "/_rollup/job/{id}/_start");
    initEndpoint("tasks.cancel", false, "/_tasks/_cancel", "/_tasks/{task_id}/_cancel");
    initEndpoint("security.saml_logout", false, "/_security/saml/logout");
    initEndpoint("render_search_template", true, "/_render/template", "/_render/template/{id}");
    initEndpoint("ml.get_calendar_events", false, "/_ml/calendars/{calendar_id}/events");
    initEndpoint("security.enable_user_profile", false, "/_security/profile/{uid}/_enable");
    initEndpoint("logstash.get_pipeline", false, "/_logstash/pipeline", "/_logstash/pipeline/{id}");
    initEndpoint("cat.snapshots", false, "/_cat/snapshots", "/_cat/snapshots/{repository}");
    initEndpoint("indices.add_block", false, "/{index}/_block/{block}");
    initEndpoint("terms_enum", true, "/{index}/_terms_enum");
    initEndpoint("ml.forecast", false, "/_ml/anomaly_detectors/{job_id}/_forecast");
    initEndpoint("cluster.stats", false, "/_cluster/stats", "/_cluster/stats/nodes/{node_id}");
    initEndpoint("search_application.list", false, "/_application/search_application");
    initEndpoint("cat.count", false, "/_cat/count", "/_cat/count/{index}");
    initEndpoint("cat.segments", false, "/_cat/segments", "/_cat/segments/{index}");
    initEndpoint("ccr.resume_follow", false, "/{index}/_ccr/resume_follow");
    initEndpoint("search_application.get", false, "/_application/search_application/{name}");
    initEndpoint(
        "security.saml_service_provider_metadata", false, "/_security/saml/metadata/{realm_name}");
    initEndpoint("update_by_query", false, "/{index}/_update_by_query");
    initEndpoint("ml.stop_datafeed", false, "/_ml/datafeeds/{datafeed_id}/_stop");
    initEndpoint("ilm.explain_lifecycle", false, "/{index}/_ilm/explain");
    initEndpoint(
        "ml.put_trained_model_vocabulary", false, "/_ml/trained_models/{model_id}/vocabulary");
    initEndpoint("indices.exists", false, "/{index}");
    initEndpoint("ml.set_upgrade_mode", false, "/_ml/set_upgrade_mode");
    initEndpoint("security.saml_invalidate", false, "/_security/saml/invalidate");
    initEndpoint(
        "ml.get_job_stats",
        false,
        "/_ml/anomaly_detectors/_stats",
        "/_ml/anomaly_detectors/{job_id}/_stats");
    initEndpoint("cluster.allocation_explain", false, "/_cluster/allocation/explain");
    initEndpoint("watcher.activate_watch", false, "/_watcher/watch/{watch_id}/_activate");
    initEndpoint(
        "searchable_snapshots.clear_cache",
        false,
        "/_searchable_snapshots/cache/clear",
        "/{index}/_searchable_snapshots/cache/clear");
    initEndpoint("msearch_template", true, "/_msearch/template", "/{index}/_msearch/template");
    initEndpoint("bulk", false, "/_bulk", "/{index}/_bulk");
    initEndpoint("cat.nodeattrs", false, "/_cat/nodeattrs");
    initEndpoint(
        "indices.get_index_template", false, "/_index_template", "/_index_template/{name}");
    initEndpoint("license.get", false, "/_license");
    initEndpoint("ccr.forget_follower", false, "/{index}/_ccr/forget_follower");
    initEndpoint("security.delete_role", false, "/_security/role/{name}");
    initEndpoint("indices.validate_query", false, "/_validate/query", "/{index}/_validate/query");
    initEndpoint("tasks.get", false, "/_tasks/{task_id}");
    initEndpoint("ml.start_data_frame_analytics", false, "/_ml/data_frame/analytics/{id}/_start");
    initEndpoint("indices.create", false, "/{index}");
    initEndpoint(
        "cluster.delete_voting_config_exclusions", false, "/_cluster/voting_config_exclusions");
    initEndpoint("info", false, "/");
    initEndpoint("watcher.stop", false, "/_watcher/_stop");
    initEndpoint("enrich.delete_policy", false, "/_enrich/policy/{name}");
    initEndpoint(
        "cat.ml_data_frame_analytics",
        false,
        "/_cat/ml/data_frame/analytics",
        "/_cat/ml/data_frame/analytics/{id}");
    initEndpoint(
        "security.change_password",
        false,
        "/_security/user/{username}/_password",
        "/_security/user/_password");
    initEndpoint("put_script", false, "/_scripts/{id}", "/_scripts/{id}/{context}");
    initEndpoint("ml.put_datafeed", false, "/_ml/datafeeds/{datafeed_id}");
    initEndpoint("cat.master", false, "/_cat/master");
    initEndpoint("features.reset_features", false, "/_features/_reset");
    initEndpoint("indices.get_data_lifecycle", false, "/_data_stream/{name}/_lifecycle");
    initEndpoint(
        "ml.get_data_frame_analytics",
        false,
        "/_ml/data_frame/analytics/{id}",
        "/_ml/data_frame/analytics");
    initEndpoint(
        "security.delete_service_token",
        false,
        "/_security/service/{namespace}/{service}/credential/token/{name}");
    initEndpoint("indices.recovery", false, "/_recovery", "/{index}/_recovery");
    initEndpoint("cat.recovery", false, "/_cat/recovery", "/_cat/recovery/{index}");
    initEndpoint("indices.downsample", false, "/{index}/_downsample/{target_index}");
    initEndpoint("ingest.delete_pipeline", false, "/_ingest/pipeline/{id}");
    initEndpoint("async_search.get", false, "/_async_search/{id}");
    initEndpoint("eql.get", false, "/_eql/search/{id}");
    initEndpoint("cat.aliases", false, "/_cat/aliases", "/_cat/aliases/{name}");
    initEndpoint(
        "security.get_service_credentials",
        false,
        "/_security/service/{namespace}/{service}/credential");
    initEndpoint("cat.allocation", false, "/_cat/allocation", "/_cat/allocation/{node_id}");
    initEndpoint("ml.stop_data_frame_analytics", false, "/_ml/data_frame/analytics/{id}/_stop");
    initEndpoint("indices.open", false, "/{index}/_open");
    initEndpoint("ilm.get_lifecycle", false, "/_ilm/policy/{policy}", "/_ilm/policy");
    initEndpoint("ilm.remove_policy", false, "/{index}/_ilm/remove");
    initEndpoint(
        "security.get_role_mapping",
        false,
        "/_security/role_mapping/{name}",
        "/_security/role_mapping");
    initEndpoint("snapshot.create", false, "/_snapshot/{repository}/{snapshot}");
    initEndpoint("watcher.get_watch", false, "/_watcher/watch/{id}");
    initEndpoint("license.post_start_trial", false, "/_license/start_trial");
    initEndpoint("snapshot.restore", false, "/_snapshot/{repository}/{snapshot}/_restore");
    initEndpoint("indices.put_mapping", false, "/{index}/_mapping");
    initEndpoint("ml.delete_calendar_job", false, "/_ml/calendars/{calendar_id}/jobs/{job_id}");
    initEndpoint("security.clear_api_key_cache", false, "/_security/api_key/{ids}/_clear_cache");
    initEndpoint("slm.start", false, "/_slm/start");
    initEndpoint(
        "cat.component_templates",
        false,
        "/_cat/component_templates",
        "/_cat/component_templates/{name}");
    initEndpoint("security.enable_user", false, "/_security/user/{username}/_enable");
    initEndpoint("cluster.delete_component_template", false, "/_component_template/{name}");
    initEndpoint("security.get_role", false, "/_security/role/{name}", "/_security/role");
    initEndpoint("ingest.get_pipeline", false, "/_ingest/pipeline", "/_ingest/pipeline/{id}");
    initEndpoint(
        "ml.delete_expired_data",
        false,
        "/_ml/_delete_expired_data/{job_id}",
        "/_ml/_delete_expired_data");
    initEndpoint(
        "indices.get_settings",
        false,
        "/_settings",
        "/{index}/_settings",
        "/{index}/_settings/{name}",
        "/_settings/{name}");
    initEndpoint("ccr.follow", false, "/{index}/_ccr/follow");
    initEndpoint("termvectors", false, "/{index}/_termvectors/{id}", "/{index}/_termvectors");
    initEndpoint("ml.post_data", false, "/_ml/anomaly_detectors/{job_id}/_data");
    initEndpoint("eql.search", true, "/{index}/_eql/search");
    initEndpoint(
        "ml.get_trained_models", false, "/_ml/trained_models/{model_id}", "/_ml/trained_models");
    initEndpoint("security.disable_user_profile", false, "/_security/profile/{uid}/_disable");
    initEndpoint("security.put_privileges", false, "/_security/privilege");
    initEndpoint("cat.nodes", false, "/_cat/nodes");
    initEndpoint("nodes.info", false, "/_nodes", "/_nodes/{node_id}", "/_nodes/{node_id}/{metric}");
    initEndpoint("graph.explore", false, "/{index}/_graph/explore");
    initEndpoint("autoscaling.put_autoscaling_policy", false, "/_autoscaling/policy/{name}");
    initEndpoint("cat.templates", false, "/_cat/templates", "/_cat/templates/{name}");
    initEndpoint("cluster.remote_info", false, "/_remote/info");
    initEndpoint("rank_eval", false, "/_rank_eval", "/{index}/_rank_eval");
    initEndpoint("security.delete_privileges", false, "/_security/privilege/{application}/{name}");
    initEndpoint(
        "security.get_privileges",
        false,
        "/_security/privilege",
        "/_security/privilege/{application}",
        "/_security/privilege/{application}/{name}");
    initEndpoint("scroll", false, "/_search/scroll");
    initEndpoint("license.delete", false, "/_license");
    initEndpoint("indices.disk_usage", false, "/{index}/_disk_usage");
    initEndpoint("msearch", true, "/_msearch", "/{index}/_msearch");
    initEndpoint("indices.field_usage_stats", false, "/{index}/_field_usage_stats");
    initEndpoint("indices.rollover", false, "/{alias}/_rollover", "/{alias}/_rollover/{new_index}");
    initEndpoint(
        "cat.ml_trained_models",
        false,
        "/_cat/ml/trained_models",
        "/_cat/ml/trained_models/{model_id}");
    initEndpoint(
        "ml.delete_trained_model_alias",
        false,
        "/_ml/trained_models/{model_id}/model_aliases/{model_alias}");
    initEndpoint("indices.get", false, "/{index}");
    initEndpoint("sql.get_async_status", false, "/_sql/async/status/{id}");
    initEndpoint("ilm.stop", false, "/_ilm/stop");
    initEndpoint("security.put_user", false, "/_security/user/{username}");
    initEndpoint(
        "cluster.state",
        false,
        "/_cluster/state",
        "/_cluster/state/{metric}",
        "/_cluster/state/{metric}/{index}");
    initEndpoint("indices.put_settings", false, "/_settings", "/{index}/_settings");
    initEndpoint("knn_search", false, "/{index}/_knn_search");
    initEndpoint("get", false, "/{index}/_doc/{id}");
    initEndpoint("eql.get_status", false, "/_eql/search/status/{id}");
    initEndpoint("ssl.certificates", false, "/_ssl/certificates");
    initEndpoint(
        "ml.get_model_snapshots",
        false,
        "/_ml/anomaly_detectors/{job_id}/model_snapshots/{snapshot_id}",
        "/_ml/anomaly_detectors/{job_id}/model_snapshots");
    initEndpoint(
        "nodes.clear_repositories_metering_archive",
        false,
        "/_nodes/{node_id}/_repositories_metering/{max_archive_version}");
    initEndpoint("security.put_role", false, "/_security/role/{name}");
    initEndpoint(
        "ml.get_influencers", false, "/_ml/anomaly_detectors/{job_id}/results/influencers");
    initEndpoint("transform.upgrade_transforms", false, "/_transform/_upgrade");
    initEndpoint(
        "ml.delete_calendar_event", false, "/_ml/calendars/{calendar_id}/events/{event_id}");
    initEndpoint(
        "indices.get_field_mapping",
        false,
        "/_mapping/field/{fields}",
        "/{index}/_mapping/field/{fields}");
    initEndpoint(
        "transform.preview_transform",
        false,
        "/_transform/{transform_id}/_preview",
        "/_transform/_preview");
    initEndpoint("tasks.list", false, "/_tasks");
    initEndpoint(
        "ml.clear_trained_model_deployment_cache",
        false,
        "/_ml/trained_models/{model_id}/deployment/cache/_clear");
    initEndpoint("cluster.reroute", false, "/_cluster/reroute");
    initEndpoint("security.saml_complete_logout", false, "/_security/saml/complete_logout");
    initEndpoint(
        "indices.simulate_index_template", false, "/_index_template/_simulate_index/{name}");
    initEndpoint("snapshot.get", false, "/_snapshot/{repository}/{snapshot}");
    initEndpoint("ccr.put_auto_follow_pattern", false, "/_ccr/auto_follow/{name}");
    initEndpoint(
        "nodes.hot_threads", false, "/_nodes/hot_threads", "/_nodes/{node_id}/hot_threads");
    initEndpoint(
        "ml.preview_data_frame_analytics",
        false,
        "/_ml/data_frame/analytics/_preview",
        "/_ml/data_frame/analytics/{id}/_preview");
    initEndpoint("indices.flush", false, "/_flush", "/{index}/_flush");
    initEndpoint("cluster.exists_component_template", false, "/_component_template/{name}");
    initEndpoint(
        "snapshot.status",
        false,
        "/_snapshot/_status",
        "/_snapshot/{repository}/_status",
        "/_snapshot/{repository}/{snapshot}/_status");
    initEndpoint("ml.update_datafeed", false, "/_ml/datafeeds/{datafeed_id}/_update");
    initEndpoint("indices.update_aliases", false, "/_aliases");
    initEndpoint("autoscaling.get_autoscaling_capacity", false, "/_autoscaling/capacity");
    initEndpoint("migration.post_feature_upgrade", false, "/_migration/system_features");
    initEndpoint("ml.get_records", false, "/_ml/anomaly_detectors/{job_id}/results/records");
    initEndpoint(
        "indices.get_alias",
        false,
        "/_alias",
        "/_alias/{name}",
        "/{index}/_alias/{name}",
        "/{index}/_alias");
    initEndpoint("logstash.put_pipeline", false, "/_logstash/pipeline/{id}");
    initEndpoint("snapshot.delete_repository", false, "/_snapshot/{repository}");
    initEndpoint(
        "security.has_privileges",
        false,
        "/_security/user/_has_privileges",
        "/_security/user/{user}/_has_privileges");
    initEndpoint("cat.indices", false, "/_cat/indices", "/_cat/indices/{index}");
    initEndpoint(
        "ccr.get_auto_follow_pattern", false, "/_ccr/auto_follow", "/_ccr/auto_follow/{name}");
    initEndpoint("ml.start_datafeed", false, "/_ml/datafeeds/{datafeed_id}/_start");
    initEndpoint("indices.clone", false, "/{index}/_clone/{target}");
    initEndpoint("search_application.delete", false, "/_application/search_application/{name}");
    initEndpoint("security.query_api_keys", false, "/_security/_query/api_key");
    initEndpoint("ml.flush_job", false, "/_ml/anomaly_detectors/{job_id}/_flush");
    initEndpoint(
        "security.clear_cached_privileges",
        false,
        "/_security/privilege/{application}/_clear_cache");
    initEndpoint("indices.exists_index_template", false, "/_index_template/{name}");
    initEndpoint("indices.explain_data_lifecycle", false, "/{index}/_lifecycle/explain");
    initEndpoint("indices.put_alias", false, "/{index}/_alias/{name}", "/{index}/_aliases/{name}");
    initEndpoint(
        "ml.get_buckets",
        false,
        "/_ml/anomaly_detectors/{job_id}/results/buckets/{timestamp}",
        "/_ml/anomaly_detectors/{job_id}/results/buckets");
    initEndpoint(
        "ml.put_trained_model_definition_part",
        false,
        "/_ml/trained_models/{model_id}/definition/{part}");
    initEndpoint("get_script", false, "/_scripts/{id}");
    initEndpoint(
        "ingest.simulate",
        false,
        "/_ingest/pipeline/_simulate",
        "/_ingest/pipeline/{id}/_simulate");
    initEndpoint("indices.migrate_to_data_stream", false, "/_data_stream/_migrate/{name}");
    initEndpoint("enrich.execute_policy", false, "/_enrich/policy/{name}/_execute");
    initEndpoint("indices.split", false, "/{index}/_split/{target}");
    initEndpoint(
        "ml.delete_model_snapshot",
        false,
        "/_ml/anomaly_detectors/{job_id}/model_snapshots/{snapshot_id}");
    initEndpoint(
        "nodes.usage",
        false,
        "/_nodes/usage",
        "/_nodes/{node_id}/usage",
        "/_nodes/usage/{metric}",
        "/_nodes/{node_id}/usage/{metric}");
    initEndpoint("cat.help", false, "/_cat");
    initEndpoint(
        "ml.estimate_model_memory", false, "/_ml/anomaly_detectors/_estimate_model_memory");
    initEndpoint("exists_source", false, "/{index}/_source/{id}");
    initEndpoint("ml.put_data_frame_analytics", false, "/_ml/data_frame/analytics/{id}");
    initEndpoint("security.put_role_mapping", false, "/_security/role_mapping/{name}");
    initEndpoint("rollup.get_rollup_index_caps", false, "/{index}/_rollup/data");
    initEndpoint("transform.reset_transform", false, "/_transform/{transform_id}/_reset");
    initEndpoint(
        "ml.infer_trained_model",
        false,
        "/_ml/trained_models/{model_id}/_infer",
        "/_ml/trained_models/{model_id}/deployment/_infer");
    initEndpoint("reindex", false, "/_reindex");
    initEndpoint("ml.put_trained_model", false, "/_ml/trained_models/{model_id}");
    initEndpoint(
        "cat.ml_jobs", false, "/_cat/ml/anomaly_detectors", "/_cat/ml/anomaly_detectors/{job_id}");
    initEndpoint(
        "search_application.search", false, "/_application/search_application/{name}/_search");
    initEndpoint("ilm.put_lifecycle", false, "/_ilm/policy/{policy}");
    initEndpoint("security.get_token", false, "/_security/oauth2/token");
    initEndpoint("ilm.move_to_step", false, "/_ilm/move/{index}");
    initEndpoint("search_template", true, "/_search/template", "/{index}/_search/template");
    initEndpoint("indices.delete_data_lifecycle", false, "/_data_stream/{name}/_lifecycle");
    initEndpoint("indices.get_data_stream", false, "/_data_stream", "/_data_stream/{name}");
    initEndpoint("ml.get_filters", false, "/_ml/filters", "/_ml/filters/{filter_id}");
    initEndpoint(
        "cat.ml_datafeeds", false, "/_cat/ml/datafeeds", "/_cat/ml/datafeeds/{datafeed_id}");
    initEndpoint("rollup.rollup_search", false, "/{index}/_rollup_search");
    initEndpoint("ml.put_job", false, "/_ml/anomaly_detectors/{job_id}");
    initEndpoint("update_by_query_rethrottle", false, "/_update_by_query/{task_id}/_rethrottle");
    initEndpoint("indices.delete_index_template", false, "/_index_template/{name}");
    initEndpoint("indices.reload_search_analyzers", false, "/{index}/_reload_search_analyzers");
    initEndpoint("cluster.get_settings", false, "/_cluster/settings");
    initEndpoint("cluster.put_settings", false, "/_cluster/settings");
    initEndpoint("transform.put_transform", false, "/_transform/{transform_id}");
    initEndpoint("watcher.stats", false, "/_watcher/stats", "/_watcher/stats/{metric}");
    initEndpoint("ccr.delete_auto_follow_pattern", false, "/_ccr/auto_follow/{name}");
    initEndpoint("mtermvectors", false, "/_mtermvectors", "/{index}/_mtermvectors");
    initEndpoint("license.post", false, "/_license");
    initEndpoint("xpack.info", false, "/_xpack");
    initEndpoint("dangling_indices.import_dangling_index", false, "/_dangling/{index_uuid}");
    initEndpoint(
        "nodes.get_repositories_metering_info", false, "/_nodes/{node_id}/_repositories_metering");
    initEndpoint("transform.get_transform_stats", false, "/_transform/{transform_id}/_stats");
    initEndpoint("mget", false, "/_mget", "/{index}/_mget");
    initEndpoint("security.get_builtin_privileges", false, "/_security/privilege/_builtin");
    initEndpoint(
        "ml.update_model_snapshot",
        false,
        "/_ml/anomaly_detectors/{job_id}/model_snapshots/{snapshot_id}/_update");
    initEndpoint("ml.info", false, "/_ml/info");
    initEndpoint("indices.exists_template", false, "/_template/{name}");
    initEndpoint(
        "watcher.ack_watch",
        false,
        "/_watcher/watch/{watch_id}/_ack",
        "/_watcher/watch/{watch_id}/_ack/{action_id}");
    initEndpoint("security.get_user", false, "/_security/user/{username}", "/_security/user");
    initEndpoint("shutdown.get_node", false, "/_nodes/shutdown", "/_nodes/{node_id}/shutdown");
    initEndpoint("watcher.start", false, "/_watcher/_start");
    initEndpoint("indices.shrink", false, "/{index}/_shrink/{target}");
    initEndpoint("license.post_start_basic", false, "/_license/start_basic");
    initEndpoint("xpack.usage", false, "/_xpack/usage");
    initEndpoint("ilm.delete_lifecycle", false, "/_ilm/policy/{policy}");
    initEndpoint("ccr.follow_info", false, "/{index}/_ccr/info");
    initEndpoint("ml.put_calendar_job", false, "/_ml/calendars/{calendar_id}/jobs/{job_id}");
    initEndpoint("rollup.put_job", false, "/_rollup/job/{id}");
    initEndpoint("clear_scroll", false, "/_search/scroll");
    initEndpoint("ml.delete_data_frame_analytics", false, "/_ml/data_frame/analytics/{id}");
    initEndpoint("security.get_api_key", false, "/_security/api_key");
    initEndpoint("cat.health", false, "/_cat/health");
    initEndpoint("security.invalidate_token", false, "/_security/oauth2/token");
    initEndpoint("slm.delete_lifecycle", false, "/_slm/policy/{policy_id}");
    initEndpoint(
        "ml.stop_trained_model_deployment",
        false,
        "/_ml/trained_models/{model_id}/deployment/_stop");
    initEndpoint("monitoring.bulk", false, "/_monitoring/bulk", "/_monitoring/{type}/bulk");
    initEndpoint(
        "indices.stats",
        false,
        "/_stats",
        "/_stats/{metric}",
        "/{index}/_stats",
        "/{index}/_stats/{metric}");
    initEndpoint(
        "searchable_snapshots.cache_stats",
        false,
        "/_searchable_snapshots/cache/stats",
        "/_searchable_snapshots/{node_id}/cache/stats");
    initEndpoint("async_search.submit", true, "/_async_search", "/{index}/_async_search");
    initEndpoint("rollup.get_jobs", false, "/_rollup/job/{id}", "/_rollup/job");
    initEndpoint(
        "ml.revert_model_snapshot",
        false,
        "/_ml/anomaly_detectors/{job_id}/model_snapshots/{snapshot_id}/_revert");
    initEndpoint("transform.delete_transform", false, "/_transform/{transform_id}");
    initEndpoint("cluster.pending_tasks", false, "/_cluster/pending_tasks");
    initEndpoint(
        "ml.get_model_snapshot_upgrade_stats",
        false,
        "/_ml/anomaly_detectors/{job_id}/model_snapshots/{snapshot_id}/_upgrade/_stats");
    initEndpoint(
        "ml.get_categories",
        false,
        "/_ml/anomaly_detectors/{job_id}/results/categories/{category_id}",
        "/_ml/anomaly_detectors/{job_id}/results/categories");
    initEndpoint("ccr.pause_follow", false, "/{index}/_ccr/pause_follow");
    initEndpoint("security.authenticate", false, "/_security/_authenticate");
    initEndpoint("enrich.stats", false, "/_enrich/_stats");
    initEndpoint(
        "ml.put_trained_model_alias",
        false,
        "/_ml/trained_models/{model_id}/model_aliases/{model_alias}");
    initEndpoint(
        "ml.get_overall_buckets", false, "/_ml/anomaly_detectors/{job_id}/results/overall_buckets");
    initEndpoint("indices.get_template", false, "/_template", "/_template/{name}");
    initEndpoint("security.delete_role_mapping", false, "/_security/role_mapping/{name}");
    initEndpoint("ml.get_datafeeds", false, "/_ml/datafeeds/{datafeed_id}", "/_ml/datafeeds");
    initEndpoint("slm.execute_lifecycle", false, "/_slm/policy/{policy_id}/_execute");
    initEndpoint("close_point_in_time", false, "/_pit");
    initEndpoint("snapshot.cleanup_repository", false, "/_snapshot/{repository}/_cleanup");
    initEndpoint("autoscaling.get_autoscaling_policy", false, "/_autoscaling/policy/{name}");
    initEndpoint("slm.put_lifecycle", false, "/_slm/policy/{policy_id}");
    initEndpoint("ml.get_jobs", false, "/_ml/anomaly_detectors/{job_id}", "/_ml/anomaly_detectors");
    initEndpoint(
        "ml.get_trained_models_stats",
        false,
        "/_ml/trained_models/{model_id}/_stats",
        "/_ml/trained_models/_stats");
    initEndpoint("ml.validate_detector", false, "/_ml/anomaly_detectors/_validate/detector");
    initEndpoint("watcher.put_watch", false, "/_watcher/watch/{id}");
    initEndpoint("transform.update_transform", false, "/_transform/{transform_id}/_update");
    initEndpoint("ml.post_calendar_events", false, "/_ml/calendars/{calendar_id}/events");
    initEndpoint("migration.get_feature_upgrade_status", false, "/_migration/system_features");
    initEndpoint("get_script_context", false, "/_script_context");
    initEndpoint("ml.put_filter", false, "/_ml/filters/{filter_id}");
    initEndpoint("ml.update_job", false, "/_ml/anomaly_detectors/{job_id}/_update");
    initEndpoint("ingest.geo_ip_stats", false, "/_ingest/geoip/stats");
    initEndpoint("security.delete_user", false, "/_security/user/{username}");
    initEndpoint("indices.unfreeze", false, "/{index}/_unfreeze");
    initEndpoint("snapshot.create_repository", false, "/_snapshot/{repository}");
    initEndpoint(
        "cluster.get_component_template",
        false,
        "/_component_template",
        "/_component_template/{name}");
    initEndpoint("ilm.migrate_to_data_tiers", false, "/_ilm/migrate_to_data_tiers");
    initEndpoint("indices.refresh", false, "/_refresh", "/{index}/_refresh");
    initEndpoint("ml.get_calendars", false, "/_ml/calendars", "/_ml/calendars/{calendar_id}");
    initEndpoint("watcher.deactivate_watch", false, "/_watcher/watch/{watch_id}/_deactivate");
    initEndpoint("cluster.health", false, "/_cluster/health", "/_cluster/health/{index}");
    initEndpoint("dangling_indices.delete_dangling_index", false, "/_dangling/{index_uuid}");
    initEndpoint("health_report", false, "/_health_report", "/_health_report/{feature}");
    initEndpoint("watcher.query_watches", false, "/_watcher/_query/watches");
    initEndpoint("ccr.unfollow", false, "/{index}/_ccr/unfollow");
    initEndpoint("ml.validate", false, "/_ml/anomaly_detectors/_validate");
    initEndpoint("cat.plugins", false, "/_cat/plugins");
    initEndpoint(
        "watcher.execute_watch",
        false,
        "/_watcher/watch/{id}/_execute",
        "/_watcher/watch/_execute");
    initEndpoint("search_shards", false, "/_search_shards", "/{index}/_search_shards");
    initEndpoint("cat.shards", false, "/_cat/shards", "/_cat/shards/{index}");
    initEndpoint("ml.delete_job", false, "/_ml/anomaly_detectors/{job_id}");
    initEndpoint("ilm.start", false, "/_ilm/start");
    initEndpoint("security.get_user_profile", false, "/_security/profile/{uid}");
    initEndpoint("indices.modify_data_stream", false, "/_data_stream/_modify");
    initEndpoint("indices.exists_alias", false, "/_alias/{name}", "/{index}/_alias/{name}");
    initEndpoint("rollup.stop_job", false, "/_rollup/job/{id}/_stop");
    initEndpoint("dangling_indices.list_dangling_indices", false, "/_dangling");
    initEndpoint("snapshot.delete", false, "/_snapshot/{repository}/{snapshot}");
    initEndpoint("security.activate_user_profile", false, "/_security/profile/_activate");
    initEndpoint(
        "ml.start_trained_model_deployment",
        false,
        "/_ml/trained_models/{model_id}/deployment/_start");
    initEndpoint("transform.start_transform", false, "/_transform/{transform_id}/_start");
    initEndpoint("cat.repositories", false, "/_cat/repositories");
    initEndpoint("ilm.get_status", false, "/_ilm/status");
    initEndpoint("shutdown.delete_node", false, "/_nodes/{node_id}/shutdown");
    initEndpoint(
        "nodes.stats",
        false,
        "/_nodes/stats",
        "/_nodes/{node_id}/stats",
        "/_nodes/stats/{metric}",
        "/_nodes/{node_id}/stats/{metric}",
        "/_nodes/stats/{metric}/{index_metric}",
        "/_nodes/{node_id}/stats/{metric}/{index_metric}");
    initEndpoint("get_script_languages", false, "/_script_language");
    initEndpoint("slm.execute_retention", false, "/_slm/_execute_retention");
    initEndpoint(
        "security.get_service_accounts",
        false,
        "/_security/service/{namespace}/{service}",
        "/_security/service/{namespace}",
        "/_security/service");
    initEndpoint("shutdown.put_node", false, "/_nodes/{node_id}/shutdown");
    initEndpoint("indices.resolve_index", false, "/_resolve/index/{name}");
    initEndpoint("search", true, "/_search", "/{index}/_search");
    initEndpoint("sql.get_async", false, "/_sql/async/{id}");
    initEndpoint("delete_by_query_rethrottle", false, "/_delete_by_query/{task_id}/_rethrottle");
    initEndpoint("transform.get_transform", false, "/_transform/{transform_id}", "/_transform");
    initEndpoint("security.invalidate_api_key", false, "/_security/api_key");
    initEndpoint("security.saml_prepare_authentication", false, "/_security/saml/prepare");
    initEndpoint(
        "ml.get_memory_stats", false, "/_ml/memory/_stats", "/_ml/memory/{node_id}/_stats");
    initEndpoint("ccr.stats", false, "/_ccr/stats");
    initEndpoint("indices.forcemerge", false, "/_forcemerge", "/{index}/_forcemerge");
    initEndpoint("indices.delete_template", false, "/_template/{name}");
    initEndpoint("sql.delete_async", false, "/_sql/async/delete/{id}");
    initEndpoint("security.update_api_key", false, "/_security/api_key/{id}");
    initEndpoint(
        "security.create_service_token",
        false,
        "/_security/service/{namespace}/{service}/credential/token/{name}",
        "/_security/service/{namespace}/{service}/credential/token");
    initEndpoint("license.get_trial_status", false, "/_license/trial_status");
    initEndpoint("searchable_snapshots.mount", false, "/_snapshot/{repository}/{snapshot}/_mount");
    initEndpoint("security.grant_api_key", false, "/_security/api_key/grant");
    initEndpoint("ilm.retry", false, "/{index}/_ilm/retry");
    initEndpoint("ml.reset_job", false, "/_ml/anomaly_detectors/{job_id}/_reset");
    initEndpoint("ml.close_job", false, "/_ml/anomaly_detectors/{job_id}/_close");
    initEndpoint(
        "ml.explain_data_frame_analytics",
        false,
        "/_ml/data_frame/analytics/_explain",
        "/_ml/data_frame/analytics/{id}/_explain");
    initEndpoint(
        "security.clear_cached_service_tokens",
        false,
        "/_security/service/{namespace}/{service}/credential/token/{name}/_clear_cache");
    initEndpoint("search_mvt", false, "/{index}/_mvt/{field}/{zoom}/{x}/{y}");
  }

  private ElasticsearchEndpointMap() {}

  private static void initEndpoint(String endpointId, boolean isSearchEndpoint, String... routes) {
    ElasticsearchEndpointDefinition endpointDef =
        new ElasticsearchEndpointDefinition(endpointId, routes, isSearchEndpoint);
    routesMap.put(endpointId, endpointDef);
  }

  @Nullable
  public static ElasticsearchEndpointDefinition get(String endpointId) {
    return routesMap.get(endpointId);
  }

  public static Collection<ElasticsearchEndpointDefinition> getAllEndpoints() {
    return routesMap.values();
  }
}
