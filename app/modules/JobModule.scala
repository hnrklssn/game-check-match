package modules

//import jobs.{ AuthTokenCleaner, Scheduler }
import com.google.inject.Provides
import jobs.{ MyPrioQueueSemantics, UniquePriorityMessageQueue }
import net.codingwell.scalaguice.ScalaModule
import play.api.libs.concurrent.AkkaGuiceSupport

/**
 * The job module.
 */
class JobModule extends ScalaModule with AkkaGuiceSupport {

  /**
   * Configures the module.
   */
  def configure() = {
    //bindActor[AuthTokenCleaner]("auth-token-cleaner")
    //bind[Scheduler].asEagerSingleton()
    bind[MyPrioQueueSemantics].to[UniquePriorityMessageQueue]
  }

}
