package com.rediac.namplayer

class NamEngine {

    companion object {
        init {
            System.loadLibrary("namplayer")
        }
    }

    external fun nativeGetVersion(): String
    external fun nativeLoadModel(path: String): Boolean
    external fun nativeStartAudio(): Boolean
    external fun nativeStopAudio()
    external fun nativeUnloadModel()
    external fun nativeIsModelLoaded(): Boolean

    val version: String       get() = nativeGetVersion()
    val isModelLoaded: Boolean get() = nativeIsModelLoaded()

    fun loadModel(path: String): Boolean = nativeLoadModel(path)
    fun startAudio(): Boolean            = nativeStartAudio()
    fun stopAudio()                      = nativeStopAudio()
    fun unloadModel()                    = nativeUnloadModel()
}
