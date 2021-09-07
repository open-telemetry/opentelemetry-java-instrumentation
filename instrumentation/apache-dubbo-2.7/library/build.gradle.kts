plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  // required for PeerServiceAttributesExtractor
  implementation(project(":javaagent-instrumentation-api"))

  library("org.apache.dubbo:dubbo:2.7.0")

  testImplementation(project(":instrumentation:apache-dubbo-2.7:testing"))

  testLibrary("org.apache.dubbo:dubbo-config-api:2.7.0")
  latestDepTestLibrary("org.apache.dubbo:dubbo:2.+")
  latestDepTestLibrary("org.apache.dubbo:dubbo-config-api:2.+")
}
