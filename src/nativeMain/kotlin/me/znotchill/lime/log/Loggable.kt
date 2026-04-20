package me.znotchill.lime.log

import co.touchlab.kermit.Logger

interface Loggable {
    val loggerTag: String
    val log: Logger get() = Logger.Companion.withTag(loggerTag)
}