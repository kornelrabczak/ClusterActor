package com.thecookiezen.business.containers.boundary

import com.thecookiezen.business.containers.control.Host.Initialized

import scala.concurrent.Future

trait ClusterEngine {
  def getRunningContainers(label: String): Future[Initialized]
}
