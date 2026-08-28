package com.app.community.feature.activity.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.common.RefreshBus
import com.app.community.core.data.repository.ActivityRepository
import com.app.community.core.model.PaymentMode
import com.app.community.core.model.formatEuros
import com.app.community.core.model.parseEurosToCents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

data class EditActivityUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val description: String = "",
    val dateMillis: Long? = null,
    val timeHour: Int = 20,
    val timeMinute: Int = 0,
    val durationHours: Int = 0,
    val durationMinutes: Int = 0,
    val locationName: String = "",
    /** Solo lectura: el modo se fija al crear la actividad. */
    val paymentMode: PaymentMode = PaymentMode.FREE,
    val priceInput: String = "",
    val howToPayInput: String = "",
    val status: EditActivityStatus = EditActivityStatus.Idle,
)

sealed class EditActivityStatus {
    data object Idle : EditActivityStatus()
    data object Saving : EditActivityStatus()
    data object Success : EditActivityStatus()
    data class Error(val message: String) : EditActivityStatus()
}

class EditActivityScreenModel(
    private val activityId: String,
    private val activityRepository: ActivityRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(EditActivityUiState())
    val state: StateFlow<EditActivityUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        screenModelScope.launch {
            activityRepository.getActivity(activityId)
                .onSuccess { activity ->
                    val localDateTime = activity.datetime.toLocalDateTime(TimeZone.currentSystemDefault())
                    val dateMillis = LocalDate(localDateTime.year, localDateTime.monthNumber, localDateTime.dayOfMonth)
                        .atStartOfDayIn(TimeZone.UTC)
                        .toEpochMilliseconds()
                    _state.value = EditActivityUiState(
                        isLoading = false,
                        name = activity.name,
                        description = activity.description.orEmpty(),
                        dateMillis = dateMillis,
                        timeHour = localDateTime.hour,
                        timeMinute = localDateTime.minute,
                        durationHours = activity.durationMinutes / 60,
                        durationMinutes = activity.durationMinutes % 60,
                        locationName = activity.locationName.orEmpty(),
                        paymentMode = activity.paymentMode,
                        priceInput = activity.priceCents
                            ?.let { formatEuros(it).removeSuffix(" €") }
                            .orEmpty(),
                        howToPayInput = activity.costDescription.orEmpty(),
                    )
                }
                .onError { msg, _ ->
                    _state.update { it.copy(isLoading = false, status = EditActivityStatus.Error(msg)) }
                }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value) }
    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value) }
    fun onDateSelected(millis: Long?) = _state.update { it.copy(dateMillis = millis) }
    fun onTimeSelected(hour: Int, minute: Int) = _state.update { it.copy(timeHour = hour, timeMinute = minute) }
    fun onDurationSelected(hours: Int, minutes: Int) = _state.update { it.copy(durationHours = hours, durationMinutes = minutes) }
    fun onLocationNameChange(value: String) = _state.update { it.copy(locationName = value) }
    fun onHowToPayInputChange(value: String) = _state.update { it.copy(howToPayInput = value) }
    fun onPriceInputChange(value: String) = _state.update { it.copy(priceInput = value) }

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(status = EditActivityStatus.Error("El nombre es obligatorio")) }
            return
        }
        if (s.dateMillis == null) {
            _state.update { it.copy(status = EditActivityStatus.Error("Selecciona una fecha")) }
            return
        }

        val priceCents =
            if (s.paymentMode != PaymentMode.FREE) parseEurosToCents(s.priceInput) else null
        // Una actividad de pago no puede quedarse sin importe: la restriccion
        // activities_mode_matches_price lo rechazaria con un error ilegible.
        if (s.paymentMode != PaymentMode.FREE && priceCents == null) {
            _state.update { it.copy(status = EditActivityStatus.Error("Introduce un importe válido, por ejemplo 6,50")) }
            return
        }

        val datetime = buildDatetime(s.dateMillis, s.timeHour, s.timeMinute)
        val durationMinutes = s.durationHours * 60 + s.durationMinutes

        _state.update { it.copy(status = EditActivityStatus.Saving) }

        screenModelScope.launch {
            activityRepository.updateActivity(
                activityId = activityId,
                name = s.name,
                description = s.description.ifBlank { null },
                datetime = datetime,
                durationMinutes = durationMinutes,
                locationName = s.locationName.ifBlank { null },
                // Nunca se borra al editar. En 'external' este es el campo "como se
                // paga"; en 'free' puede ser el texto de coste antiguo de una actividad
                // anterior a Stripe, que el detalle sigue enseñando. Editar el nombre no
                // puede hacerlo desaparecer en silencio.
                costDescription = s.howToPayInput.ifBlank { null },
                priceCents = priceCents,
            )
                .onSuccess {
                    RefreshBus.emit(RefreshBus.ACTIVITY_DETAIL, RefreshBus.ACTIVITIES)
                    _state.update { it.copy(status = EditActivityStatus.Success) }
                }
                .onError { msg, _ ->
                    _state.update { it.copy(status = EditActivityStatus.Error(msg)) }
                }
        }
    }

    private fun buildDatetime(dateMillis: Long, timeHour: Int, timeMinute: Int): Instant {
        val utcDate = Instant.fromEpochMilliseconds(dateMillis)
            .toLocalDateTime(TimeZone.UTC).date
        val localDateTime = LocalDateTime(
            utcDate.year, utcDate.monthNumber, utcDate.dayOfMonth,
            timeHour, timeMinute,
        )
        return localDateTime.toInstant(TimeZone.currentSystemDefault())
    }
}
