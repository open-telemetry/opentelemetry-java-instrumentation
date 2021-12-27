# Settings for the external annotations instrumentation

| System property 	| Environment variable 	| Type 	| Default 	| Description 	|
|-----------------	|----------------------	|------	|---------	|-------------	|
| `otel.instrumentation.external-annotations.include` | `TRACE_ANNOTATIONS_CONFIG` | String	| Default annotations | Configuration for trace annotations, in the form of a pattern that matches `'package.Annotation$Name;*'`.
| `otel.instrumentation.external-annotations.exclude-methods` | `TRACE_ANNOTATED_METHODS_EXCLUDE_CONFIG` | String | |  All methods to be excluded from auto-instrumentation by annotation-based advices. |