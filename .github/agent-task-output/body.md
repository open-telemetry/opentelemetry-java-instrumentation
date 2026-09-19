Expand the package-name checker to cover javaagent source sets in addition to libraries. Enforce package prefixes derived from module names, including version segments and special handling for Java/JDK instrumentation, while retaining explicit exceptions for packages that intentionally cannot follow the convention.

Document the remaining exceptions, completed cleanup work, and follow-up candidates in a package-name cleanup plan.
