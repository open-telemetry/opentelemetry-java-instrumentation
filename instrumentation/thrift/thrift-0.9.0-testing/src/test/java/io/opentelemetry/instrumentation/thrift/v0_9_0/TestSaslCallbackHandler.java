/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_9_0;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.RealmCallback;

public class TestSaslCallbackHandler implements CallbackHandler {
  public static final String PRINCIPAL = "thrift-test-principal";
  public static final String REALM = "thrift-test-realm";
  private final String password;

  public TestSaslCallbackHandler(String password) {
    this.password = password;
  }

  @Override
  public void handle(Callback[] callbacks) throws UnsupportedCallbackException {
    Callback[] var2 = callbacks;
    int var3 = callbacks.length;

    for (int var4 = 0; var4 < var3; ++var4) {
      Callback c = var2[var4];
      if (c instanceof NameCallback) {
        ((NameCallback) c).setName("thrift-test-principal");
      } else if (c instanceof PasswordCallback) {
        ((PasswordCallback) c).setPassword(this.password.toCharArray());
      } else if (c instanceof AuthorizeCallback) {
        ((AuthorizeCallback) c).setAuthorized(true);
      } else {
        if (!(c instanceof RealmCallback)) {
          throw new UnsupportedCallbackException(c);
        }

        ((RealmCallback) c).setText("thrift-test-realm");
      }
    }
  }
}
