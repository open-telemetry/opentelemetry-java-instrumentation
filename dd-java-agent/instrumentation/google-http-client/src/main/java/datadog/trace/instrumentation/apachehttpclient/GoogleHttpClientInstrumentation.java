package datadog.trace.instrumentation.apachehttpclient;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import java.util.Map;

@AutoService(Instrumenter.class)
public class GoogleHttpClientInstrumentation extends Instrumenter.Default {
  public GoogleHttpClientInstrumentation() {
    super("google-http-client");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return null;
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return null;
  }
}
