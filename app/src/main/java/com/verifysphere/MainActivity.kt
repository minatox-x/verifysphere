package com.verifysphere

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.turnstilekit.TurnstileCallback
import com.turnstilekit.TurnstileSDK
import com.verifysphere.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Holds decoded payload for the current session only — never stored to disk
    private var payload: IntentPayload? = null

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRetry.setOnClickListener { startVerification() }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    // -----------------------------------------------------------------------
    // Intent handling
    // -----------------------------------------------------------------------

    private fun handleIntent(intent: Intent) {
        val data: Uri? = intent.data

        if (data == null || data.scheme != "verifysphere") {
            showError("No verification request received.\n\nOpen a verifysphere:// link to begin.")
            return
        }

        // The entire path after "verifysphere://" is the encrypted payload.
        // Accepted forms:
        //   verifysphere://verify?data=<base64>
        //   verifysphere://<base64>
        val encrypted: String? = data.getQueryParameter("data")
            ?: data.host?.takeIf { it.isNotBlank() }
            ?: data.encodedPath?.removePrefix("/")?.takeIf { it.isNotBlank() }

        if (encrypted.isNullOrBlank()) {
            showError("Invalid verification link — no payload found.")
            return
        }

        try {
            val json = CryptoHelper.decrypt(encrypted)
            payload = PayloadParser.parse(json)
            showWaiting()
            startVerification()
        } catch (e: Exception) {
            showError("Invalid or corrupted verification link.")
        }
    }

    // -----------------------------------------------------------------------
    // Turnstile flow
    // -----------------------------------------------------------------------

    private fun startVerification() {
        val p = payload ?: run {
            showError("No verification data. Please retry the original link.")
            return
        }

        showWaiting()

        TurnstileSDK.call(
            activity = this,
            url      = p.url,
            sitekey  = p.sitekey,
            callback = object : TurnstileCallback {

                override fun onSuccess(contextId: String) {
                    val result = TurnstileSDK.report(contextId)
                    if (result == null || result.isExpired()) {
                        TurnstileSDK.clear(contextId)
                        showRetry("Verification expired. Please try again.")
                        return
                    }

                    val rawToken = result.token
                    TurnstileSDK.clear(contextId)

                    deliverToken(rawToken)
                }

                override fun onFailure(error: String) {
                    when (error) {
                        "cancelled"     -> showRetry("Verification was cancelled.")
                        "load_timeout"  -> showRetry("Network timeout. Please check your connection and try again.")
                        "token_expired" -> showRetry("Verification expired. Please try again.")
                        else            -> showRetry("Verification failed. Please try again.")
                    }
                }
            }
        )
    }

    // -----------------------------------------------------------------------
    // Token delivery
    // -----------------------------------------------------------------------

    private fun deliverToken(rawToken: String) {
        val p = payload ?: return

        // Encrypt the token with the same key so the server can decrypt it
        val encryptedToken = try {
            CryptoHelper.encrypt(rawToken)
        } catch (e: Exception) {
            showRetry("Failed to prepare token. Please try again.")
            return
        }

        // URL-encode the encrypted Base64 token for safe embedding in a query param
        val urlSafeToken = Uri.encode(encryptedToken)

        // Append token to callbackUrl (handle existing query params)
        val callbackWithToken = buildCallbackUrl(p.callbackUrl, urlSafeToken)

        // Show success state first
        showSuccess()

        // Launch the URL in external browser / browser chooser
        openInBrowser(callbackWithToken, encryptedToken)
    }

    /**
     * Appends ?token=... or &token=... to the callback URL.
     */
    private fun buildCallbackUrl(callbackUrl: String, urlSafeToken: String): String {
        return if (callbackUrl.contains('?')) {
            "$callbackUrl&token=$urlSafeToken"
        } else {
            "$callbackUrl?token=$urlSafeToken"
        }
    }

    /**
     * Opens [url] in the system browser via a chooser.
     * If no browser is available, shows a dialog with a copy button.
     */
    private fun openInBrowser(url: String, encryptedToken: String) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        val chooser = Intent.createChooser(browserIntent, "Open verification result in browser")

        if (browserIntent.resolveActivity(packageManager) != null) {
            startActivity(chooser)
            // Also show the fallback dialog so user has the URL if browser fails
            showFallbackDialog(url, encryptedToken)
        } else {
            // No browser at all — go straight to dialog
            showFallbackDialog(url, encryptedToken)
        }
    }

    /**
     * Shows a dialog with the full callback URL and a Copy button.
     * The encrypted token is embedded; the decrypted value is never shown.
     */
    private fun showFallbackDialog(callbackUrl: String, encryptedToken: String) {
        // We show the full callbackUrl (with encrypted token appended) so
        // the user can copy and paste it. We do NOT show the raw token or
        // any decrypted values from the original payload.
        AlertDialog.Builder(this, R.style.AppDialog)
            .setTitle("Open this URL in your browser")
            .setMessage("If the browser did not open automatically, copy the link below and paste it into any browser:\n\n$callbackUrl")
            .setPositiveButton("Copy URL") { dialog, _ ->
                copyToClipboard("Verification URL", callbackUrl)
                Toast.makeText(this, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Close", null)
            .setCancelable(true)
            .show()
    }

    private fun copyToClipboard(label: String, text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    // -----------------------------------------------------------------------
    // UI state helpers
    // -----------------------------------------------------------------------

    private fun showWaiting() {
        with(binding) {
            layoutWaiting.visibility  = View.VISIBLE
            layoutSuccess.visibility  = View.GONE
            layoutError.visibility    = View.GONE
            layoutRetry.visibility    = View.GONE
        }
    }

    private fun showSuccess() {
        with(binding) {
            layoutWaiting.visibility  = View.GONE
            layoutSuccess.visibility  = View.VISIBLE
            layoutError.visibility    = View.GONE
            layoutRetry.visibility    = View.GONE
        }
    }

    private fun showRetry(message: String) {
        with(binding) {
            layoutWaiting.visibility  = View.GONE
            layoutSuccess.visibility  = View.GONE
            layoutError.visibility    = View.GONE
            layoutRetry.visibility    = View.VISIBLE
            tvRetryMessage.text       = message
        }
    }

    private fun showError(message: String) {
        with(binding) {
            layoutWaiting.visibility  = View.GONE
            layoutSuccess.visibility  = View.GONE
            layoutRetry.visibility    = View.GONE
            layoutError.visibility    = View.VISIBLE
            tvErrorMessage.text       = message
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Wipe any held tokens on activity destruction
        TurnstileSDK.clearAll()
        payload = null
    }
}
