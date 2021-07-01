package io.opentelemetry.instrumentation.spring.autoconfigure.propagators;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Collections;
import java.util.List;

@ConfigurationProperties("otel.propagators")
public final class PropagationProperties {

  private List<PropagationType> type = Collections.singletonList(PropagationType.W3C);

  public List<PropagationType> getType() {
    return type;
  }

  public void setType(List<PropagationType> type){
    this.type = type;
  }


}
