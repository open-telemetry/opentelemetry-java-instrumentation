plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:opensearch:opensearch-rest-common-1.0:javaagent"))
  // getterFallsBackToHttpMethodWhenNoRouteMatches instantiates OpenSearchRestAttributesGetter,
  // which implements DbClientAttributesGetter from this module.
  testImplementation(project(":instrumentation-api-incubator"))
}
