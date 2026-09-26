package com.solanasignal.app.photon

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Opens the official Photon Solana web terminal for manual trading (spec #30-32).
 *
 * The app does NOT execute trades through Photon. Confirmed real Photon token/pool
 * page format (seen in Photon's own social posts and third-party address extractors):
 *   https://photon-sol.tinyastro.io/en/lp/{poolAddress}
 * where {poolAddress} is the liquidity-pool address Photon indexes by - NOT the
 * token mint. For a pre-migration pump.fun token, PumpPortal's "bondingCurveKey"
 * is the closest available identifier for that pool; this is a best-effort mapping,
 * not a guarantee, since it hasn't been verified against Photon's own docs (Photon
 * doesn't publish a formal deep-link spec). After a token migrates to Raydium/PumpSwap
 * its real LP address usually differs from the pump.fun bonding curve, so a bonding
 * curve key from a migrated token likely will NOT resolve correctly on Photon.
 *
 * Behavior:
 *  - poolAddress present  -> open https://photon-sol.tinyastro.io/en/lp/{poolAddress} directly.
 *  - poolAddress missing  -> fall back to the official homepage + copy the mint to
 *    the clipboard so the user can paste/search it manually.
 * In both cases the mint is copied to the clipboard as a safety net, and only the
 * verified photon-sol.tinyastro.io domain is ever opened.
 */
object PhotonLauncher {

    private const val OFFICIAL_PHOTON_HOST = "photon-sol.tinyastro.io"
    private const val HOMEPAGE_URL = "https://photon-sol.tinyastro.io/"

    fun buildOpenIntent(context: Context, mint: String, poolAddress: String? = null): Intent {
        val url = if (!poolAddress.isNullOrBlank()) {
            "https://$OFFICIAL_PHOTON_HOST/en/lp/$poolAddress"
        } else {
            HOMEPAGE_URL
        }
        val uri = Uri.parse(url)
        // Verified-domain-only guard: never build an intent pointing anywhere else.
        require(uri.host == OFFICIAL_PHOTON_HOST) { "Refusing to open unverified Photon domain" }
        return Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /** Copies the mint to the clipboard and opens Photon, scoped to the token's pool when known. */
    fun openForToken(context: Context, mint: String, poolAddress: String?, symbol: String?) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Solana token mint", mint))
        val message = if (!poolAddress.isNullOrBlank())
            "Opening Photon for this token. Address also copied as backup."
        else
            "Token address copied \u2014 paste it into Photon (pool not identified yet)."
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        context.startActivity(buildOpenIntent(context, mint, poolAddress))
    }
}
