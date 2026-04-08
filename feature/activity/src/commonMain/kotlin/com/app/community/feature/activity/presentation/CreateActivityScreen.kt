package com.app.community.feature.activity.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import com.app.community.core.ui.components.GreekKeyDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.app.community.core.model.SlotMode
import kotlinx.serialization.Serializable
import org.koin.core.parameter.parametersOf

@Serializable
data class CreateActivityScreen(val communityId: String) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<CreateActivityScreenModel> { parametersOf(communityId) }
        val state by screenModel.state.collectAsState()

        LaunchedEffect(state.status) {
            if (state.status is CreateActivityStatus.Success) {
                navigator.pop()
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Nueva Actividad") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Name
                OutlinedTextField(
                    value = state.name,
                    onValueChange = screenModel::onNameChange,
                    label = { Text("Nombre *") },
                    placeholder = { Text("Ej: Voley Mixto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Description (markdown)
                OutlinedTextField(
                    value = state.description,
                    onValueChange = screenModel::onDescriptionChange,
                    label = { Text("Descripción") },
                    placeholder = { Text("Soporta formato Markdown") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Date and Time row
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.date,
                        onValueChange = screenModel::onDateChange,
                        label = { Text("Fecha *") },
                        placeholder = { Text("DD/MM/YYYY") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.time,
                        onValueChange = screenModel::onTimeChange,
                        label = { Text("Hora *") },
                        placeholder = { Text("HH:MM") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                // Duration
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.durationHours.toString(),
                        onValueChange = { screenModel.onDurationHoursChange(it.toIntOrNull() ?: 0) },
                        label = { Text("Horas") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.durationMinutes.toString(),
                        onValueChange = { screenModel.onDurationMinutesChange(it.toIntOrNull() ?: 0) },
                        label = { Text("Minutos") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                // Location
                OutlinedTextField(
                    value = state.locationName,
                    onValueChange = screenModel::onLocationNameChange,
                    label = { Text("Lugar") },
                    placeholder = { Text("Ej: Pabellón de la Fuensanta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Cost
                OutlinedTextField(
                    value = state.costDescription,
                    onValueChange = screenModel::onCostDescriptionChange,
                    label = { Text("Coste") },
                    placeholder = { Text("Ej: Bizum de 6.5€ por persona") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Slot mode selector
                Spacer(Modifier.height(8.dp))
                Text("Tipo de plazas", style = MaterialTheme.typography.titleSmall)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.slotMode == SlotMode.UNLIMITED,
                        onClick = { screenModel.onSlotModeChange(SlotMode.UNLIMITED) },
                        label = { Text("Sin límite") },
                    )
                    FilterChip(
                        selected = state.slotMode == SlotMode.LIMITED,
                        onClick = { screenModel.onSlotModeChange(SlotMode.LIMITED) },
                        label = { Text("Limitado") },
                    )
                    FilterChip(
                        selected = state.slotMode == SlotMode.LIMITED_WITH_POSITIONS,
                        onClick = { screenModel.onSlotModeChange(SlotMode.LIMITED_WITH_POSITIONS) },
                        label = { Text("Con posiciones") },
                    )
                }

                // Limited mode: max slots
                if (state.slotMode == SlotMode.LIMITED) {
                    OutlinedTextField(
                        value = state.maxSlots,
                        onValueChange = screenModel::onMaxSlotsChange,
                        label = { Text("Número de plazas *") },
                        placeholder = { Text("Ej: 12") },
                        singleLine = true,
                        modifier = Modifier.width(160.dp),
                    )
                }

                // Position mode: full configurator
                if (state.slotMode == SlotMode.LIMITED_WITH_POSITIONS) {
                    PositionConfigurator(state = state, screenModel = screenModel)
                }

                // Error
                if (state.status is CreateActivityStatus.Error) {
                    Text(
                        text = (state.status as CreateActivityStatus.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Create button
                Button(
                    onClick = screenModel::create,
                    enabled = state.status !is CreateActivityStatus.Loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.status is CreateActivityStatus.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp).width(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Crear Actividad")
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ============================================================================
// Position Configurator (multi-step)
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PositionConfigurator(
    state: CreateActivityUiState,
    screenModel: CreateActivityScreenModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GreekKeyDivider()

        // Step 1: Define positions
        Text("1. Define posiciones", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "Las posiciones disponibles (ej: Central, Libero, Colocador...)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )

        state.positions.forEach { position ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = position.name,
                    onValueChange = { screenModel.updatePositionName(position.id, it) },
                    placeholder = { Text("Nombre posición") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                if (state.positions.size > 1) {
                    IconButton(onClick = { screenModel.removePosition(position.id) }) {
                        Icon(Icons.Default.Close, "Eliminar", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        TextButton(onClick = screenModel::addPosition) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Añadir posición")
        }

        HorizontalDivider()

        // Step 2: Define groups and slots
        Text("2. Define grupos y huecos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "Cada grupo tiene huecos. Cada hueco acepta una o más posiciones.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )

        val validPositions = state.positions.filter { it.name.isNotBlank() }

        state.groups.forEach { group ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Group header
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = group.name,
                            onValueChange = { screenModel.updateGroupName(group.id, it) },
                            label = { Text("Nombre grupo") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        if (state.groups.size > 1) {
                            IconButton(onClick = { screenModel.removeGroup(group.id) }) {
                                Icon(Icons.Default.Delete, "Eliminar grupo", modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    // Slots in this group
                    group.slots.forEachIndexed { index, slot ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Hueco ${index + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                IconButton(
                                    onClick = { screenModel.removeSlotFromGroup(group.id, slot.id) },
                                ) {
                                    Icon(Icons.Default.Close, "Eliminar hueco", modifier = Modifier.size(16.dp))
                                }
                            }

                            // Position chips for this slot
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                validPositions.forEach { position ->
                                    FilterChip(
                                        selected = position.id in slot.acceptedPositionIds,
                                        onClick = {
                                            screenModel.toggleSlotPosition(group.id, slot.id, position.id)
                                        },
                                        label = { Text(position.name, style = MaterialTheme.typography.labelSmall) },
                                    )
                                }
                            }
                        }
                    }

                    // Add slot button
                    OutlinedButton(
                        onClick = { screenModel.addSlotToGroup(group.id) },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Añadir hueco")
                    }
                }
            }
        }

        TextButton(onClick = screenModel::addGroup) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Añadir grupo")
        }

        // Preview
        if (state.totalSlotCount > 0) {
            HorizontalDivider()
            Text("Vista previa", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "${state.totalSlotCount} huecos en ${state.groups.size} grupo(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )

            state.groups.forEach { group ->
                if (group.slots.isNotEmpty()) {
                    Text(
                        group.name.ifBlank { "Grupo" },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    group.slots.forEachIndexed { index, slot ->
                        val posNames = slot.acceptedPositionIds
                            .mapNotNull { pid -> validPositions.find { it.id == pid }?.name }
                            .joinToString(" / ")
                        Text(
                            "  ${index + 1}. ${posNames.ifEmpty { "Sin posición" }}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
