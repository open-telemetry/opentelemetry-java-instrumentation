plugins {
  id("org.xbib.gradle.plugin.jflex")

  id("otel.java-conventions")
  id("otel.jacoco-conventions")
  id("otel.publish-conventions")
}

sourceSets {
  main {
    java {
      // gradle-jflex-plugin has a bug in that it always looks for the last srcDir in this source
      // set to generate into. By default it would be the src/main directory itself.
      srcDir("$buildDir/generated/sources/jflex")
    }

    val cachingShadedDeps = project(":instrumentation-api-caching")
    output.dir(cachingShadedDeps.file("build/extracted/shadow"), "builtBy" to ":instrumentation-api-caching:extractShadowJar")
  }
}

group = "io.opentelemetry.instrumentation"

dependencies {
  compileOnly(project(":instrumentation-api-caching"))

  api("io.opentelemetry:opentelemetry-api")
  api("io.opentelemetry:opentelemetry-semconv")

  implementation("io.opentelemetry:opentelemetry-api-metrics")
  implementation("org.slf4j:slf4j-api")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testCompileOnly(project(":instrumentation-api-caching"))
  testImplementation(project(":testing-common"))
  testImplementation("org.mockito:mockito-core")
  testImplementation("org.mockito:mockito-junit-jupiter")
  testImplementation("org.assertj:assertj-core")
  testImplementation("org.awaitility:awaitility")
  testImplementation("io.opentelemetry:opentelemetry-sdk-metrics")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
}

tasks {
  sourcesJar {
    dependsOn("generateJflex")
  }
}
