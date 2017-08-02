package com.datadoghq.examples.resources;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class HomeResource {

  @RequestMapping(method = RequestMethod.GET)
  public String test() {

    final StringBuilder template = new StringBuilder();

    template.append("Demo links");
    template.append("<ul>");
    template.append("<li><a href=\"/user/add?name=name&email=f@example.com\">Add a user</a></li>");
    template.append("<li><a href=\"/user/all\">List all users</a></li>");
    template.append("<li><a href=\"/user/get?id=1\">Get user with id=1</a></li>");
    template.append("</ul>");

    return template.toString();
  }
}
