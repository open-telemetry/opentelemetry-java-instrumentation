package io.opentelemetry.instrumentation.api.instrumenter.peer;

public final class PeerResourceAttributes {
    private PeerResourceAttributes() {}
    
    public static final String PEER_SERVICE = "x-otel-peer-service";
    public static final String PEER_SERVICE_NAMESPACE = "x-otel-peer-service-namespace";
    public static final String PEER_SERVICE_VERSION = "x-otel-peer-service-version";
    public static final String PEER_SERVICE_INSTANCE_ID = "x-otel-peer-service-instance-id";
    public static final String PEER_DEPLOYMENT_ENVIRONMENT = "x-otel-peer-deployment-environment";
}
