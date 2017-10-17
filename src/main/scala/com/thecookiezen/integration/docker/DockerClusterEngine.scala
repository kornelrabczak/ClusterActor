package com.thecookiezen.integration.docker

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes.Success
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.{Framing, Source}
import akka.util.ByteString
import com.thecookiezen.business.containers.boundary.ClusterEngine
import com.thecookiezen.business.containers.control.Host.Initialized
import com.thecookiezen.integration.docker.DockerClusterEngine.{DockerContainer, getLabelJson}
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol

import scala.concurrent.{ExecutionContext, Future}

trait ContainersListProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val dockerContainerFormat = jsonFormat(DockerContainer, "Id", "Names", "Image")
}

class DockerClusterEngine(dockerApiVersion: String,
                          dockerDaemonUrl: String,
                          http: HttpRequest => Future[HttpResponse])
                         (implicit ec: ExecutionContext, mat: Materializer)
  extends ClusterEngine with ContainersListProtocol {

  val baseUrl = s"http://$dockerDaemonUrl/v$dockerApiVersion"

  private val log = LoggerFactory.getLogger(classOf[DockerClusterEngine])

  override def getRunningContainers(label: String): Future[Initialized] = {
    http(Get(Uri(s"""$baseUrl/containers/json?filters=${getLabelJson(label)}"""))).flatMap(response =>
      response.status match {
        case Success(_) =>
          Unmarshal(response.entity).to[Array[DockerContainer]]
            .map(containers => Initialized(containers.map(container => container.id)))
        case other => {
          log.error("Wrong status code for fetching containers: {}", other)
          Future.failed(new IllegalStateException("Fetching containers list failed"))
        }
      }
    )
  }

  override def log(containerId: String)(implicit system: ActorSystem): Future[Source[ByteString, NotUsed]] = {
    val query = Query(("follow", "true"), ("stdout", "true"), ("timestamps", "true"))
    http(Get(Uri(s"$baseUrl/containers/$containerId/logs").withQuery(query))).map(response => {
      response.entity.dataBytes
        .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256))
        .mapMaterializedValue(_ => NotUsed)
    })
  }
}

object DockerClusterEngine {
  def getLabelJson(label: String): String = {
    s"""{"label":["cluster=$label"]}"""
  }

  case class DockerContainer(id: String, names: Seq[String], image: String)

}

