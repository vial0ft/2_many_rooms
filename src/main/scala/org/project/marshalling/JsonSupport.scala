package org.project.marshalling

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.project.model.{AuthUserContext, MessageBody, Room, User}
import org.project.services.rooms.RoomService.{Failed, Rooms, Status, Successful}
import spray.json.{DeserializationException, JsString, JsValue, RootJsonFormat}

trait JsonSupport extends SprayJsonSupport {
  // import the default encoders for primitive types (Int, String, Lists etc)

  import spray.json.DefaultJsonProtocol._

  implicit object StatusFormat extends RootJsonFormat[Status] {
    def write(status: Status): JsValue = status match {
      case Failed => JsString("Failed")
      case Successful => JsString("Successful")
    }

    def read(json: JsValue): Status = json match {
      case JsString("Failed") => Failed
      case JsString("Successful") => Successful
      case _ => throw new DeserializationException("Status unexpected")
    }
  }

  implicit val userFormat: RootJsonFormat[User] = jsonFormat1(User)
  implicit val usersSetFormat: RootJsonFormat[collection.Set[User]] = setFormat(userFormat)
  implicit val msgFormat: RootJsonFormat[MessageBody] = jsonFormat1(MessageBody)
  implicit val userCtxFormat: RootJsonFormat[AuthUserContext] = jsonFormat2(AuthUserContext)
  implicit val roomFormat: RootJsonFormat[Room] = jsonFormat1(Room)
  implicit val roomsFormat: RootJsonFormat[Rooms] = jsonFormat1(Rooms)
}