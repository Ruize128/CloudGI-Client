package io.github.ruize128.cloudgi.client

/** A discovery observation, including services that are not verified CloudGI hosts. */
data class DiscoveredHost(
    val key: String,
    val serviceName: String,
    val source: String,
    val address: String? = null,
    val sunshinePort: Int? = null,
    val cloudGiName: String? = null,
    val status: String = "已发现服务，等待解析地址",
    val canConnect: Boolean = false,
    val hostId: String? = null,
)
