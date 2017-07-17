package models.daos

import akka.NotUsed
import akka.stream.scaladsl.Source
import models.ServiceProfile

/**
 * Created by henrik on 2017-02-26.
 */
//class ServiceUserDao {

/*@Provides
  def allUserSummariesProvider(@Inject steamUserSummaries: Source[Seq[SteamProfile], NotUsed]): Source[Seq[ServiceProfile], NotUsed] = {
    steamUserSummaries.buffer(1, OverflowStrategy.dropTail) //if several services in future, merge streams here
  }*/
//}

object ServiceUserDAO {
  type serviceUserId = String
  type ServiceProfileSource = Source[Seq[ServiceProfile], NotUsed]
}