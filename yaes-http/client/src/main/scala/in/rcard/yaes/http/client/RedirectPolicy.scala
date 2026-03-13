package in.rcard.yaes.http.client

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
