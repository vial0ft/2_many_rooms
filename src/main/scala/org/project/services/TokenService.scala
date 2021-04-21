package org.project.services

import com.typesafe.config.Config
import org.project.marshalling.JsonSupport
import org.project.model.User
import pdi.jwt.JwtAlgorithm.HS256
import pdi.jwt.algorithms.JwtHmacAlgorithm
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtSprayJson}
import spray.json._

import java.time.Instant

object TokenService {


  def apply(config: Config): TokenService = {
    val secret = config.getString("app.jwt.secret")
    val expireSec = config.getLong("app.jwt.expire.sec")


    new TokenService(secret, HS256, expireSec)
  }

  // TODO: Base64 coded token

}

class TokenService(secret: String, algorithm: JwtHmacAlgorithm, expireSec: Long) extends JsonSupport {


  def validate(token: String) = {
    JwtSprayJson.validate(token,secret,Seq(algorithm))
    token
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

  def getUser(token: String) = {

    JwtSprayJson.decode(token,secret,Seq(algorithm))
      .map(claim => {
        if (claim.expiration.exists(expireSec => expireSec < Instant.now().getEpochSecond)) {
          throw new Exception("token had expired")
        }
        userFormat.read(claim.content.parseJson)
      })
  }
}
