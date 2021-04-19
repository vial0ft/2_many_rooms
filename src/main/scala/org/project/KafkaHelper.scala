package org.project

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.DateTime
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import akka.stream.scaladsl.{BroadcastHub, Sink, Source}
import com.typesafe.config.Config
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.project.model.{Message, Room}
import spray.json.{CompactPrinter, RootJsonFormat}

object KafkaHelper {

   // Gets the host and a port from the configuration

  def apply(config: Config)(implicit system: ActorSystem[_]): KafkaHelper = {
    val bootstrapServer = config.getString("kafka.bootstrap.servers")

    val consumerSettings: ConsumerSettings[String, String] = ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
      .withBootstrapServers(bootstrapServer)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      .withProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "5000")
      .withGroupId("2-many-rooms-app")

    val producerSettings: ProducerSettings[String, String] = ProducerSettings(system, new StringSerializer, new StringSerializer)
      .withBootstrapServers(bootstrapServer)

    val kafkaProducer = producerSettings.createKafkaProducer()
    val settingsWithProducer = producerSettings.withProducer(kafkaProducer)

    new KafkaHelper(consumerSettings, settingsWithProducer)
  }

}


class KafkaHelper(consumerSettings: ConsumerSettings[String, String],
                  settingsWithProducer: ProducerSettings[String, String])
                 (implicit system: ActorSystem[_]) {

  import spray.json.DefaultJsonProtocol._

  implicit val msgFormat: RootJsonFormat[Message] = jsonFormat2(Message)

  def pushMsg(room: Room, msg: Message) = {
    Source.single(msg)
      .map(msg => CompactPrinter(msgFormat.write(msg)))
      .map(value => new ProducerRecord[String, String](room.name, DateTime.now.toIsoDateTimeString(), value))
      .runWith(Producer.plainSink(settingsWithProducer))
  }

  def getTopicSource(room: Room): Source[String, Consumer.Control] = {
    Consumer.plainSource(consumerSettings, Subscriptions.topics(room.name))
      .map(consumerRecord => consumerRecord.value())
  }
}


