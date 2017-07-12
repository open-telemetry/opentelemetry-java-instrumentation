package com.datadoghq.examples.resources;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class HelloResource {

  @RequestMapping(method = RequestMethod.GET)
  public String test() {

    return "Hello world!";
  }
}
