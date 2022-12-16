/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package listener

import org.springframework.context.annotation.ComponentScan
import org.springframework.jms.annotation.EnableJms

@ComponentScan
@EnableJms
class AnnotatedListenerConfig extends AbstractConfig {
}
