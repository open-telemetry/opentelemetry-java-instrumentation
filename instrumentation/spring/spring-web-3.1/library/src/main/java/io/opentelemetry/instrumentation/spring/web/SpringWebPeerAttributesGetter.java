package io.opentelemetry.instrumentation.spring.web;

import io.opentelemetry.instrumentation.api.instrumenter.peer.PeerAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.peer.PeerResourceAttributes;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

import javax.annotation.Nullable;

public class SpringWebPeerAttributesGetter
    implements PeerAttributesGetter<HttpRequest, ClientHttpResponse> {
    
    @Nullable
    @Override
    public String peerServiceName(HttpRequest httpRequest, @Nullable ClientHttpResponse clientHttpResponse) {
        return httpRequest.getHeaders().getFirst(PeerResourceAttributes.PEER_SERVICE);
    }
    
    @Nullable
    @Override
    public String peerServiceNamespace(HttpRequest httpRequest, @Nullable ClientHttpResponse clientHttpResponse) {
        return httpRequest.getHeaders().getFirst(PeerResourceAttributes.PEER_SERVICE_NAMESPACE);
    }
    
    @Nullable
    @Override
    public String peerServiceVersion(HttpRequest httpRequest, @Nullable ClientHttpResponse clientHttpResponse) {
        return httpRequest.getHeaders().getFirst(PeerResourceAttributes.PEER_SERVICE_VERSION);
    }
    
    @Nullable
    @Override
    public String peerServiceInstanceID(HttpRequest httpRequest, @Nullable ClientHttpResponse clientHttpResponse) {
        return httpRequest.getHeaders().getFirst(PeerResourceAttributes.PEER_SERVICE_INSTANCE_ID);
    }
    
    @Nullable
    @Override
    public String peerDeploymentEnvironment(HttpRequest httpRequest, @Nullable ClientHttpResponse clientHttpResponse) {
        return httpRequest.getHeaders().getFirst(PeerResourceAttributes.PEER_DEPLOYMENT_ENVIRONMENT);
    }
}
