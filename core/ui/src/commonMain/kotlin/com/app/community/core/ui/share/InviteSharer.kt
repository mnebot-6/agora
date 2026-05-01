package com.app.community.core.ui.share

import androidx.compose.runtime.Composable

/**
 * Abstracción multiplataforma para abrir el share-sheet del sistema con un texto.
 * Usado para compartir invitaciones a comunidades como link.
 */
expect class InviteSharer {
    fun share(text: String)
}

@Composable
expect fun rememberInviteSharer(): InviteSharer
