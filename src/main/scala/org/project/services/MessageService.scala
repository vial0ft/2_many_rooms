package org.project.services

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.stream.scaladsl.Source
import org.project.KafkaHelper
import org.project.model.{Message, Room}

import scala.util.{Failure, Success}

object MessageService {

  import scala.concurrent.ExecutionContext.Implicits.global

  sealed trait MessageResponse

  final case class TopicSource(source: Source[String,_]) extends MessageResponse

  case object Sent extends MessageResponse

  final case class Fail(reason: String) extends MessageResponse

  sealed trait MessageCommand

  final case class SendMessage(room: Room, msg: Message, replyTo: ActorRef[MessageResponse]) extends MessageCommand

  final case class Subscribe(room: Room, replyTo: ActorRef[MessageResponse]) extends MessageCommand

  def apply(kafka: KafkaHelper)(implicit system: ActorSystem[_]): Behavior[MessageCommand] = Behaviors.receiveMessage {

    case SendMessage(room, msg, replyTo) =>
      kafka.pushMsg(room, msg)
        .onComplete {
          case Success(value) => replyTo ! Sent
          case Failure(exception) => replyTo ! Fail(exception.getMessage)
        }
      Behaviors.same
    case Subscribe(room, replyTo) => {
      replyTo ! TopicSource(kafka.getTopicSource(room))
      Behaviors.same
    }
  }
}
