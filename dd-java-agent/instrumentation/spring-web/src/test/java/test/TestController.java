package test;

import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class TestController {

  @GetMapping("/")
  public @ResponseBody String greeting() {
    return "Hello World";
  }

  @GetMapping("/param/{parameter}/")
  public ResponseEntity<String> withParam(@PathVariable("parameter") final String param) {
    if (param.equals("missing")) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok("Hello " + param);
  }

  @PostMapping("/validated")
  public @ResponseBody String withValidation(@Valid @RequestBody final TestForm form) {
    return "Hello " + form.getName() + " " + form;
  }

  @GetMapping("/error/{parameter}/")
  public @ResponseBody String withError(@PathVariable("parameter") final String param) {
    throw new RuntimeException(param);
  }
}
