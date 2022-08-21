package io.opentelemetry.instrumentation.api.instrumenter.peer;

import javax.annotation.Nullable;

public interface PeerAttributesGetter<REQUEST, RESPONSE> {
    @Nullable
    String peerServiceName(REQUEST request, @Nullable RESPONSE response);
    
    @Nullable
    String peerServiceNamespace(REQUEST request,@Nullable RESPONSE response);
    
    @Nullable
    String peerServiceVersion(REQUEST request, @Nullable RESPONSE response);
    
    @Nullable
    String peerServiceInstanceID(REQUEST request, @Nullable RESPONSE response);
    
    @Nullable
    String peerDeploymentEnvironment(REQUEST request, @Nullable RESPONSE response);
}
