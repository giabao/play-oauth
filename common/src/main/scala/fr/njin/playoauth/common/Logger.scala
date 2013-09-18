package fr.njin.playoauth.common

import org.slf4j.LoggerFactory

/**
 * User: bathily
 * Date: 18/09/13
 */
trait Logger {
     val logger = LoggerFactory.getLogger(getClass)
}
