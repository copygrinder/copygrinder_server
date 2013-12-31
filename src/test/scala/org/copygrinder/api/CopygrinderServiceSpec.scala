package org.copygrinder.api

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.http.StatusCodes._

@RunWith(classOf[JUnitRunner])
class CopygrinderServiceSpec extends Specification with Specs2RouteTest with CopygrinderService {
  def actorRefFactory = system
  
  "CopygrinderService" should {

    "return a greeting for GET requests to the root path" in {
      Get() ~> myRoute ~> check {
        entityAs[String] must contain("Hello")
      }
    }

    "leave GET requests to other paths unhandled" in {
      Get("/kermit") ~> myRoute ~> check {
        handled must beFalse
      }
    }

    "return a MethodNotAllowed error for PUT requests to the root path" in {
      Put() ~> sealRoute(myRoute) ~> check {
        status === MethodNotAllowed
        entityAs[String] === "HTTP method not allowed, supported methods: GET"
      }
    }
  }
}
