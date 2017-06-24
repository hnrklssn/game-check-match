Game-Check-Match!
===================================
This is the complete source code for the Game-Check-Match website. Game-Check-Match uses the Steam API to give you recommendations on new people to play with, with similar interests and maybe even mutual friends. It can also give you recommendations on new games to play based on what's currently popular among your friends, or among other people that tend to like the same games as you.

At the moment you can find a running example of this site under the following URL: https://game-check-match.herokuapp.com/
This is still in development and not ready for users yet, but you're welcome to have a look around if you don't feel like cloning the repo and running it locally. 

## Frameworks and technologies

This website is built using the Play! Scala web framework, with the Silhouette module for authentication and based on the Play Silhouette Seed as a starting point.

Guice is used for dependency injection, helping maintain loose coupling. At the moment the Steam API is unparalleled of its kind among the PC gaming platforms, however should for example Origin create an API it will not be too much of a hassle to integrate with this application.

MongoDB and Neo4j are the DBs currently in use, with Neo4j powering the pattern finding in the recommendation engine, and Mongo storing raw data concerning users, games and steam profiles.

## TODO
In no particular order:

#### Increase usability
The visuals and navigation are still very much in the development phase.

#### Implement data collection and statistics around usage
I will probably start out with Google Analytics since that's what I'm most familiar with. If I ever get enough users it'd be fun to try some A/B testing to optimise recommendations.

#### Improve the recommendation engine
At the moment the recommendation engine uses preset recommendation types (people with many games and play time in common, games with a surge in playtime the last 2 weeks, etc.) on each page, and recommendations are only scored based on that particular pattern. GraphAware have made a great recommendation engine (https://github.com/graphaware/neo4j-reco) for Neo4j, with more or less all the features that I'd like, but for the learning experience I'd like to implement them myself. If I ever get enough users it'd be fun to try machine learning as well, and see which yields the best results.
  
#### Facilitate interaction between users
Maybe show other users currently Online, "Looking to play" etc and either implement a chat system, or somehow make the Steam chat easily accessible.

#### Take current price deals into consideration
When recommending games the user doesn't have, give priority to games with a temporarily reduced price.

#### Client side rendering of recommendations
All HTML content is currently generated server side. I'm planning to integrate some Scala.js for loading the recommendations. This would improve rendering times if the databases are overloaded, and allow for a "Load new recommendations" feature.

#### Write tests
Starting out I was too busy learning the Play/Silhouette architecture to be able to meaningfully practice TDD, since everything was a lot of trial and error. I've enjoyed TDD when working in Java and Node, so I really should take the time to properly learn a Scala testing framework.

#### Protect against injection attacks
At the moment all Neo4j queries are made by constructing [Cypher](https://neo4j.com/developer/cypher-query-language/) query strings. Currently, a Steam user could change their name to valid Cypher syntax and change the effect of the query to inject unwanted data, or potentially delete all data in the database.

#### Implement Continuous Integration with automated testing
After proper unit tests have been implemented, the next step would be setting up a dev branch and make Heroku pull the latest passing version from the master branch.

#### Make a cool logo
Fact: all serious GitHub projects have a cool logo. That's what differentiates the hardcore from the hobby projects.

# License

The code is licensed under [Apache License v2.0](http://www.apache.org/licenses/LICENSE-2.0).

## Contributing
Although the final goal for the project is pretty open ended, there are some short term plans for the development. Since I'm currently the only contributor it's all in my head, save for the TODO list above. If you want to contribute, just contact me and I'll share some more detailed plans and we can brainstorm the best way to implement it.
