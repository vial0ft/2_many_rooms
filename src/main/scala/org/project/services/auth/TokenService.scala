package org.project.services.auth

import com.typesafe.config.Config
import org.project.marshalling.JsonSupport
import org.project.model.User
import pdi.jwt.JwtAlgorithm.HS256
import pdi.jwt.algorithms.JwtHmacAlgorithm
import pdi.jwt.{JwtClaim, JwtSprayJson}
import spray.json._

import java.time.Instant
import scala.util.{Failure, Success, Try}

object TokenService {


  def apply(config: Config): TokenService = {
    val secret = config.getString("app.jwt.secret")
    val expireSec = config.getLong("app.jwt.expire.sec")


    new TokenService(secret, HS256, expireSec)
  }
}

class TokenService(secret: String, algorithm: JwtHmacAlgorithm, expireSec: Long) extends JsonSupport {

  def validate(token: String): Try[String] = {
    for {
      _ <- Try(JwtSprayJson.validate(token, secret, Seq(algorithm)))
      t <- Success(token)
    } yield t
  }

  def createToken(user: User): String = {
    val createdAt = Instant.now()
    val expireAt = createdAt.plusSeconds(expireSec)
    val claim = JwtClaim(
      content = user.toJson.compactPrint,
      expiration = Some(expireAt.getEpochSecond),
      issuedAt = Some(createdAt.getEpochSecond)
    )
    JwtSprayJson.encode(claim, secret, algorithm)
  }

  def getUser(token: String): Try[User] = {
    def tryGetUser(claim: JwtClaim): Try[User] = {
      if (claim.expiration.exists(expireSec => expireSec < Instant.now().getEpochSecond)) {
        Failure(new Exception("token had expired"))
      }
      Success(userFormat.read(claim.content.parseJson))
    }

    for {
      claim <- JwtSprayJson.decode(token, secret, Seq(algorithm))
      user <- tryGetUser(claim)
    } yield user
  }
}
