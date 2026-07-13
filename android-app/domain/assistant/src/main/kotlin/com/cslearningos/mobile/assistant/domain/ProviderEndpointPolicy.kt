package com.cslearningos.mobile.assistant.domain

import java.net.URI

const val InvalidProviderEndpointMessage = "Provider endpoint must use HTTPS and include a host."

fun isValidProviderEndpoint(value: String): Boolean =
    runCatching {
        URI(value).let { uri ->
            uri.isAbsolute && uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
        }
    }.getOrDefault(false)

fun requireValidProviderEndpoint(value: String) {
    require(isValidProviderEndpoint(value)) { InvalidProviderEndpointMessage }
}
