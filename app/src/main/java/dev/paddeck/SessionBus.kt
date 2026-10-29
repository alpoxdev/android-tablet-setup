package dev.paddeck

import dev.paddeck.vnc.RfbClient

/**
 * 화면과 입력기 서비스가 같은 원격 세션을 보게 하는 연결 지점입니다.
 * 원격 화면이 떠 있지 않으면 null입니다.
 */
object SessionBus {
    @Volatile
    var client: RfbClient? = null

    @Volatile
    var remoteInputActive: Boolean = false

    val isRemote: Boolean get() = client != null

    fun sendKey(down: Boolean, keysym: Int) {
        client?.sendKey(down, keysym)
    }
}
