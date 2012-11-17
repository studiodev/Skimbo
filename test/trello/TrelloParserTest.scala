package trello

import org.specs2.mutable._
import play.api.test._
import play.api.test.Helpers._
import model.parser._
import model.Skimbo
import play.api.libs.json.Json

object TrelloParserTest extends Specification {

  "TrelloParser" should {
    "Read json from trello API for notification requests" in {
      val jsonMsg = TrelloWallParser.cut(TrelloFixture.miniTimeline)
      jsonMsg.size must beEqualTo(1)
      val msg = Json.fromJson[TrelloWallMessage](jsonMsg(0)).get
      msg.idMemberCreator must beEqualTo("501cf5007d3f7888395dbf71")
      msg.unread must beEqualTo(false)
    }

    "Read json from complexe API json" in {
      val jsonMsg = TrelloWallParser.cut(TrelloFixture.timeline)
      jsonMsg.size must beEqualTo(32)
    }

    "Convert Trello message as Skimbo" in {
      val jsonMsg = TrelloWallParser.cut(TrelloFixture.miniTimeline)
      val msg = Json.fromJson[TrelloWallMessage](jsonMsg(0)).get
      val res = TrelloWallParser.asSkimbo(msg)
      res.get.authorScreenName must beEqualTo(msg.memberCreator.username)
    }
  }

}