package io.github.ruize128.cloudgi.client

/** Keep an available preference, or use the only verified host. Ambiguity requires selection. */
fun chooseHostId(availableIds: List<String>, preferredId: String?): String? {
    val candidates = availableIds.distinct()
    return preferredId?.takeIf { it in candidates } ?: candidates.singleOrNull()
}
