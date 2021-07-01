package io.opentelemetry.instrumentation.spring.autoconfigure.propagators;

import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.extension.trace.propagation.JaegerPropagator;
import io.opentelemetry.extension.trace.propagation.OtTracePropagator;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.util.ClassUtils;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class CompositeTextMapPropagator implements TextMapPropagator {

  private final Map<PropagationType, TextMapPropagator> mapping = new HashMap<>();
  private final List<PropagationType> types;

  public CompositeTextMapPropagator(BeanFactory beanFactory, List<PropagationType> types){

    this.types = types;

    if(isOnClasspath("io.opentelemetry.extension.trace.propagation.B3Propagator")){
      mapping.put(PropagationType.B3, beanFactory.getBeanProvider(B3Propagator.class).getIfAvailable(B3Propagator::injectingSingleHeader));
    }
    if(isOnClasspath("io.opentelemetry.extension.trace.propagation.JaegerPropagator")){
      mapping.put(PropagationType.JAEGER, beanFactory.getBeanProvider(JaegerPropagator.class).getIfAvailable(JaegerPropagator::getInstance));
    }
    if(isOnClasspath("io.opentelemetry.extension.trace.propagation.OtTracerPropagator")){
      mapping.put(PropagationType.OT_TRACER, beanFactory.getBeanProvider(OtTracePropagator.class).getIfAvailable(OtTracePropagator::getInstance));
    }

    mapping.put(PropagationType.W3C, W3CTraceContextPropagator.getInstance());
    mapping.put(PropagationType.BAGGAGE, W3CBaggagePropagator.getInstance());
    mapping.put(PropagationType.NOOP, TextMapPropagator.noop());

  }

   private static boolean isOnClasspath(String clazz){
    return ClassUtils.isPresent(clazz, null);
  }


  @Override
  public Collection<String> fields() {
    return types.stream().map(key -> mapping.getOrDefault(key, TextMapPropagator.noop())).flatMap(p -> p.fields().stream()).collect(Collectors.toList());
  }

  @Override
  public <C> void inject(Context context, @Nullable C c, TextMapSetter<C> textMapSetter) {
    types.stream().map(key -> mapping.getOrDefault(key, TextMapPropagator.noop())).forEach(p -> p.inject(context, c, textMapSetter));
  }

  @Override
  public <C> Context extract(Context context, @Nullable C c, TextMapGetter<C> textMapGetter) {
    for (PropagationType type : types) {

      TextMapPropagator propagator = mapping.get(type);
      if(propagator == null || propagator == TextMapPropagator.noop()){
        continue;
      }

      Context extractedContext = propagator.extract(context, c, textMapGetter);
      Span span = Span.fromContextOrNull(extractedContext);
      Baggage baggage = Baggage.fromContextOrNull(extractedContext);
      if(span != null || baggage != null){
        return extractedContext;
      }
    }
    return context;
  }
}
