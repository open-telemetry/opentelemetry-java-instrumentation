package io.opentelemetry.instrumentation.api.instrumenter.peer;

import io.opentelemetry.api.common.AttributeKey;

public final class SemanticAttributes {
    private SemanticAttributes() {}
    
    public static final AttributeKey<String> PEER_SERVICE = AttributeKey.stringKey("peer.service");
    public static final AttributeKey<String> PEER_SERVICE_NAMESPACE = AttributeKey.stringKey("peer.service.namespace");
    public static final AttributeKey<String> PEER_SERVICE_VERSION = AttributeKey.stringKey("peer.service.version");
    public static final AttributeKey<String> PEER_SERVICE_INSTANCE_ID = AttributeKey.stringKey("peer.service.instance.id");
    public static final AttributeKey<String> PEER_DEPLOYMENT_ENVIRONMENT = AttributeKey.stringKey("peer.deployment.environment");
}
