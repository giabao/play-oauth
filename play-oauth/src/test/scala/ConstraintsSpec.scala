import fr.njin.playoauth.as.endpoints.Constraints._
import org.specs2.mutable.Specification
import org.specs2.specification.core.Fragment
import play.api.data.validation.{Valid, Invalid}

/**
 * User: bathily
 * Date: 19/09/13
 */
class ConstraintsSpec extends Specification {


  "URI Constraint should" ^ {

    Seq("http://www.google.com",
      "https://www.google.com",
      "file:///dir/file",
      "http://localhost:9000/"
    ).map(valid)

    Seq("www.google.com",
      "/search",
      "localhost",
      "localhost:9000",
      "file:/dir/file"
    ).map(invalid)
  }

  def valid(url:String): Fragment = {
    s"Validate `$url`" in {
      uri(url) must beAnInstanceOf[Valid.type]
    }
  }

  def invalid(url:String): Fragment = {
    s"Invalidate `$url`" in {
      uri(url) must beAnInstanceOf[Invalid]
    }
  }
}
