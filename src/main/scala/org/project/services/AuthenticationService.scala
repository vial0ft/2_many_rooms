package org.project.services

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import org.project.redis.RedisAuthService


object AuthenticationService {
  sealed trait AuthenticationResponse

  final case class SuccessAuth(token: String) extends AuthenticationResponse
  final case class FailAuth(reason: String) extends AuthenticationResponse

  sealed trait AuthenticationCommand
  final case class Auth(name: String, replyTo: ActorRef[AuthenticationResponse]) extends AuthenticationCommand

  def apply(redisRoomService: RedisAuthService)(implicit system: ActorSystem[_]): Behavior[AuthenticationCommand] = Behaviors.receiveMessage {
    case Auth(user, replyTo) =>
      replyTo ! SuccessAuth(user)
      Behaviors.same
    case _ => Behaviors.same
  }
}
