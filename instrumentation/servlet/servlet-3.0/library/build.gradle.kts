plugins {
  id("otel.library-instrumentation")
  id("com.gradleup.shadow")
}

dependencies {
  compileOnly("javax.servlet:javax.servlet-api:3.0.1")

  // Not great practice for library instrumentation to depend on javaagent classes. (Usually better the other way.)
  implementation(project(":javaagent-extension-api"))
  implementation(project(":instrumentation:servlet:servlet-common:bootstrap"))
  implementation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))
  implementation(project(":instrumentation:servlet:servlet-common:javaagent"))
  implementation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
}

tasks {
  shadowJar {
    archiveClassifier = ""

    duplicatesStrategy = DuplicatesStrategy.FAIL
    exclude {
      it.path.startsWith("META-INF") && !it.path.startsWith("META-INF/io/opentelemetry/instrumentation/")
    }

    minimize()

    dependencies {
      include(project(":javaagent-extension-api"))
      include(project(":instrumentation:servlet:servlet-common:bootstrap"))
      include(project(":instrumentation:servlet:servlet-common:javaagent"))
      include(project(":instrumentation:servlet:servlet-javax-common:javaagent"))
      include(project(":instrumentation:servlet:servlet-3.0:javaagent"))
    }

    relocate( // :instrumentation:servlet:servlet-common:bootstrap
      "io.opentelemetry.javaagent.bootstrap.servlet",
      "io.opentelemetry.instrumentation.servlet.v3_0.shaded.servlet_bootstrap"
    )
    relocate( // :javaagent-extension-api
      "io.opentelemetry.javaagent.bootstrap",
      "io.opentelemetry.instrumentation.servlet.v3_0.shaded.extension_api"
    ) {
      exclude("io.opentelemetry.javaagent.bootstrap.servlet")
    }
    relocate( // :instrumentation:servlet:servlet-javax-common:javaagent
      "io.opentelemetry.javaagent.instrumentation.servlet.javax",
      "io.opentelemetry.instrumentation.servlet.v3_0.shaded.servlet_javax"
    )
    relocate( // :instrumentation:servlet:servlet-3.0:javaagent
      "io.opentelemetry.javaagent.instrumentation.servlet.v3_0",
      "io.opentelemetry.instrumentation.servlet.v3_0.shaded.servlet_v3_0"
    )
    relocate( // :instrumentation:servlet:servlet-common:javaagent
      "io.opentelemetry.javaagent.instrumentation.servlet",
      "io.opentelemetry.instrumentation.servlet.v3_0.shaded.servlet_javaagent"
    ) {
      exclude("io.opentelemetry.javaagent.instrumentation.servlet.javax")
      exclude("io.opentelemetry.javaagent.instrumentation.servlet.v3_0")
    }
  }
}
