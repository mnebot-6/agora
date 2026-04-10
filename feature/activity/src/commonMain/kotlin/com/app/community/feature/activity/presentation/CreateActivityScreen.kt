package com.app.community.feature.activity.presentation

import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.app.community.core.ui.components.AgoraButton
import com.app.community.core.ui.components.AgoraButtonVariant
import com.app.community.core.ui.components.AgoraDatePickerField
import com.app.community.core.ui.components.AgoraDurationPickerField
import com.app.community.core.ui.components.AgoraTimePickerField
import com.app.community.core.ui.components.AgoraTopBar
import com.app.community.core.ui.components.FriezeBandHeader
import com.app.community.core.ui.components.DentilDivider
import com.app.community.core.ui.components.MarbleCard
import com.app.community.core.ui.theme.AgoraElevation
import com.app.community.core.ui.theme.AgoraSpacing
import com.app.community.core.ui.theme.agoraColors
import agora.feature.activity.generated.resources.Res
import agora.feature.activity.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import org.koin.core.parameter.parametersOf

@Serializable
data class CreateActivityScreen(val communityId: String) : Screen {

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
                AgoraTopBar(
                    title = { Text(stringResource(Res.string.create_title)) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back_cd))
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.agoraColors.parchment)
                    .padding(AgoraSpacing.screenHorizontal)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AgoraSpacing.md),
            ) {
                Spacer(Modifier.height(AgoraSpacing.xs))

                // Name
                OutlinedTextField(
                    value = state.name,
                    onValueChange = screenModel::onNameChange,
                    label = { Text(stringResource(Res.string.label_name_required)) },
                    placeholder = { Text(stringResource(Res.string.create_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Description (markdown)
                OutlinedTextField(
                    value = state.description,
                    onValueChange = screenModel::onDescriptionChange,
                    label = { Text(stringResource(Res.string.label_description)) },
                    placeholder = { Text(stringResource(Res.string.placeholder_markdown)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Date and Time pickers
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.sm)) {
                    val dateDisplayText = state.dateMillis?.let { millis ->
                        val d = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
                        "${d.dayOfMonth.toString().padStart(2, '0')}/${d.monthNumber.toString().padStart(2, '0')}/${d.year}"
                    } ?: ""
                    AgoraDatePickerField(
                        selectedDateMillis = state.dateMillis,
                        onDateSelected = screenModel::onDateSelected,
                        label = stringResource(Res.string.label_date_required),
                        displayText = dateDisplayText,
                        placeholder = stringResource(Res.string.create_date_placeholder),
                        modifier = Modifier.weight(1f),
                    )
                    AgoraTimePickerField(
                        hour = state.timeHour,
                        minute = state.timeMinute,
                        onTimeSelected = screenModel::onTimeSelected,
                        label = stringResource(Res.string.label_time_required),
                        modifier = Modifier.weight(1f),
                    )
                }

                // Duration picker
                AgoraDurationPickerField(
                    hours = state.durationHours,
                    minutes = state.durationMinutes,
                    onDurationSelected = screenModel::onDurationSelected,
                    label = stringResource(Res.string.label_duration),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Location
                OutlinedTextField(
                    value = state.locationName,
                    onValueChange = screenModel::onLocationNameChange,
                    label = { Text(stringResource(Res.string.label_location)) },
                    placeholder = { Text(stringResource(Res.string.create_location_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Cost
                OutlinedTextField(
                    value = state.costDescription,
                    onValueChange = screenModel::onCostDescriptionChange,
                    label = { Text(stringResource(Res.string.label_cost)) },
                    placeholder = { Text(stringResource(Res.string.create_cost_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Slot mode selector
                DentilDivider()

                FriezeBandHeader(title = stringResource(Res.string.slot_type_header))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.sm),
                ) {
                    SlotModeCard(
                        label = stringResource(Res.string.slot_mode_unlimited),
                        isSelected = state.slotMode == SlotMode.UNLIMITED,
                        onClick = { screenModel.onSlotModeChange(SlotMode.UNLIMITED) },
                        modifier = Modifier.weight(1f),
                    )
                    SlotModeCard(
                        label = stringResource(Res.string.slot_mode_limited),
                        isSelected = state.slotMode == SlotMode.LIMITED,
                        onClick = { screenModel.onSlotModeChange(SlotMode.LIMITED) },
                        modifier = Modifier.weight(1f),
                    )
                    SlotModeCard(
                        label = stringResource(Res.string.slot_mode_positions),
                        isSelected = state.slotMode == SlotMode.LIMITED_WITH_POSITIONS,
                        onClick = { screenModel.onSlotModeChange(SlotMode.LIMITED_WITH_POSITIONS) },
                        modifier = Modifier.weight(1f),
                    )
                }

                // Limited mode: max slots
                if (state.slotMode == SlotMode.LIMITED) {
                    OutlinedTextField(
                        value = state.maxSlots,
                        onValueChange = screenModel::onMaxSlotsChange,
                        label = { Text(stringResource(Res.string.create_max_slots_label)) },
                        placeholder = { Text(stringResource(Res.string.create_max_slots_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.width(160.dp),
                    )
                }

                // Position mode: templates + configurator
                if (state.slotMode == SlotMode.LIMITED_WITH_POSITIONS) {
                    TemplateSection(state = state, screenModel = screenModel)
                    PositionConfigurator(state = state, screenModel = screenModel)
                }

                // Save template dialog
                if (state.showSaveTemplateDialog) {
                    AlertDialog(
                        onDismissRequest = screenModel::hideSaveTemplateDialog,
                        title = { Text(stringResource(Res.string.create_save_template_title)) },
                        text = {
                            OutlinedTextField(
                                value = state.saveTemplateName,
                                onValueChange = screenModel::onSaveTemplateNameChange,
                                label = { Text(stringResource(Res.string.create_template_name_label)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = screenModel::saveAsTemplate,
                                enabled = state.saveTemplateName.isNotBlank(),
                            ) {
                                Text(stringResource(Res.string.create_save_template_confirm))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = screenModel::hideSaveTemplateDialog) {
                                Text(stringResource(Res.string.label_cancel))
                            }
                        },
                    )
                }

                // Error
                if (state.status is CreateActivityStatus.Error) {
                    Text(
                        text = (state.status as CreateActivityStatus.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(AgoraSpacing.lg))

                // Create button
                AgoraButton(
                    text = stringResource(Res.string.create_button),
                    onClick = screenModel::create,
                    variant = AgoraButtonVariant.Primary,
                    enabled = state.status !is CreateActivityStatus.Loading,
                    isLoading = state.status is CreateActivityStatus.Loading,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(AgoraSpacing.xxl))
            }
        }
    }
}

@Composable
private fun SlotModeCard(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MarbleCard(
        modifier = modifier,
        elevation = if (isSelected) AgoraElevation.standard else AgoraElevation.none,
        borderColor = if (isSelected) MaterialTheme.agoraColors.gildedVolute else MaterialTheme.colorScheme.outlineVariant,
        containerColor = if (isSelected) MaterialTheme.agoraColors.parchment else MaterialTheme.colorScheme.surface,
        onClick = onClick,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.agoraColors.onParchment else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AgoraSpacing.md, vertical = AgoraSpacing.sm),
        )
    }
}

// ============================================================================
// Template Section
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateSection(
    state: CreateActivityUiState,
    screenModel: CreateActivityScreenModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AgoraSpacing.sm)) {
        FriezeBandHeader(title = stringResource(Res.string.create_templates_header))
        Text(
            stringResource(Res.string.create_templates_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(AgoraSpacing.xs),
        ) {
            state.templates.forEach { template ->
                val isDefault = template.userId == null
                FilterChip(
                    selected = false,
                    onClick = { screenModel.applyTemplate(template) },
                    label = { Text(template.name, style = MaterialTheme.typography.labelSmall) },
                    trailingIcon = if (!isDefault) {
                        {
                            IconButton(
                                onClick = { screenModel.deleteTemplate(template.id) },
                                modifier = Modifier.size(18.dp),
                            ) {
                                Icon(Icons.Default.Close, stringResource(Res.string.label_delete), modifier = Modifier.size(14.dp))
                            }
                        }
                    } else null,
                )
            }
        }
        if (state.totalSlotCount > 0) {
            AgoraButton(
                text = stringResource(Res.string.create_save_template),
                onClick = screenModel::showSaveTemplateDialog,
                variant = AgoraButtonVariant.Tertiary,
                modifier = Modifier.fillMaxWidth(),
            )
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
    Column(verticalArrangement = Arrangement.spacedBy(AgoraSpacing.lg)) {
        DentilDivider()

        // Step 1: Define positions
        FriezeBandHeader(title = stringResource(Res.string.create_step1_title))
        Text(
            stringResource(Res.string.create_step1_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )

        state.positions.forEach { position ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.sm),
            ) {
                OutlinedTextField(
                    value = position.name,
                    onValueChange = { screenModel.updatePositionName(position.id, it) },
                    placeholder = { Text(stringResource(Res.string.create_position_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                if (state.positions.size > 1) {
                    IconButton(onClick = { screenModel.removePosition(position.id) }) {
                        Icon(Icons.Default.Close, stringResource(Res.string.create_delete_position_cd), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        TextButton(onClick = screenModel::addPosition) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(AgoraSpacing.xs))
            Text(stringResource(Res.string.create_add_position))
        }

        DentilDivider()

        // Step 2: Define groups and slots
        FriezeBandHeader(title = stringResource(Res.string.create_step2_title))
        Text(
            stringResource(Res.string.create_step2_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )

        val validPositions = state.positions.filter { it.name.isNotBlank() }

        state.groups.forEach { group ->
            MarbleCard(elevation = AgoraElevation.subtle) {
                Column(
                    Modifier.padding(AgoraSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(AgoraSpacing.sm),
                ) {
                    // Group header
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = group.name,
                            onValueChange = { screenModel.updateGroupName(group.id, it) },
                            label = { Text(stringResource(Res.string.create_group_name_label)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        if (state.groups.size > 1) {
                            IconButton(onClick = { screenModel.removeGroup(group.id) }) {
                                Icon(Icons.Default.Delete, stringResource(Res.string.create_delete_group_cd), modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    // Slots in this group
                    group.slots.forEachIndexed { index, slot ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = AgoraSpacing.sm),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(Res.string.create_slot_index, index + 1),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                IconButton(
                                    onClick = { screenModel.removeSlotFromGroup(group.id, slot.id) },
                                ) {
                                    Icon(Icons.Default.Close, stringResource(Res.string.create_delete_slot_cd), modifier = Modifier.size(16.dp))
                                }
                            }

                            // Position chips for this slot
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.xs),
                                verticalArrangement = Arrangement.spacedBy(AgoraSpacing.xs),
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
                    AgoraButton(
                        text = stringResource(Res.string.create_add_slot),
                        onClick = { screenModel.addSlotToGroup(group.id) },
                        variant = AgoraButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        TextButton(onClick = screenModel::addGroup) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(AgoraSpacing.xs))
            Text(stringResource(Res.string.create_add_group))
        }

        // Preview
        if (state.totalSlotCount > 0) {
            DentilDivider()
            FriezeBandHeader(title = stringResource(Res.string.create_preview_title))
            Text(
                stringResource(Res.string.create_preview_summary, state.totalSlotCount, state.groups.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )

            val groupFallback = stringResource(Res.string.create_group_fallback)
            val noPositionLabel = stringResource(Res.string.no_position)
            state.groups.forEach { group ->
                if (group.slots.isNotEmpty()) {
                    Text(
                        group.name.ifBlank { groupFallback },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    group.slots.forEachIndexed { index, slot ->
                        val posNames = slot.acceptedPositionIds
                            .mapNotNull { pid -> validPositions.find { it.id == pid }?.name }
                            .joinToString(" / ")
                        Text(
                            "  ${index + 1}. ${posNames.ifEmpty { noPositionLabel }}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
