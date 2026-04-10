package com.app.community.feature.activity.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.app.community.core.ui.components.AgoraButton
import com.app.community.core.ui.components.AgoraButtonVariant
import com.app.community.core.ui.components.AgoraDatePickerField
import com.app.community.core.ui.components.AgoraDurationPickerField
import com.app.community.core.ui.components.AgoraTimePickerField
import com.app.community.core.ui.components.AgoraTopBar
import com.app.community.core.ui.components.LoadingScreen
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
data class EditActivityScreen(val activityId: String) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<EditActivityScreenModel> { parametersOf(activityId) }
        val state by screenModel.state.collectAsState()

        LaunchedEffect(state.status) {
            if (state.status is EditActivityStatus.Success) {
                navigator.pop()
            }
        }

        Scaffold(
            topBar = {
                AgoraTopBar(
                    title = { Text(stringResource(Res.string.edit_title)) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back_cd))
                        }
                    },
                )
            },
        ) { padding ->
            if (state.isLoading) {
                LoadingScreen(Modifier.padding(padding))
            } else {
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

                    OutlinedTextField(
                        value = state.name,
                        onValueChange = screenModel::onNameChange,
                        label = { Text(stringResource(Res.string.label_name_required)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = state.description,
                        onValueChange = screenModel::onDescriptionChange,
                        label = { Text(stringResource(Res.string.label_description)) },
                        placeholder = { Text(stringResource(Res.string.placeholder_markdown)) },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )

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

                    AgoraDurationPickerField(
                        hours = state.durationHours,
                        minutes = state.durationMinutes,
                        onDurationSelected = screenModel::onDurationSelected,
                        label = stringResource(Res.string.label_duration),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = state.locationName,
                        onValueChange = screenModel::onLocationNameChange,
                        label = { Text(stringResource(Res.string.label_location)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = state.costDescription,
                        onValueChange = screenModel::onCostDescriptionChange,
                        label = { Text(stringResource(Res.string.label_cost)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (state.status is EditActivityStatus.Error) {
                        Text(
                            text = (state.status as EditActivityStatus.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Spacer(Modifier.height(AgoraSpacing.lg))

                    AgoraButton(
                        text = stringResource(Res.string.edit_save),
                        onClick = screenModel::save,
                        variant = AgoraButtonVariant.Primary,
                        enabled = state.status !is EditActivityStatus.Saving,
                        isLoading = state.status is EditActivityStatus.Saving,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(AgoraSpacing.xxl))
                }
            }
        }
    }
}
