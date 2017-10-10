package com.thecookiezen.integration.docker

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Materializer
import com.thecookiezen.business.containers.control.Host

import scala.concurrent.{ExecutionContext, Future}

class DockerHost(dockerApiVersion: String, dockerDaemonUrl: String, http: (HttpRequest) => Future[HttpResponse])
                (implicit ec: ExecutionContext, mat: Materializer)
  extends DockerClusterEngine(dockerApiVersion, dockerDaemonUrl, http)
    with Host
