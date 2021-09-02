plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("org.apache.dubbo:dubbo:2.7.0")

  testImplementation(project(":instrumentation:apache-dubbo-2.7:testing"))

  testLibrary("org.apache.dubbo:dubbo-config-api:2.7.0")
  latestDepTestLibrary("org.apache.dubbo:dubbo:2.+")
  latestDepTestLibrary("org.apache.dubbo:dubbo-config-api:2.+")
}
