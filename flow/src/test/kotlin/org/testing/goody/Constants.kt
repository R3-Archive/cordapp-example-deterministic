@file:JvmName("Constants")
package org.testing.goody

import net.corda.core.identity.CordaX500Name

@JvmField
val BOG_NAME = CordaX500Name("BankOfGoodies", "London", "GB")

@JvmField
val SOG_NAME = CordaX500Name("ShopOfGoodies", "New York", "US")

fun fail(message: String): Nothing = throw AssertionError(message)
