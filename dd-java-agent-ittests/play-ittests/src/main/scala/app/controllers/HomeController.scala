package controllers

import javax.inject.Inject

import play.api.mvc._

/**
  * This controller creates an `Action` to handle HTTP requests to the
  * application's welcome greeting
  */
class HomeController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  /**
    * Create an Action to return a greeting
    */
  def doGet(id: Option[Int]) = Action { implicit request: Request[AnyContent] =>
    val idVal = id.getOrElse(-1)
    if (idVal > 0) {
      Ok("Welcome %d.".format(idVal))
    } else {
      Ok("No ID.")
    }
  }
}
