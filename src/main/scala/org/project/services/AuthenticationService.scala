package org.project.services

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import org.project.model.{AuthUserContext, User}


object AuthenticationService {
  sealed trait AuthenticationResponse

  final case class SuccessAuth(userAuth: AuthUserContext) extends AuthenticationResponse

  final case class FailAuth(reason: String) extends AuthenticationResponse

  sealed trait AuthenticationCommand

  final case class Auth(name: User, replyTo: ActorRef[AuthenticationResponse]) extends AuthenticationCommand

  def apply(tokenService: TokenService)(implicit system: ActorSystem[_]): Behavior[AuthenticationCommand] = Behaviors.receiveMessage {
    case Auth(user, replyTo) =>
      val token = tokenService.createToken(user)
      replyTo ! SuccessAuth(AuthUserContext(user, token))
      Behaviors.same
    case _ => Behaviors.same
  }
}
