tasks {
  val resolveLatestDepVersions by registering {
    group = "Help"
    description = "Resolve latest dependency versions and write to .github/config/latest-dep-versions.json"

    doLast {
      if (gradle.startParameter.projectProperties["testLatestDeps"] != "true" ||
        gradle.startParameter.projectProperties["resolveLatestDeps"] != "true") {
        throw GradleException("Must run with -PtestLatestDeps=true -PresolveLatestDeps=true")
      }

      val versions = sortedMapOf<String, String>()

      fun isHigherVersion(v1: String, v2: String): Boolean {
        val parts1 = v1.split(Regex("[.\\-]"))
        val parts2 = v2.split(Regex("[.\\-]"))
        for (i in 0 until maxOf(parts1.size, parts2.size)) {
          val n1 = parts1.getOrElse(i) { "0" }.toIntOrNull() ?: 0
          val n2 = parts2.getOrElse(i) { "0" }.toIntOrNull() ?: 0
          if (n1 != n2) return n1 > n2
        }
        return false
      }

      fun isPreRelease(version: String): Boolean {
        return version.contains("-alpha", true)
          || version.contains("-beta", true)
          || version.contains("-rc", true)
          || version.contains(".rc", true)
          || version.contains("-m", true)
          || version.contains(".m", true)
          || version.contains(".alpha", true)
          || version.contains(".beta", true)
          || version.contains(".cr", true)
      }

      fun recordVersion(key: String, version: String) {
        if (isPreRelease(version)) {
          logger.info("Skipping pre-release version $key:$version")
          return
        }
        val existing = versions[key]
        if (existing == null || isHigherVersion(version, existing)) {
          versions[key] = version
        }
      }

      subprojects {
        configurations
          .filter { it.isCanBeResolved && it.name.contains("test", ignoreCase = true) && it.name.endsWith("RuntimeClasspath") }
          .forEach { config ->
            try {
              config.incoming.resolutionResult.allDependencies.forEach { dep ->
                if (dep is org.gradle.api.artifacts.result.ResolvedDependencyResult) {
                  val requested = dep.requested
                  if (requested is org.gradle.api.artifacts.component.ModuleComponentSelector) {
                    val reqVersion = requested.version
                    val selectedVersion = dep.selected.moduleVersion?.version ?: return@forEach
                    if (reqVersion == "latest.release") {
                      recordVersion("${requested.group}:${requested.module}", selectedVersion)
                    } else if (reqVersion.contains("+")) {
                      recordVersion("${requested.group}:${requested.module}#$reqVersion", selectedVersion)
                    }
                  }
                }
              }
            } catch (e: Exception) {
              logger.warn("Failed to resolve ${this.path}:${config.name}: ${e.message}")
            }
          }
      }

      // Resolve Spring Boot catalog versions using a detached configuration
      listOf("3.+", "4.+").forEach { range ->
        val detached = project.configurations.detachedConfiguration(
          project.dependencies.create("org.springframework.boot:spring-boot-dependencies:$range")
        )
        detached.resolutionStrategy.componentSelection.all {
          val v = candidate.version
          if (v.contains("-alpha", true) || v.contains("-beta", true) ||
            v.contains("-rc", true) || v.contains(".rc", true) ||
            v.contains("-m", true) || v.contains(".m", true) ||
            v.contains(".alpha", true) || v.contains(".beta", true) ||
            v.contains(".cr", true)
          ) {
            reject("pre-release version")
          }
        }
        try {
          val resolvedVersion = detached.incoming.resolutionResult.rootComponent.get()
            .dependencies
            .filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>()
            .first()
            .selected.moduleVersion?.version
          if (resolvedVersion != null) {
            versions["org.springframework.boot:spring-boot-dependencies#$range"] = resolvedVersion
          }
        } catch (e: Exception) {
          logger.warn("Failed to resolve Spring Boot $range: ${e.message}")
        }
      }

      val outputFile = file(".github/config/latest-dep-versions.json")
      outputFile.parentFile.mkdirs()
      outputFile.printWriter().use { writer ->
        writer.println("{")
        val entries = versions.entries.toList()
        entries.forEachIndexed { index, (key, value) ->
          val comma = if (index < entries.size - 1) "," else ""
          writer.println("  \"$key\": \"$value\"$comma")
        }
        writer.println("}")
      }

      logger.lifecycle("Wrote ${versions.size} pinned versions to ${outputFile.path}")
    }
  }
}
