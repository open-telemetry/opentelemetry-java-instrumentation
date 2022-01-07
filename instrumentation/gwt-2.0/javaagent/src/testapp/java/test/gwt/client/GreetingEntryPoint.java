/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.gwt.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import test.gwt.shared.MessageService;
import test.gwt.shared.MessageServiceAsync;

public class GreetingEntryPoint implements EntryPoint {
  private final MessageServiceAsync messageServiceAsync = GWT.create(MessageService.class);

  @Override
  // GWT compiler still expects pre-Java 8 semantics
  @SuppressWarnings("UnnecessaryFinal")
  public void onModuleLoad() {
    Button greetingButton = new Button("Greeting");
    greetingButton.addStyleName("greeting.button");

    Button errorButton = new Button("Error");
    errorButton.addStyleName("error.button");

    RootPanel.get("buttonContainer").add(greetingButton);
    RootPanel.get("buttonContainer").add(errorButton);

    final Label messageLabel = new Label();
    RootPanel.get("messageContainer").add(messageLabel);

    class MyHandler implements ClickHandler {
      private final String message;

      MyHandler(String message) {
        this.message = message;
      }

      @Override
      public void onClick(ClickEvent event) {
        sendMessageToServer();
      }

      private void sendMessageToServer() {
        messageLabel.setText("");
        messageLabel.setStyleName("");

        messageServiceAsync.sendMessage(
            message,
            new AsyncCallback<String>() {
              @Override
              public void onFailure(Throwable caught) {
                messageLabel.setText("Error");
                messageLabel.addStyleName("error.received");
              }

              @Override
              public void onSuccess(String result) {
                messageLabel.setText(result);
                messageLabel.addStyleName("message.received");
              }
            });
      }
    }

    greetingButton.addClickHandler(new MyHandler("Otel"));
    errorButton.addClickHandler(new MyHandler("Error"));
  }
}
