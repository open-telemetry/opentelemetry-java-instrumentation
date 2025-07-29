# Produced Metrics


## Metric `system.memory.utilization`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `system.memory.utilization` | Gauge | `1` | System memory utilization | ![Development](https://img.shields.io/badge/-development-blue) |


### `system.memory.utilization` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `state` | string | The type of memory being measured. | `used`; `free`; `cached`; `buffered` | `Required` | ![Development](https://img.shields.io/badge/-development-blue) |



## Metric `system.memory.usage`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `system.memory.usage` | UpDownCounter | `By` | System memory usage | ![Development](https://img.shields.io/badge/-development-blue) |


### `system.memory.usage` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `state` | string | The type of memory being measured. | `used`; `free`; `cached`; `buffered` | `Required` | ![Development](https://img.shields.io/badge/-development-blue) |



## Metric `system.network.io`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `system.network.io` | Counter | `By` | System network IO | ![Development](https://img.shields.io/badge/-development-blue) |


### `system.network.io` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `device` | string | The name of the network device. | `eth0` | `Required` | ![Development](https://img.shields.io/badge/-development-blue) |
| `direction` | string | The direction of the flow of data being measured. | `receive`; `transmit`; `read`; `write` | `Required` | ![Development](https://img.shields.io/badge/-development-blue) |



## Metric `system.network.packets`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `system.network.packets` | Counter | `{packets}` | System network packets | ![Development](https://img.shields.io/badge/-development-blue) |


### `system.network.packets` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `device` | string | The name of the network device. | `eth0` | `Required` | ![Development](https://img.shields.io/badge/-development-blue) |
| `direction` | string | The direction of the flow of data being measured. | `receive`; `transmit`; `read`; `write` | `Required` | ![Development](https://img.shields.io/badge/-development-blue) |



## Metric `system.network.errors`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `system.network.errors` | Counter | `{errors}` | System network errors | ![Development](https://img.shields.io/badge/-development-blue) |


### `system.network.errors` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `device` | string | The name of the network device. | `eth0` | `Required` | ![Development](https://img.shields.io/badge/-development-blue) |
| `direction` | string | The direction of the flow of data being measured. | `receive`; `transmit`; `read`; `write` | `Required` | ![Development](https://img.shields.io/badge/-development-blue) |



## Metric `system.disk.io`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `system.disk.io` | Counter | `By` | System disk IO | ![Development](https://img.shields.io/badge/-development-blue) |


### `system.disk.io` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `device` | string | The name of the network device. | `eth0` | `Required` | ![Development](https://img.shields.io/badge/-development-blue) |
| `direction` | string | The direction of the flow of data being measured. | `receive`; `transmit`; `read`; `write` | `Required` | ![Development](https://img.shields.io/badge/-development-blue) |



## Metric `system.disk.operations`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `system.disk.operations` | Counter | `{operations}` | System disk operations | ![Development](https://img.shields.io/badge/-development-blue) |


### `system.disk.operations` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `device` | string | The name of the network device. | `eth0` | `Required` | ![Development](https://img.shields.io/badge/-development-blue) |
| `direction` | string | The direction of the flow of data being measured. | `receive`; `transmit`; `read`; `write` | `Required` | ![Development](https://img.shields.io/badge/-development-blue) |



## Metric `runtime.java.memory`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `runtime.java.memory` | UpDownCounter | `By` | Runtime Java memory | ![Development](https://img.shields.io/badge/-development-blue) |


### `runtime.java.memory` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `type` | string | The type of memory measurement | `rss`; `vms` | `Recommended` | ![Development](https://img.shields.io/badge/-development-blue) |



## Metric `runtime.java.cpu_time`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `runtime.java.cpu_time` | Gauge | `ms` | Runtime Java CPU time | ![Development](https://img.shields.io/badge/-development-blue) |


### `runtime.java.cpu_time` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `type` | string | The type of CPU time measurement | `user`; `system` | `Recommended` | ![Development](https://img.shields.io/badge/-development-blue) |
