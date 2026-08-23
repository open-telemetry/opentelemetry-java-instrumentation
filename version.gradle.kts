val stableVersion = "2.32.0-SNAPSHOT"
val alphaVersion = "2.32.0-alpha-SNAPSHOT"

val apidiffBaselineVersion = "2.31.0"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
  extra["apidiffBaselineVersion"] = apidiffBaselineVersion
}
