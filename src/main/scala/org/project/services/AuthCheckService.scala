package org.project.services

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import org.project.model.AuthUserContext

import scala.util.{Failure, Success, Try}

object AuthCheckService {

  sealed trait AuthResponse

  final case class SuccessAuthCheck(userCtx: AuthUserContext) extends AuthResponse

  final case class FailAuth(reason: String) extends AuthResponse

  sealed trait AuthCommand

  final case class Auth(token: String, replyTo: ActorRef[AuthResponse]) extends AuthCommand

  def apply(tokenService: TokenService)(implicit system: ActorSystem[_]): Behavior[AuthCommand] = Behaviors.receiveMessage {
    case Auth(token, replyTo) =>
      val response = Try(tokenService.validate(token))
        .flatMap(validToken => tokenService.getUser(validToken)) match {
        case Success(user) => SuccessAuthCheck(AuthUserContext(user, token))
        case Failure(exception) => {
          system.log.error("error Auth", exception)
          FailAuth(exception.getMessage)
        }
      }
      replyTo ! response
      Behaviors.same
    case _ => Behaviors.same
  }
}
