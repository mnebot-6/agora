package com.app.community.feature.activity.presentation

import agora.feature.activity.generated.resources.Res
import agora.feature.activity.generated.resources.price_agora
import agora.feature.activity.generated.resources.price_agora_unavailable
import agora.feature.activity.generated.resources.price_external
import agora.feature.activity.generated.resources.price_free
import agora.feature.activity.generated.resources.price_how_to_pay_label
import agora.feature.activity.generated.resources.price_how_to_pay_placeholder
import agora.feature.activity.generated.resources.price_invalid
import agora.feature.activity.generated.resources.price_label
import agora.feature.activity.generated.resources.price_mode_locked
import agora.feature.activity.generated.resources.price_per_slot_label
import agora.feature.activity.generated.resources.price_placeholder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.app.community.core.model.PaymentMode
import com.app.community.core.model.parseEurosToCents
import com.app.community.core.ui.theme.AgoraSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * Coste de la actividad: sin pago, pago externo o pago gestionado por Agora.
 *
 * "Pago con Agora" solo aparece si la comunidad tiene cobrador dado de alta. Ensenarla
 * apagada seria peor: el admin la pulsaria, no pasaria nada y no sabria por que.
 *
 * Lo comparten crear y editar para que las dos pantallas validen igual. En editar el modo
 * va en solo lectura (`readOnly`): se decide al crear y no cambia, porque cambiarlo con
 * pagos hechos dejaria cobros de Stripe vivos en una actividad que ya no los usa.
 */
@Composable
fun ActivityPriceField(
    mode: PaymentMode,
    priceInput: String,
    howToPayInput: String,
    canUseAgoraPayments: Boolean,
    onModeChange: (PaymentMode) -> Unit,
    onPriceInputChange: (String) -> Unit,
    onHowToPayInputChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AgoraSpacing.sm),
    ) {
        Text(
            text = stringResource(Res.string.price_label),
            style = MaterialTheme.typography.labelLarge,
        )

        if (readOnly) {
            Text(
                text = when (mode) {
                    PaymentMode.FREE -> stringResource(Res.string.price_free)
                    PaymentMode.EXTERNAL -> stringResource(Res.string.price_external)
                    PaymentMode.AGORA -> stringResource(Res.string.price_agora)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(Res.string.price_mode_locked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.sm),
            ) {
                SlotModeCard(
                    label = stringResource(Res.string.price_free),
                    isSelected = mode == PaymentMode.FREE,
                    onClick = { onModeChange(PaymentMode.FREE) },
                    modifier = Modifier.weight(1f),
                )
                SlotModeCard(
                    label = stringResource(Res.string.price_external),
                    isSelected = mode == PaymentMode.EXTERNAL,
                    onClick = { onModeChange(PaymentMode.EXTERNAL) },
                    modifier = Modifier.weight(1f),
                )
                if (canUseAgoraPayments) {
                    SlotModeCard(
                        label = stringResource(Res.string.price_agora),
                        isSelected = mode == PaymentMode.AGORA,
                        onClick = { onModeChange(PaymentMode.AGORA) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (!canUseAgoraPayments) {
                Text(
                    text = stringResource(Res.string.price_agora_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (mode != PaymentMode.FREE) {
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
        }

        // Solo en externo: en 'agora' el usuario paga en Checkout y no hay nada que
        // explicarle, y en 'free' no hay nada que cobrar.
        if (mode == PaymentMode.EXTERNAL) {
            OutlinedTextField(
                value = howToPayInput,
                onValueChange = onHowToPayInputChange,
                label = { Text(stringResource(Res.string.price_how_to_pay_label)) },
                placeholder = { Text(stringResource(Res.string.price_how_to_pay_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
