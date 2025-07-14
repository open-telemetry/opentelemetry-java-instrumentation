import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("com.gradleup.shadow")
  id("otel.java-conventions")
}

description = "opentelemetry-api shaded for internal javaagent usage"
group = "io.opentelemetry.javaagent"

val latestDeps by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
}
val v1_10Deps by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
}
val v1_15Deps by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
}
val v1_27Deps by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
}
val v1_31Deps by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
  exclude("io.opentelemetry", "opentelemetry-bom-alpha")
}
val v1_32Deps by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
  exclude("io.opentelemetry", "opentelemetry-bom-alpha")
}
val v1_37Deps by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
  exclude("io.opentelemetry", "opentelemetry-bom-alpha")
}
val v1_38Deps by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
  exclude("io.opentelemetry", "opentelemetry-bom-alpha")
}
val v1_40Deps by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
  exclude("io.opentelemetry", "opentelemetry-bom-alpha")
}
val v1_42Deps by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
  exclude("io.opentelemetry", "opentelemetry-bom-alpha")
}
val v1_47Deps by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
  exclude("io.opentelemetry", "opentelemetry-bom-alpha")
}
val v1_50Deps by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
  exclude("io.opentelemetry", "opentelemetry-bom-alpha")
}
val v1_52Deps by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
  exclude("io.opentelemetry", "opentelemetry-bom-alpha")
}
// configuration for publishing the shadowed artifact
val v1_10 by configurations.creating {
  isCanBeConsumed = true
  isCanBeResolved = false
}
val v1_15 by configurations.creating {
  isCanBeConsumed = true
  isCanBeResolved = false
}
val v1_27 by configurations.creating {
  isCanBeConsumed = true
  isCanBeResolved = false
}
val v1_31 by configurations.creating {
  isCanBeConsumed = true
  isCanBeResolved = false
}
val v1_32 by configurations.creating {
  isCanBeConsumed = true
  isCanBeResolved = false
}
val v1_37 by configurations.creating {
  isCanBeConsumed = true
  isCanBeResolved = false
}
val v1_38 by configurations.creating {
  isCanBeConsumed = true
  isCanBeResolved = false
}
val v1_40 by configurations.creating {
  isCanBeConsumed = true
  isCanBeResolved = false
}
val v1_42 by configurations.creating {
  isCanBeConsumed = true
  isCanBeResolved = false
}
val v1_47 by configurations.creating {
  isCanBeConsumed = true
  isCanBeResolved = false
}
val v1_50 by configurations.creating {
  isCanBeConsumed = true
  isCanBeResolved = false
}
val v1_52 by configurations.creating {
  isCanBeConsumed = true
  isCanBeResolved = false
}

dependencies {
  latestDeps("io.opentelemetry:opentelemetry-api")

  listOf("opentelemetry-api", "opentelemetry-context").forEach {
    v1_10Deps("io.opentelemetry:$it") {
      version {
        strictly("1.10.0")
      }
    }
    v1_15Deps("io.opentelemetry:$it") {
      version {
        strictly("1.15.0")
      }
    }
    v1_27Deps("io.opentelemetry:$it") {
      version {
        strictly("1.27.0")
      }
    }
    v1_31Deps("io.opentelemetry:$it") {
      version {
        strictly("1.31.0")
      }
    }
    v1_32Deps("io.opentelemetry:$it") {
      version {
        strictly("1.32.0")
      }
    }
  }

  listOf("opentelemetry-extension-incubator").forEach {
    v1_31Deps("io.opentelemetry:$it") {
      version {
        strictly("1.31.0-alpha")
      }
    }
    v1_32Deps("io.opentelemetry:$it") {
      version {
        strictly("1.32.0-alpha")
      }
    }
  }

  listOf("opentelemetry-api-incubator").forEach {
    v1_37Deps("io.opentelemetry:$it") {
      version {
        strictly("1.37.0-alpha")
      }
    }
    v1_38Deps("io.opentelemetry:$it") {
      version {
        strictly("1.38.0-alpha")
      }
    }
    v1_40Deps("io.opentelemetry:$it") {
      version {
        strictly("1.40.0-alpha")
      }
    }
    v1_42Deps("io.opentelemetry:$it") {
      version {
        strictly("1.42.0-alpha")
      }
    }
    v1_47Deps("io.opentelemetry:$it") {
      version {
        strictly("1.47.0-alpha")
      }
    }
    v1_50Deps("io.opentelemetry:$it") {
      version {
        strictly("1.50.0-alpha")
      }
    }
    v1_52Deps("io.opentelemetry:$it") {
      version {
        strictly("1.52.0-alpha")
      }
    }
  }
}

// OpenTelemetry API shaded so that it can be used in instrumentation of OpenTelemetry API itself,
// and then its usage can be unshaded after OpenTelemetry API is shaded
// (see more explanation in opentelemetry-api-1.0.gradle)
tasks {
  withType<ShadowJar>().configureEach {
    relocate("io.opentelemetry", "application.io.opentelemetry")
  }

  shadowJar {
    configurations = listOf(latestDeps)
  }

  val v1_10Shadow by registering(ShadowJar::class) {
    configurations = listOf(v1_10Deps)
    archiveClassifier.set("v1_10")
  }
  val v1_15Shadow by registering(ShadowJar::class) {
    configurations = listOf(v1_15Deps)
    archiveClassifier.set("v1_15")
  }
  val v1_27Shadow by registering(ShadowJar::class) {
    configurations = listOf(v1_27Deps)
    archiveClassifier.set("v1_27")
  }
  val v1_31Shadow by registering(ShadowJar::class) {
    configurations = listOf(v1_31Deps)
    archiveClassifier.set("v1_31")
  }
  val v1_32Shadow by registering(ShadowJar::class) {
    configurations = listOf(v1_32Deps)
    archiveClassifier.set("v1_32")
  }
  val v1_37Shadow by registering(ShadowJar::class) {
    configurations = listOf(v1_37Deps)
    archiveClassifier.set("v1_37")
  }
  val v1_38Shadow by registering(ShadowJar::class) {
    configurations = listOf(v1_38Deps)
    archiveClassifier.set("v1_38")
  }
  val v1_40Shadow by registering(ShadowJar::class) {
    configurations = listOf(v1_40Deps)
    archiveClassifier.set("v1_40")
  }
  val v1_42Shadow by registering(ShadowJar::class) {
    configurations = listOf(v1_42Deps)
    archiveClassifier.set("v1_42")
  }
  val v1_47Shadow by registering(ShadowJar::class) {
    configurations = listOf(v1_47Deps)
    archiveClassifier.set("v1_47")
  }
  val v1_50Shadow by registering(ShadowJar::class) {
    configurations = listOf(v1_50Deps)
    archiveClassifier.set("v1_50")
  }
  val v1_52Shadow by registering(ShadowJar::class) {
    configurations = listOf(v1_52Deps)
    archiveClassifier.set("v1_52")
  }

  artifacts {
    add(v1_10.name, v1_10Shadow)
    add(v1_15.name, v1_15Shadow)
    add(v1_27.name, v1_27Shadow)
    add(v1_31.name, v1_31Shadow)
    add(v1_32.name, v1_32Shadow)
    add(v1_37.name, v1_37Shadow)
    add(v1_38.name, v1_38Shadow)
    add(v1_40.name, v1_40Shadow)
    add(v1_42.name, v1_42Shadow)
    add(v1_47.name, v1_47Shadow)
    add(v1_50.name, v1_50Shadow)
    add(v1_52.name, v1_52Shadow)
  }
}
