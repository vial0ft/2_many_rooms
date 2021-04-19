package org.project.redis

import com.redis.RedisClient
import com.typesafe.config.Config
import org.project.services.RoomService.Rooms
import org.project.model.Room

import scala.language.implicitConversions

object RedisRoomService {

  private def _listStr2Rooms(x: List[String]): Rooms = Rooms(x.toSet.map(n => Room(n)))

  def apply(config: Config) : RedisRoomService = {
    val host = config.getString("redis.rooms.host") // Gets the host and a port from the configuration
    val port = config.getInt("redis.rooms.port")
    val client = new RedisClient(host, port)
    client.flushdb
    new RedisRoomService(client)
  }
}


class RedisRoomService(_r: RedisClient) {
  import org.project.redis.RedisRoomService._listStr2Rooms
  def getRooms(): Rooms = {
    _r.keys()
      .map(k => k.flatten)
      .map(l => _listStr2Rooms(l))
      .getOrElse(Rooms())
  }

  def createRoom(room: Room):Option[Room] = {
    if(_r.exists(room.name)) return None
    _r.set(room.name,0)
    Some(room)
  }

  def addUserToRoom(room: Room): Option[Long]  = {
    _r.incr(room.name)
  }

  def leaveUserRoom(room: Room): Option[Long]  = {
    _r.decr(room.name)
  }

  def closeRoom(room: Room): Option[Long] = {
    _r.del(room.name)
  }
}
