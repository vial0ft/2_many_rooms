package org.project.services.msgs

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.stream.scaladsl.Source
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout

import scala.concurrent.duration._
import akka.util.Timeout._
import org.project.model.{Message, Room, User}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object MessageService {

  import scala.concurrent.ExecutionContext.Implicits.global
  import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}

  implicit val timeout: Timeout = 3.seconds

  sealed trait MessageResponse

  final case class TopicSource(source: Source[String, _]) extends MessageResponse

  final case class CantSubscribe(reason: Throwable) extends MessageResponse

  case object Sent extends MessageResponse

  final case class Fail(reason: String) extends MessageResponse

  sealed trait MessageCommand

  final case class SendMessage(room: Room, msg: Message, replyTo: ActorRef[MessageResponse]) extends MessageCommand

  final case class Subscribe(user: User, room: Room, replyTo: ActorRef[MessageResponse]) extends MessageCommand

  def apply(kafka: KafkaHelper)(implicit system: ActorSystem[_]): Behavior[MessageCommand] =
    Behaviors.receive { (ctx, msg) =>

      msg match {
        case SendMessage(room, msg, replyTo) =>
          kafka.pushMsg(room, msg)
            .onComplete {
              case Success(value) => replyTo ! Sent
              case Failure(exception) => replyTo ! Fail(exception.getMessage)
            }
          Behaviors.same
        case Subscribe(user, room, replyTo) =>
          val act: String = s"${room.name}_${user.name}"
          ctx.log.debug("act:{}", act)
          val subActor: ActorRef[MsgSubscribeService.SubscribeBy] = ctx.spawn(MsgSubscribeService(kafka), act)
          val sub: Future[MsgSubscribeService.Subscribed] = subActor.ask(MsgSubscribeService.SubscribeBy(user, room, _))

          sub.onComplete {
            case Success(value) => replyTo ! TopicSource(value.source)
            case Failure(exception) => replyTo ! CantSubscribe(exception)
          }

          Behaviors.same
      }
    }
}
