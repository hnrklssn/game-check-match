package models

import play.twirl.api.Html

/**
 * Created by Henrik on 2017-05-09.
 */
abstract class Recommendation(html: Html) extends Html(scala.collection.immutable.Seq(html)) {
}

class FriendsWithGameRecommendation(profiles: Iterable[ServiceProfile], gameName: String) extends Recommendation(views.html.recommendations.friendsWithGameRec(profiles, gameName))
class MutualFriendsRecommendation(mutualFriends: Seq[ServiceProfile], name: String) extends Recommendation(views.html.recommendations.mutualFriendsRec(mutualFriends, name))
