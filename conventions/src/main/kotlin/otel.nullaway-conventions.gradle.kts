import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway

plugins {
  id("net.ltgt.nullaway")

  id("otel.errorprone-conventions")
}

dependencies {
  errorprone("com.uber.nullaway:nullaway")
}

nullaway {
  annotatedPackages.addAll("io.opentelemetry", "com.linecorp.armeria,com.google.common")
}

tasks {
  withType<JavaCompile>().configureEach {
    if (!name.contains("test", ignoreCase = true)) {
      options.errorprone.nullaway {
        severity.set(CheckSeverity.ERROR)
      }
    }
    options.errorprone.nullaway {
      customInitializerAnnotations.add("org.openjdk.jmh.annotations.Setup")
      excludedFieldAnnotations.add("org.mockito.Mock")
      excludedFieldAnnotations.add("org.mockito.InjectMocks")
    }
  }
}
