package de.lifeos.core.runtime

import dalvik.system.InMemoryDexClassLoader
import java.nio.ByteBuffer

interface DynamicPluginModule {
    fun executeAction(params: Map<String, Any?>): Map<String, Any?>
}

object DexHotSwapEngine {

    private val loadedModules = mutableMapOf<String, DynamicPluginModule>()

    /**
     * Lädt Dalvik-Bytecode (.dex) direkt aus einem Byte-Buffer in den Arbeitsspeicher.
     * Vollständig flüchtig (Zero-Disk-Trace).
     */
    fun injectDexBytecode(moduleName: String, dexBytes: ByteArray, entryClassName: String): Boolean {
        return try {
            val byteBuffer = ByteBuffer.wrap(dexBytes)
            val classLoader = InMemoryDexClassLoader(
                byteBuffer,
                DexHotSwapEngine::class.java.classLoader
            )

            val loadedClass = classLoader.loadClass(entryClassName)
            val instance = loadedClass.getDeclaredConstructor().newInstance() as? DynamicPluginModule

            if (instance != null) {
                loadedModules[moduleName] = instance
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun executeModule(moduleName: String, params: Map<String, Any?>): Map<String, Any?>? {
        val module = loadedModules[moduleName] ?: return null
        return module.executeAction(params)
    }

    fun listActiveModules(): List<String> = loadedModules.keys.toList()

    fun unloadModule(moduleName: String) {
        loadedModules.remove(moduleName)
    }

    /**
     * Registriert ein bereits instanziiertes Modul direkt.
     * Wird von BuiltInToolEngine für vorcompilierte Werkzeuge verwendet.
     */
    fun registerModule(moduleName: String, module: DynamicPluginModule) {
        loadedModules[moduleName] = module
    }
}
