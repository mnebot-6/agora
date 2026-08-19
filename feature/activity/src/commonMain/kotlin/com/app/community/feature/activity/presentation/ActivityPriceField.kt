package com.app.community.feature.activity.presentation

import agora.feature.activity.generated.resources.Res
import agora.feature.activity.generated.resources.price_free
import agora.feature.activity.generated.resources.price_help_legacy
import agora.feature.activity.generated.resources.price_invalid
import agora.feature.activity.generated.resources.price_label
import agora.feature.activity.generated.resources.price_paid
import agora.feature.activity.generated.resources.price_per_slot_label
import agora.feature.activity.generated.resources.price_placeholder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.app.community.core.model.parseEurosToCents
import com.app.community.core.ui.theme.AgoraSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * Coste de la actividad: gratuita o de pago con importe. Sustituye al campo de texto libre
 * que habia antes ("Ej: Bizum de 6.5 euros por persona").
 *
 * Lo comparten crear y editar para que las dos pantallas validen igual: si divergen, una
 * de las dos acabara aceptando un importe que la otra rechaza.
 */
@Composable
fun ActivityPriceField(
    isPaid: Boolean,
    priceInput: String,
    onIsPaidChange: (Boolean) -> Unit,
    onPriceInputChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Texto de coste antiguo, si esta actividad todavia no tiene importe. */
    legacyCostDescription: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.price_label),
            style = MaterialTheme.typography.labelLarge,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.sm),
        ) {
            SlotModeCard(
                label = stringResource(Res.string.price_free),
                isSelected = !isPaid,
                onClick = { onIsPaidChange(false) },
                modifier = Modifier.weight(1f),
            )
            SlotModeCard(
                label = stringResource(Res.string.price_paid),
                isSelected = isPaid,
                onClick = { onIsPaidChange(true) },
                modifier = Modifier.weight(1f),
            )
        }

        if (isPaid) {
            // Solo se marca en rojo cuando hay algo escrito: un campo vacio recien
            // abierto no es un error todavia, es que aun no has escrito.
            val isInvalid = priceInput.isNotBlank() && parseEurosToCents(priceInput) == null

            OutlinedTextField(
                value = priceInput,
                onValueChange = onPriceInputChange,
                label = { Text(stringResource(Res.string.price_per_slot_label)) },
                placeholder = { Text(stringResource(Res.string.price_placeholder)) },
                suffix = { Text("€") },
                singleLine = true,
                isError = isInvalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = if (isInvalid) {
                    { Text(stringResource(Res.string.price_invalid)) }
                } else {
                    null
                },
                modifier = Modifier.width(200.dp),
            )

            if (legacyCostDescription != null) {
                Text(
                    text = stringResource(Res.string.price_help_legacy, legacyCostDescription),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
