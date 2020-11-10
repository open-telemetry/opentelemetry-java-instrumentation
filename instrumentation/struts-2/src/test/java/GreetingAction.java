import com.opensymphony.xwork2.ActionSupport;

public class GreetingAction extends ActionSupport {

  @Override
  public String execute() {
    return "greeting";
  }
}
