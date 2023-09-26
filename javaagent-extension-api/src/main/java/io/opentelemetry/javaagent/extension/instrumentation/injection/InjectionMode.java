package io.opentelemetry.javaagent.extension.instrumentation.injection;

public enum InjectionMode {

  CLASS_ONLY
  //TODO: implement the modes RESOURCE_ONLY and CLASS_AND_RESOURCE
  //This will require a custom URL implementation for byte arrays, similar to how bytebuddy's ByteArrayClassLoader does it

}
