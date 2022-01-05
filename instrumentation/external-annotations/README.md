# Settings for the external annotations instrumentation

| System property 	| Type 	| Default 	| Description 	|
|-----------------	|------	|---------	|-------------	|
| `otel.instrumentation.external-annotations.include` | String	| Default annotations | Configuration for trace annotations, in the form of a pattern that matches `'package.Annotation$Name;*'`.
| `otel.instrumentation.external-annotations.exclude-methods` | String |  | All methods to be excluded from auto-instrumentation by annotation-based advices. |