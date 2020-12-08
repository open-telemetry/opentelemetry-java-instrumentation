package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.instrumentation.api.tracer.NoopHttpClientOperation;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

class NoopAwsSdkOperation extends NoopHttpClientOperation<SdkHttpResponse>
    implements AwsSdkOperation {
  private static final NoopAwsSdkOperation INSTANCE = new NoopAwsSdkOperation();

  static NoopAwsSdkOperation noop() {
    return INSTANCE;
  }

  @Override
  public void inject(SdkHttpRequest.Builder request) {}

  @Override
  public void onRequest(SdkHttpRequest request) {}
}
