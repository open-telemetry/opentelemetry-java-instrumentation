import ru.vyarus.gradle.plugin.animalsniffer.AnimalSniffer

plugins {
  id("org.xbib.gradle.plugin.jflex")

  id("otel.java-conventions")
  id("otel.animalsniffer-conventions")
  id("otel.jacoco-conventions")
  id("otel.japicmp-conventions")
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

  val testStatementSanitizerConfig by registering(Test::class) {
    filter {
      includeTestsMatching("StatementSanitizationConfigTest")
      isFailOnNoMatchingTests = false
    }
    include("**/StatementSanitizationConfigTest.*")
    jvmArgs("-Dotel.instrumentation.common.db-statement-sanitizer.enabled=false")
  }

  test {
    dependsOn(testStatementSanitizerConfig)

    filter {
      excludeTestsMatching("StatementSanitizationConfigTest")
      isFailOnNoMatchingTests = false
    }
  }

  withType<AnimalSniffer>().configureEach {
    // we catch NoClassDefFoundError when use caffeine3 is not available on classpath and fall back to caffeine2
    exclude("**/internal/shaded/caffeine3/**")

    ignoreClasses = listOf(
      // ignore shaded caffeine3 references
      "io.opentelemetry.instrumentation.api.internal.shaded.caffeine3.cache.Caffeine",
      "io.opentelemetry.instrumentation.api.internal.shaded.caffeine3.cache.Cache",

      // these standard Java classes seem to be desugared properly
      "java.lang.ClassValue",
      "java.util.concurrent.CompletableFuture",
      "java.util.concurrent.CompletionException",
      "java.util.concurrent.CompletionStage",
      "java.util.concurrent.ConcurrentHashMap",
      "java.util.concurrent.ForkJoinPool",
      "java.util.concurrent.atomic.LongAdder",
      "sun.misc.Unsafe"
    )
  }
}
