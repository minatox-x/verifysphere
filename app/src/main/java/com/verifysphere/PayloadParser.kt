package com.verifysphere

import org.json.JSONObject

internal object PayloadParser {

    /**
     * Takes the raw decrypted JSON string and returns an [IntentPayload].
     * Throws [IllegalArgumentException] if any required field is missing or invalid.
     */
    fun parse(json: String): IntentPayload {
        val obj = JSONObject(json)

        val url = obj.optString("url", "").trim()
        val sitekey = obj.optString("sitekey", "").trim()
        val callbackUrl = obj.optString("callbackUrl", "").trim()

        require(url.startsWith("http://") || url.startsWith("https://")) {
            "Missing or invalid 'url' in payload"
        }
        require(sitekey.isNotBlank()) { "Missing 'sitekey' in payload" }
        require(callbackUrl.startsWith("http://") || callbackUrl.startsWith("https://")) {
            "Missing or invalid 'callbackUrl' in payload"
        }

        return IntentPayload(url = url, sitekey = sitekey, callbackUrl = callbackUrl)
    }
}
