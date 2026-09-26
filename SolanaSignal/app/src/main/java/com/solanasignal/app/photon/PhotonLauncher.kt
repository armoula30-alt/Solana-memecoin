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
 * The app does NOT execute trades through Photon and does NOT invent an unverified
 * deep-link URL format. Only the verified official domain under tinyastro.io is ever
 * opened. Since a token-specific deep-link parameter scheme for Photon is not
 * something this project can verify without live access to Photon's current docs,
 * the safe, spec-compliant default is: open the official site, copy the token mint
 * to the clipboard, and tell the user to paste it in. If/when a verified Photon
 * deep-link format is confirmed, replace buildOpenIntent's URL construction below -
 * do not guess at a format before that is confirmed.
 */
object PhotonLauncher {

    private const val OFFICIAL_PHOTON_URL = "https://photon-sol.tinyastro.io/"
    private const val OFFICIAL_PHOTON_HOST = "photon-sol.tinyastro.io"

    fun buildOpenIntent(context: Context, mint: String): Intent {
        // Verified-domain-only guard: never build an intent pointing anywhere else.
        val uri = Uri.parse(OFFICIAL_PHOTON_URL)
        require(uri.host == OFFICIAL_PHOTON_HOST) { "Refusing to open unverified Photon domain" }
        return Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /** Copies the mint to the clipboard and opens the official Photon site (spec #31/#32). */
    fun openForToken(context: Context, mint: String, symbol: String?) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Solana token mint", mint))
        Toast.makeText(context, "Token address copied \u2014 paste it into Photon.", Toast.LENGTH_LONG).show()
        context.startActivity(buildOpenIntent(context, mint))
    }
}
