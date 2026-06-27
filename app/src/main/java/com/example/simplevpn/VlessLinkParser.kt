package com.example.simplevpn

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object VlessLinkParser {
    fun parse(link: String): ParsedVlessConfig? {
        if (!link.startsWith("vless://")) {
            return null
        }

        val withoutScheme = link.removePrefix("vless://")
        val hashParts = withoutScheme.split("#", limit = 2)
        val mainPart = hashParts[0]
        val displayName = hashParts.getOrNull(1)?.let {
            URLDecoder.decode(it, StandardCharsets.UTF_8.name())
        }?.ifBlank { "Saved VLESS Node" } ?: "Saved VLESS Node"

        val userAndRest = mainPart.split("@", limit = 2)
        if (userAndRest.size != 2) {
            return null
        }

        val userId = userAndRest[0]
        val hostAndQuery = userAndRest[1].split("?", limit = 2)
        val addressParts = hostAndQuery[0].split(":", limit = 2)
        if (addressParts.size != 2) {
            return null
        }

        val serverAddress = addressParts[0]
        val serverPort = addressParts[1].toIntOrNull() ?: return null
        val query = hostAndQuery.getOrNull(1).orEmpty()
        val params = query.split("&")
            .mapNotNull { token ->
                val kv = token.split("=", limit = 2)
                if (kv.size == 2) {
                    kv[0] to URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name())
                } else {
                    null
                }
            }
            .toMap()

        val alpnValues = params["alpn"]
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

        return ParsedVlessConfig(
            rawLink = link,
            displayName = displayName,
            userId = userId,
            serverAddress = serverAddress,
            serverPort = serverPort,
            encryption = params["encryption"].orEmpty().ifBlank { "none" },
            transport = params["type"].orEmpty().ifBlank { "tcp" }.lowercase(),
            security = params["security"].orEmpty().ifBlank { "none" }.lowercase(),
            hostHeader = params["host"].orEmpty(),
            path = params["path"].orEmpty(),
            sni = params["sni"].orEmpty(),
            flow = params["flow"].orEmpty(),
            fingerprint = params["fp"].orEmpty(),
            publicKey = params["pbk"].orEmpty(),
            shortId = params["sid"].orEmpty(),
            alpnValues = alpnValues
        )
    }
}
