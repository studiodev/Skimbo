package services.auth

import play.api._
import play.api.mvc._
import play.api.libs.oauth._
import play.api.Play.current
import play.api.libs.ws.WS
import services.UserDao
import scala.concurrent.Await
import scala.concurrent.duration._
import models.user.SkimboToken
import services.commands.Commands
import models.command.NewToken
import services.actors.UserInfosActor

trait OAuthProvider extends GenericProvider {

  lazy val KEY = ConsumerKey(config.getString("clientId").get, config.getString("secret").get)

  lazy val service = OAuth(ServiceInfo(
    config.getString("requestToken").get+"?scope="+permissions.mkString(permissionsSep),
    config.getString("accessToken").get,
    config.getString("authorize").get, KEY),
    false)

  // Session and cookies fields
  lazy val fieldToken = namespace+"_token"
  lazy val fieldSecret = namespace+"_expires"

  /**
   * Execute authentication on provider
   * @param redirectRoute : Where the user wil be redirected after correct authentication
   */
  override def auth(redirectRoute: Call)(implicit request: RequestHeader): Result = {
    request.getQueryString("oauth_verifier") match {

      // Step 1 : ask request token to provider and then redirect to accreditation page
      case None =>
        service.retrieveRequestToken(authRoute.absoluteURL(false)) match {
          case Right(t) => Redirect(service.redirectUrl(t.token))
                            .withSession(request.session + (fieldToken -> t.token) + (fieldSecret -> t.secret))
          case Left(e)  => throw e
        }

      // Step 2 : Retrieve access-token from WS and redirect to app
      case Some(verifier) =>
        service.retrieveAccessToken(getSessionToken, verifier) match {
          case Right(t) => {
              val session = generateUniqueId(request.session)
              //TODO rework this, same lignes in OAuth2Provider
              UserDao.setToken(session("id"), this, SkimboToken(t.token, Some(t.secret)))
              Commands.interpretCmd(session("id"), NewToken.asCommand(this))
              UserInfosActor.refreshInfosUser(session("id"), this)
              Redirect(redirectRoute).withSession(session)
            }
          case Left(e)  => Redirect(redirectRoute).flashing("login-error" -> name)
        }
    }
  }
  
  private def getSessionToken(implicit request:RequestHeader) = {
    RequestToken(request.session.get(fieldToken).get, request.session.get(fieldSecret).get)
  }

  override def getToken(implicit request: RequestHeader): Option[RequestToken] = {
    val skimboToken = request.session.get("id").flatMap(id => Await.result(UserDao.getToken(id, this), 1 second))
    skimboToken.map(st => RequestToken(st.token, st.secret.get))
  }

  override def fetch(url: String)(implicit request: RequestHeader) = {
    WS.url(url).sign(OAuthCalculator(KEY, getToken.get))
  }

}