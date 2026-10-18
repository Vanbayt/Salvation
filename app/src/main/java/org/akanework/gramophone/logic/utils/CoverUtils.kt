package org.akanework.gramophone.logic.utils

object CoverUtils {
    private const val DEFAULT_BACKEND_HOST = "http://185.196.41.31"

    /**
     * Normalizes and upgrades cover image URLs to high quality (1000x1000).
     * - Fixes relative backend URLs starting with "/"
     * - If a cover URL contains yt3.googleusercontent.com or lh3.googleusercontent.com and has `=w...-h...`,
     *   ensures it requests high-res `=w1000-h1000-l90-rj` (or `=s1000`).
     * - Upgrades Deezer CDN covers to 1000x1000.
     */
    fun upgradeCoverQuality(url: String?): String? {
        if (url.isNullOrBlank()) return null
        var formatted = if (url.startsWith("/")) "$DEFAULT_BACKEND_HOST$url" else url

        // Google User Content (YouTube Music / Google / YouTube channel cover art)
        if (formatted.contains("yt3.googleusercontent.com") || formatted.contains("lh3.googleusercontent.com")) {
            if (formatted.contains(Regex("=w\\d+-h\\d+"))) {
                formatted = formatted.replace(Regex("=w\\d+-h\\d+[^?#]*"), "=w1000-h1000-l90-rj")
            } else if (formatted.contains(Regex("=s\\d+"))) {
                formatted = formatted.replace(Regex("=s\\d+[^?#]*"), "=s1000")
            }
        }

        // Deezer CDN high-res 1000x1000 upgrade
        if (formatted.contains("dzcdn.net") && formatted.contains(Regex("/\\d+x\\d+-"))) {
            formatted = formatted.replace(Regex("/\\d+x\\d+-"), "/1000x1000-")
        }

        return formatted
    }

    /**
     * Returns true if the cover URL is missing, blank, or is a low-res video thumbnail (e.g. hqdefault.jpg).
     */
    fun isLowResOrEmpty(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        return url.contains("hqdefault.jpg") || url.contains("mqdefault.jpg") || url.contains("default.jpg")
    }

    /**
     * Returns the best available high-res cover URL.
     * If [currentCover] is empty or low-res (e.g. hqdefault.jpg) and [alternateCover] has a high-res cover,
     * returns [alternateCover] (upgraded); otherwise returns [currentCover] (upgraded).
     */
    fun getBestCover(currentCover: String?, alternateCover: String?): String? {
        val currentUpgraded = upgradeCoverQuality(currentCover)
        val altUpgraded = upgradeCoverQuality(alternateCover)
        return if (isLowResOrEmpty(currentUpgraded) && !isLowResOrEmpty(altUpgraded)) {
            altUpgraded
        } else {
            currentUpgraded ?: altUpgraded
        }
    }
}
