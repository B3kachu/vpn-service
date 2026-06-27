package com.example.simplevpn

import android.content.Context
import android.provider.Settings
import java.io.File
import java.lang.reflect.Proxy

object XrayCoreBridge {
    private const val SEQ_CLASS_NAME = "go.Seq"
    private const val LIB_CLASS_NAME = "libv2ray.Libv2ray"
    private const val CALLBACK_CLASS_NAME = "libv2ray.CoreCallbackHandler"
    @Volatile
    private var controller: Any? = null
    @Volatile
    private var isEnvironmentInitialized = false
    @Volatile
    private var lastStatusMessage: String? = null

    fun isAvailable(): Boolean {
        return runCatching { Class.forName(LIB_CLASS_NAME) }.isSuccess
    }

    fun start(context: Context, configJson: String, tunFd: Int): Result<Unit> {
        return synchronized(this) {
            runCatching {
                if (!isAvailable()) {
                    error("libv2ray AAR not found in app/libs")
                }

                stop().getOrNull()
                initializeCoreEnvironment(context)
                val callbackInterface = Class.forName(CALLBACK_CLASS_NAME)
                val callback = Proxy.newProxyInstance(
                    callbackInterface.classLoader,
                    arrayOf(callbackInterface)
                ) { _, method, args ->
                    when (method.name) {
                        "startup" -> {
                            lastStatusMessage = "startup"
                            0L
                        }
                        "shutdown" -> {
                            lastStatusMessage = "shutdown"
                            controller = null
                            0L
                        }
                        "onEmitStatus" -> {
                            lastStatusMessage = args?.getOrNull(1) as? String
                            0L
                        }
                        else -> null
                    }
                }

                val libClass = Class.forName(LIB_CLASS_NAME)
                runCatching {
                    libClass.getMethod("reconcileBrowserDialer", String::class.java).invoke(null, "")
                }
                controller = libClass
                    .getMethod("newCoreController", callbackInterface)
                    .invoke(null, callback)

                startLoop(configJson, tunFd)
                if (!isRunning()) {
                    error(lastStatusMessage ?: "libv2ray core did not enter running state")
                }
            }
        }
    }

    fun stop(): Result<Unit> {
        return synchronized(this) {
            runCatching {
                controller?.javaClass?.getMethod("stopLoop")?.invoke(controller)
                controller = null
                lastStatusMessage = null
            }
        }
    }

    fun getVersionOrNull(): String? {
        return runCatching {
            Class.forName(LIB_CLASS_NAME)
                .getMethod("checkVersionX")
                .invoke(null) as? String
        }.getOrNull()
    }

    fun isRunning(): Boolean {
        val current = controller ?: return false
        return invokeBooleanMethodIfExists(current, "isRunning", "getIsRunning") ?: false
    }

    private fun initializeCoreEnvironment(context: Context) {
        if (isEnvironmentInitialized) {
            return
        }

        val seqClass = Class.forName(SEQ_CLASS_NAME)
        seqClass.getMethod("setContext", Context::class.java).invoke(null, context.applicationContext)

        val assetDir = File(context.noBackupFilesDir, "xray_assets").apply { mkdirs() }
        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )?.takeIf { it.isNotBlank() } ?: context.packageName
        Class.forName(LIB_CLASS_NAME)
            .getMethod("initCoreEnv", String::class.java, String::class.java)
            .invoke(null, assetDir.absolutePath, deviceId)
        isEnvironmentInitialized = true
    }

    private fun startLoop(configJson: String, tunFd: Int) {
        val current = controller ?: error("Controller was not created")
        val methods = current.javaClass.methods.filter { it.name == "startLoop" }
        val started = methods.any { method ->
            runCatching {
                val types = method.parameterTypes
                when {
                    types.size == 2 && types[1] == Int::class.javaPrimitiveType -> {
                        method.invoke(current, configJson, tunFd)
                        true
                    }
                    types.size == 2 && types[1] == Long::class.javaPrimitiveType -> {
                        method.invoke(current, configJson, tunFd.toLong())
                        true
                    }
                    else -> false
                }
            }.getOrDefault(false)
        }

        if (!started) {
            error("No compatible startLoop method found in libv2ray controller")
        }
    }

    private fun invokeBooleanMethodIfExists(target: Any, vararg methodNames: String): Boolean? {
        for (methodName in methodNames) {
            val value = runCatching {
                target.javaClass.getMethod(methodName).invoke(target) as? Boolean
            }.getOrNull()
            if (value != null) {
                return value
            }
        }
        return null
    }
}
