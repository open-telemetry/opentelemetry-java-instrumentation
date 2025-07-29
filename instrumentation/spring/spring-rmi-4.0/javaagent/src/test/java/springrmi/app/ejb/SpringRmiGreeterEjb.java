/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springrmi.app.ejb;

import javax.ejb.Stateless;
import springrmi.app.SpringRmiGreeterImpl;

@Stateless
public class SpringRmiGreeterEjb extends SpringRmiGreeterImpl
    implements SpringRmiGreeterEjbRemote {}
