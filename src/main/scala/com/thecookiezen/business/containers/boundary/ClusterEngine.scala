package com.thecookiezen.business.containers.boundary

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.thecookiezen.business.containers.control.Host.Initialized

import scala.concurrent.Future

trait ClusterEngine {
  def getRunningContainers(label: String): Future[Initialized]
  def log(containerId: String)(implicit system: ActorSystem): Future[Source[ByteString, NotUsed]]
}
