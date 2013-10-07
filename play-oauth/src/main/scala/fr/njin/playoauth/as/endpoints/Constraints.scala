package fr.njin.playoauth.as.endpoints

import play.api.data.validation.{ValidationError, Invalid, Valid, Constraint}
import java.net.{URISyntaxException, URI}
import org.apache.commons.validator.routines.UrlValidator

/**
 * User: bathily
 * Date: 19/09/13
 */
object Constraints {

  def equalTo(value: String): Constraint[String] = Constraint[String]("constraint.equal", value){ o:String =>
    if(value == o) Valid else Invalid(ValidationError("error.equal", o, value))
  }

  def uri: Constraint[String] = Constraint[String]("constraint.uri"){ uri =>
    val invalid = Invalid(ValidationError("error.uri", uri))
    if(new UrlValidator(UrlValidator.ALLOW_ALL_SCHEMES + UrlValidator.ALLOW_LOCAL_URLS).isValid(uri)) Valid else invalid
  }

  def uris: Constraint[Seq[String]] = Constraint[Seq[String]]("constraint.uris"){ uris =>
    uris.filterNot(uri => new UrlValidator(UrlValidator.ALLOW_ALL_SCHEMES + UrlValidator.ALLOW_LOCAL_URLS).isValid(uri)) match {
      case Seq() => Valid
      case invalids => Invalid("error.uris", invalids.length, invalids.mkString(","))
    }
  }
}
