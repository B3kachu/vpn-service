package com.example.simplevpn

enum class StatusTone {
    IDLE,
    PENDING,
    SUCCESS,
    ERROR
}

data class VlessRuntimeState(
    val status: String,
    val details: String,
    val tone: StatusTone,
    val isRunning: Boolean
)

data class ParsedVlessConfig(
    val rawLink: String,
    val displayName: String,
    val userId: String,
    val serverAddress: String,
    val serverPort: Int,
    val encryption: String,
    val transport: String,
    val security: String,
    val hostHeader: String,
    val path: String,
    val sni: String,
    val flow: String,
    val fingerprint: String,
    val publicKey: String,
    val shortId: String,
    val alpnValues: List<String>
)

object VlessRuntimeStateStore {
    @Volatile
    var currentState: VlessRuntimeState = VlessRuntimeState(
        status = "idle",
        details = "Save a VLESS profile and press Connect.",
        tone = StatusTone.IDLE,
        isRunning = false
    )
}

object VlessConnectionIntents {
    const val ACTION_CONNECT = "com.example.simplevpn.action.CONNECT_VLESS"
    const val ACTION_DISCONNECT = "com.example.simplevpn.action.DISCONNECT_VLESS"
    const val ACTION_STATE_CHANGED = "com.example.simplevpn.action.STATE_CHANGED"
}
