package me.znotchill.lime.log

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

class LimeLogWriter : LogWriter() {
    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val color = when (severity) {
            Severity.Verbose -> Ansi.GRAY
            Severity.Debug -> Ansi.CYAN
            Severity.Info -> Ansi.GREEN
            Severity.Warn -> Ansi.YELLOW
            Severity.Error, Severity.Assert -> Ansi.RED
        }
        
        println(
            "${Ansi.GRAY} " +
            "${color}${Ansi.BOLD}${severity.name.padEnd(5)} " +
            "${Ansi.RESET}${Ansi.PURPLE}[$tag] " +
            "${Ansi.RESET}$message"
        )
        
        throwable?.printStackTrace()
    }
}