package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkHttpClientTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.extension.trace.propagation.AwsXRayPropagator;
import io.opentelemetry.instrumentation.api.tracer.DefaultHttpClientOperation;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

public class DefaultAwsSdkOperation extends DefaultHttpClientOperation<SdkHttpResponse>
    implements AwsSdkOperation {

  public DefaultAwsSdkOperation(Context context, Context parentContext) {
    super(context, parentContext, tracer());
  }

  @Override
  public void inject(SdkHttpRequest.Builder request) {
    inject(request, AwsSdkInjectAdapter.INSTANCE, AwsXRayPropagator.getInstance());
  }

  @Override
  public void onRequest(SdkHttpRequest request) {
    tracer().onRequest(getSpan(), request);
  }
}
