package org.project.services.msgs

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.stream.scaladsl.Source
import org.project.model.{Room, User}

object MsgSubscribeService {
  final case class SubscribeBy(user: User, room: Room, replyTo: ActorRef[Subscribed])
  final case class Subscribed(source: Source[String, _])

  def apply(kafka: KafkaHelper): Behavior[SubscribeBy] =
    Behaviors.receive {(ctx, msg) =>
      msg match {
        case SubscribeBy(user, room,replyTo) => {
          ctx.log.info(s"New subscriber $user for $room")

          replyTo ! Subscribed(kafka.getTopicSource(user,room))
          Behaviors.same
        }
        case _ => Behaviors.ignore
      }
  }
}
