package com.verifysphere

/**
 * Holds the decrypted payload fields extracted from the verifysphere:// deep-link.
 *
 * Expected JSON after decryption:
 * {
 *   "url":         "https://my-app.com",
 *   "sitekey":     "0x4AAAAAAA...",
 *   "callbackUrl": "https://my-app.com/callback?foo=bar"
 * }
 */
internal data class IntentPayload(
    val url: String,
    val sitekey: String,
    val callbackUrl: String
)
