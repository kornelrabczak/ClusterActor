package com.thecookiezen.integration.docker

import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes.Success
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import com.thecookiezen.business.containers.boundary.ClusterEngine
import com.thecookiezen.business.containers.control.Host.Initialized
import com.thecookiezen.integration.docker.DockerClusterEngine.{ContainersListResponse, DockerContainer, getLabelJson}
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol

import scala.concurrent.{ExecutionContext, Future}

trait ContainersListProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val dockerContainerFormat = jsonFormat3(DockerContainer)
  implicit val containersListResponseFormat = jsonFormat1(ContainersListResponse)
}

class DockerClusterEngine(dockerApiVersion: String,
                          dockerDaemonUrl: String,
                          http: HttpRequest => Future[HttpResponse])
                         (implicit ec: ExecutionContext, mat: Materializer)
  extends ClusterEngine with ContainersListProtocol {

  val baseUrl = s"$dockerDaemonUrl/v$dockerApiVersion"

  private val log = LoggerFactory.getLogger(classOf[DockerClusterEngine])

  override def getRunningContainers(label: String): Future[Initialized] = {
    http(Get(Uri(s"""$baseUrl/containers/json?filters=${getLabelJson(label)}"""))).flatMap(response =>
      response.status match {
        case Success(_) => Unmarshal(response.entity).to[ContainersListResponse]
          .map(response => Initialized(response.containers.map(container => container.id)))
        case other => {
          log.error("Wrong status code for fetching containers: {}", other)
          Future.failed(new IllegalStateException("Fetching containers list failed"))
        }
      }
    )
  }
}

object DockerClusterEngine {
  def getLabelJson(label: String): String = {
    s"""{"label":["cluster=$label"]}"""
  }

  case class ContainersListResponse(containers: Seq[DockerContainer])
  case class DockerContainer(id: String, names: Seq[String], image: String)
}

