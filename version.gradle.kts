val stableVersion = "2.34.0-SNAPSHOT"
val alphaVersion = "2.34.0-alpha-SNAPSHOT"

val apidiffBaselineVersion = "2.33.1"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
  extra["apidiffBaselineVersion"] = apidiffBaselineVersion
}
