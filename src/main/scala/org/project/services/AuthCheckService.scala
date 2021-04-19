package org.project.services

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import org.project.model.{AuthUserContext, User}
import org.project.redis.RedisAuthService

object AuthCheckService {

  sealed trait AuthResponse

  final case class SuccessAuthCheck(userCtx: AuthUserContext) extends AuthResponse
  final case class FailAuth(reason: String) extends AuthResponse

  sealed trait AuthCommand
  final case class Auth(token: String, replyTo: ActorRef[AuthResponse]) extends AuthCommand

  def apply(redisAuthService: RedisAuthService)(implicit system: ActorSystem[_]): Behavior[AuthCommand] = Behaviors.receiveMessage {
    case Auth(token, replyTo) =>
      val response = redisAuthService.getUser(token) match {
        case Some(user) => SuccessAuthCheck(AuthUserContext(user.name, token))
        case None => FailAuth("Not Authorize")
      }
      replyTo ! response
        Behaviors.same
    case _ => Behaviors.same
  }
}
