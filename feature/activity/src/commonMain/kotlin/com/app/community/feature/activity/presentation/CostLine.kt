package com.app.community.feature.activity.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.app.community.core.model.findPhoneNumber
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import agora.feature.activity.generated.resources.Res
import agora.feature.activity.generated.resources.detail_cost
import agora.feature.activity.generated.resources.detail_phone_copied
import agora.feature.activity.generated.resources.detail_phone_copy

/**
 * La linea de "Coste". Cuando el "como se paga" lleva un telefono (el caso normal del
 * pago externo: un Bizum), ese telefono se puede tocar para copiarlo. Antes el texto
 * no era ni seleccionable, asi que el numero habia que copiarlo a mano mirandolo.
 *
 * Se copia sin espacios ni guiones, que es lo unico que aceptan el marcador y las
 * apps de pago aunque el admin lo haya escrito con separadores.
 */
@Composable
internal fun CostLine(cost: String, modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.detail_cost, cost)
    val phone = remember(cost) { findPhoneNumber(cost) }
    if (phone == null) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
        return
    }

    val clipboard = LocalClipboardManager.current
    val copyLabel = stringResource(Res.string.detail_phone_copy)
    var copied by remember(phone) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    // Subrayado y en color primario, igual que el enlace de la ubicacion justo encima:
    // en esta tarjeta eso ya significa "esto se toca".
    val start = label.indexOf(phone.raw)
    val text = buildAnnotatedString {
        append(label)
        if (start >= 0) {
            addStyle(
                SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                ),
                start,
                start + phone.raw.length,
            )
        }
    }

    Column(modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clickable(onClickLabel = copyLabel) {
                clipboard.setText(AnnotatedString(phone.normalized))
                copied = true
            },
        )
        if (copied) {
            Text(
                text = stringResource(Res.string.detail_phone_copied),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
