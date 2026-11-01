package br.leve.launcher

import java.net.Inet4Address
import java.net.NetworkInterface

object Net {
    /** IP do projetor na rede local (Wi-Fi ou cabo), ou null sem rede. */
    fun localIp(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
            ?.hostAddress
    } catch (e: Exception) {
        null
    }
}
