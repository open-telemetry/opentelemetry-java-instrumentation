package io.opentelemetry.instrumentation.api.instrumenter.peer;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

public final class PeerAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {
    
    private final PeerAttributesGetter<REQUEST, RESPONSE> getter;
    
    public static <REQUEST, RESPONSE> PeerAttributesExtractor<REQUEST, RESPONSE> create(
            PeerAttributesGetter<REQUEST, RESPONSE> getter) {
        return new PeerAttributesExtractor<>(getter);
    }
    
    public PeerAttributesExtractor(PeerAttributesGetter<REQUEST, RESPONSE> getter) {
        this.getter = getter;
    }
    
    @Override
    public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {}
    
    @Override
    public void onEnd(AttributesBuilder attributes,
            Context context,
            REQUEST request,
            @Nullable RESPONSE response,
            @Nullable Throwable error) {
        
        String peerServiceName = getter.peerServiceName(request, response);
        if (peerServiceName != null) {
            internalSet(attributes, SemanticAttributes.PEER_SERVICE, getter.peerServiceName(request, response));
        }
        
        String peerServiceNamespace = getter.peerServiceNamespace(request, response);
        if (peerServiceNamespace != null) {
            internalSet(attributes, SemanticAttributes.PEER_SERVICE_NAMESPACE, peerServiceNamespace);
        }
    
        String peerDeploymentEnvironment = getter.peerDeploymentEnvironment(request, response);
        if (peerDeploymentEnvironment != null) {
            internalSet(attributes, SemanticAttributes.PEER_DEPLOYMENT_ENVIRONMENT, peerDeploymentEnvironment);
        }
        
        String peerServiceVersion = getter.peerServiceVersion(request, response);
        if (peerServiceVersion != null) {
            internalSet(attributes, SemanticAttributes.PEER_SERVICE_VERSION, peerServiceVersion);
        }
        
        String peerServiceInstanceID = getter.peerServiceInstanceID(request, response);
        if (peerServiceInstanceID != null) {
            internalSet(attributes, SemanticAttributes.PEER_SERVICE_INSTANCE_ID, peerServiceInstanceID);
        }
    }
}
