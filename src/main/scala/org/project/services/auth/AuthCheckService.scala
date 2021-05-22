package org.project.services.auth

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.project.model.{AuthUserContext, User}

import scala.util.{Failure, Success, Try}

object AuthCheckService {

  sealed trait AuthResponse

  final case class SuccessAuthCheck(userCtx: AuthUserContext) extends AuthResponse

  final case class FailAuth(reason: String) extends AuthResponse

  sealed trait AuthCommand

  final case class Auth(token: String, replyTo: ActorRef[AuthResponse]) extends AuthCommand

  def apply(tokenService: TokenService): Behavior[AuthCommand] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Auth(token, replyTo) =>
          val maybeUser: Try[User] =
            for {
              validToken <- tokenService.validate(token)
              user <- tokenService.getUser(validToken)
            } yield user

          val response =
            maybeUser match {
              case Success(user) => SuccessAuthCheck(AuthUserContext(user, token))
              case Failure(exception) =>
                ctx.log.error("error Auth:", exception)
                FailAuth(exception.getMessage)
            }
          replyTo ! response
          Behaviors.same
        case _ => Behaviors.ignore
      }
    }
}
