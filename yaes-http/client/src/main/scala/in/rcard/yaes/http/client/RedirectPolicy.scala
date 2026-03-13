package in.rcard.yaes.http.client

import java.net.http.{HttpClient => JHttpClient}

/** Redirect-following policy for the HTTP client.
  *
  * @see [[YaesClientConfig]]
  */
enum RedirectPolicy:
  /** Never follow redirects. */
  case Never
  /** Always follow redirects, including cross-protocol (HTTPS to HTTP). */
  case Always
  /** Follow redirects except cross-protocol downgrades. */
  case Normal

  /** Converts to the corresponding [[java.net.http.HttpClient.Redirect]] constant. */
  def toJava: JHttpClient.Redirect = this match
    case Never  => JHttpClient.Redirect.NEVER
    case Always => JHttpClient.Redirect.ALWAYS
    case Normal => JHttpClient.Redirect.NORMAL
