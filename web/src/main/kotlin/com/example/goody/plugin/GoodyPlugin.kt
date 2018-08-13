package com.example.goody.plugin

import com.example.goody.api.GoodyApi
import net.corda.webserver.services.WebServerPluginRegistry
import java.util.function.Function

class GoodyPlugin : WebServerPluginRegistry {
    override val webApis = listOf(Function(::GoodyApi))
}
