package io.github.xixka.qbittorrent.api

import java.io.IOException

/** Credentials rejected by the server (or the account is temporarily banned). */
class QBAuthException(message: String) : IOException(message)

/** Transport-level problem: wrong address, timeout, TLS failure, … */
class QBConnectException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** The server answered with an unexpected HTTP status. */
class QBApiException(message: String) : IOException(message)
