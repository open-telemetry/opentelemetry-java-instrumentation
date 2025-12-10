package io.opentelemetry.instrumentation.sofa.rpc.api;

public interface ErrorService {
  String throwException();

  String throwBusinessException();

  String timeout();
}
