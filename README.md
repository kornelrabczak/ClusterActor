import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import com.thecookiezen.containers._

implicit val timeout = Timeout(5 seconds)
val system: ActorSystem = ActorSystem("clusterEngine")
val clusterActor = system.actorOf(Props(classOf[Cluster], "testing_cluster", 10), "clusterActor")

clusterActor ! Cluster.StartCluster

val futureSize = clusterActor ? Cluster.SizeOfCluster()

val futureSize = clusterActor ? DockerHost.ListContainers("test_label")

val futureSize = clusterActor ? Cluster.ListHosts()

val futureSize = clusterActor ? Cluster.AddDockerHost("api version","api url")

