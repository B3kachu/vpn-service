package com.example.simplevpn

import org.json.JSONArray
import org.json.JSONObject

object VlessXrayConfigBuilder {
    fun build(
        profile: ParsedVlessConfig,
        dnsServers: List<String>,
        splitTunnelMode: AppSettings.SplitTunnelMode,
        splitTunnelDomains: Set<String>
    ): String {
        val user = JSONObject()
            .put("id", profile.userId)
            .put("encryption", profile.encryption)
            .put("level", 8)

        if (profile.flow.isNotBlank()) {
            user.put("flow", profile.flow)
        }

        val proxyOutbound = JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vless")
            .put(
                "settings",
                JSONObject().put(
                    "vnext",
                    JSONArray().put(
                        JSONObject()
                            .put("address", profile.serverAddress)
                            .put("port", profile.serverPort)
                            .put("users", JSONArray().put(user))
                    )
                )
            )
            .put("streamSettings", buildStreamSettings(profile))
            .put("mux", JSONObject().put("enabled", false))

        return JSONObject()
            .put("stats", JSONObject())
            .put("log", JSONObject().put("loglevel", "info"))
            .put("policy", buildPolicy())
            .put("inbounds", buildInbounds())
            .put("outbounds", buildOutbounds(proxyOutbound))
            .put("routing", buildRouting(splitTunnelMode, splitTunnelDomains))
            .put("dns", buildDns(dnsServers))
            .toString()
    }

    private fun buildRouting(
        splitTunnelMode: AppSettings.SplitTunnelMode,
        splitTunnelDomains: Set<String>
    ): JSONObject {
        val rules = JSONArray()
        val domainRules = splitTunnelDomains
            .map(::normalizeDomainRule)
            .filter { it.isNotBlank() }

        if (domainRules.isNotEmpty()) {
            val domainsJson = JSONArray().apply {
                domainRules.forEach { put(it) }
            }
            when (splitTunnelMode) {
                AppSettings.SplitTunnelMode.BYPASS_SELECTED -> {
                    rules.put(
                        JSONObject()
                            .put("type", "field")
                            .put("domain", domainsJson)
                            .put("outboundTag", "direct")
                    )
                }

                AppSettings.SplitTunnelMode.PROXY_SELECTED -> {
                    rules.put(
                        JSONObject()
                            .put("type", "field")
                            .put("domain", domainsJson)
                            .put("outboundTag", "proxy")
                    )
                    rules.put(
                        JSONObject()
                            .put("type", "field")
                            .put("network", "tcp,udp")
                            .put("outboundTag", "direct")
                    )
                }
            }
        }

        return JSONObject()
            .put("domainStrategy", "AsIs")
            .put("rules", rules)
    }

    private fun buildDns(dnsServers: List<String>): JSONObject {
        val servers = JSONArray()
        dnsServers.ifEmpty { listOf("8.8.8.8", "8.8.4.4") }.forEach { servers.put(it) }
        return JSONObject().put("hosts", JSONObject()).put("servers", servers)
    }

    private fun normalizeDomainRule(domain: String): String {
        val value = domain.trim()
        if (value.isBlank()) {
            return ""
        }
        return if (':' in value) value else "domain:$value"
    }

    private fun buildPolicy(): JSONObject {
        return JSONObject()
            .put(
                "levels",
                JSONObject().put(
                    "8",
                    JSONObject()
                        .put("handshake", 4)
                        .put("connIdle", 300)
                        .put("uplinkOnly", 1)
                        .put("downlinkOnly", 1)
                )
            )
            .put(
                "system",
                JSONObject()
                    .put("statsOutboundUplink", true)
                    .put("statsOutboundDownlink", true)
            )
    }

    private fun buildInbounds(): JSONArray {
        val sniffing = JSONObject()
            .put("enabled", true)
            .put("destOverride", JSONArray().put("http").put("tls").put("quic"))

        return JSONArray()
            .put(
                JSONObject()
                    .put("tag", "socks")
                    .put("port", 10808)
                    .put("protocol", "socks")
                    .put(
                        "settings",
                        JSONObject()
                            .put("auth", "noauth")
                            .put("udp", true)
                            .put("userLevel", 8)
                    )
                    .put("sniffing", sniffing)
            )
            .put(
                JSONObject()
                    .put("tag", "tun")
                    .put("protocol", "tun")
                    .put(
                        "settings",
                        JSONObject()
                            .put("name", "xray0")
                            .put("MTU", 1500)
                            .put("userLevel", 8)
                    )
                    .put("sniffing", sniffing)
            )
    }

    private fun buildOutbounds(proxyOutbound: JSONObject): JSONArray {
        return JSONArray()
            .put(proxyOutbound)
            .put(
                JSONObject()
                    .put("protocol", "freedom")
                    .put("tag", "direct")
                    .put(
                        "streamSettings",
                        JSONObject().put(
                            "sockopt",
                            JSONObject().put("domainStrategy", "UseIP")
                        )
                    )
            )
            .put(
                JSONObject()
                    .put("protocol", "blackhole")
                    .put("tag", "block")
                    .put(
                        "settings",
                        JSONObject().put(
                            "response",
                            JSONObject().put("type", "http")
                        )
                    )
            )
    }

    private fun buildStreamSettings(profile: ParsedVlessConfig): JSONObject {
        val streamSettings = JSONObject().put("network", profile.transport)

        when (profile.security) {
            "tls" -> {
                streamSettings.put("security", "tls")
                streamSettings.put("tlsSettings", buildTlsSettings(profile))
            }
            "reality" -> {
                streamSettings.put("security", "reality")
                streamSettings.put("realitySettings", buildRealitySettings(profile))
            }
            else -> streamSettings.put("security", "none")
        }

        when (profile.transport) {
            "ws" -> {
                val wsSettings = JSONObject().put("path", profile.path.ifBlank { "/" })
                val hostHeader = profile.hostHeader.ifBlank { profile.sni }
                if (hostHeader.isNotBlank()) {
                    wsSettings.put("headers", JSONObject().put("Host", hostHeader))
                }
                streamSettings.put("wsSettings", wsSettings)
            }
            "grpc" -> {
                streamSettings.put(
                    "grpcSettings",
                    JSONObject().put("serviceName", profile.path.removePrefix("/"))
                )
            }
        }

        return streamSettings
    }

    private fun buildTlsSettings(profile: ParsedVlessConfig): JSONObject {
        val tlsSettings = JSONObject().put("allowInsecure", false)
        if (profile.sni.isNotBlank()) {
            tlsSettings.put("serverName", profile.sni)
        }
        if (profile.fingerprint.isNotBlank()) {
            tlsSettings.put("fingerprint", profile.fingerprint)
        }
        if (profile.alpnValues.isNotEmpty()) {
            tlsSettings.put("alpn", JSONArray(profile.alpnValues))
        } else if (profile.transport == "ws") {
            tlsSettings.put("alpn", JSONArray().put("http/1.1"))
        }
        return tlsSettings
    }

    private fun buildRealitySettings(profile: ParsedVlessConfig): JSONObject {
        val realitySettings = JSONObject()
        if (profile.sni.isNotBlank()) {
            realitySettings.put("serverName", profile.sni)
        }
        if (profile.fingerprint.isNotBlank()) {
            realitySettings.put("fingerprint", profile.fingerprint)
        }
        if (profile.publicKey.isNotBlank()) {
            realitySettings.put("publicKey", profile.publicKey)
        }
        if (profile.shortId.isNotBlank()) {
            realitySettings.put("shortId", profile.shortId)
        }
        if (profile.alpnValues.isNotEmpty()) {
            realitySettings.put("alpn", JSONArray(profile.alpnValues))
        }
        return realitySettings
    }
}
