package com.app.community.feature.activity.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.common.RefreshBus
import com.app.community.core.data.repository.ActivityRepository
import com.app.community.core.data.repository.AuthRepository
import com.app.community.core.data.repository.CommunityRepository
import com.app.community.core.data.repository.GuestRepository
import com.app.community.core.data.repository.PaymentError
import com.app.community.core.data.repository.PaymentRepository
import com.app.community.core.data.repository.ProfileRepository
import com.app.community.core.data.repository.SlotRepository
import com.app.community.core.model.Activity
import com.app.community.core.model.ActivityStatus
import com.app.community.core.model.CancellationBreakdown
import com.app.community.core.model.ManualDebt
import com.app.community.core.model.CommunityMember
import com.app.community.core.model.CommunityVisibility
import com.app.community.core.model.MemberRole
import com.app.community.core.model.PaymentMode
import com.app.community.core.model.Position
import com.app.community.core.model.Profile
import com.app.community.core.model.Slot
import com.app.community.core.model.SlotGroup
import com.app.community.core.model.SlotMode
import com.app.community.core.model.SlotPosition
import com.app.community.core.model.SlotStatus
import com.app.community.core.model.SubstituteEntry
import com.app.community.core.model.effectiveMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SlotWithProfile(
    val slot: Slot,
    val profile: Profile? = null,
    val positionIds: List<String> = emptyList(),
    val positionNames: List<String> = emptyList(),
)

data class GroupWithSlots(
    val group: SlotGroup,
    val slots: List<SlotWithProfile>,
)

sealed class ActivityDetailUiState {
    data object Loading : ActivityDetailUiState()
    data class Content(
        val activity: Activity,
        val slots: List<SlotWithProfile>,
        val groups: List<GroupWithSlots> = emptyList(),
        val positions: List<Position> = emptyList(),
        val substituteQueue: List<SubstituteEntry>,
        val currentUserId: String,
        val isAdmin: Boolean,
        val participantCount: Int = 0,
        val isUserJoined: Boolean = false,
        val isPublicCommunity: Boolean = false,
        val members: List<CommunityMember> = emptyList(),
    ) : ActivityDetailUiState()

    data class Error(val message: String) : ActivityDetailUiState()
}

class ActivityDetailScreenModel(
    private val activityId: String,
    private val activityRepository: ActivityRepository,
    private val slotRepository: SlotRepository,
    private val authRepository: AuthRepository,
    private val communityRepository: CommunityRepository,
    private val profileRepository: ProfileRepository,
    private val guestRepository: GuestRepository,
    private val paymentRepository: PaymentRepository,
) : ScreenModel {

    /** URL de Checkout pendiente de abrir en el navegador. Se consume una sola vez. */
    private val _checkoutUrl = MutableStateFlow<String?>(null)
    val checkoutUrl: StateFlow<String?> = _checkoutUrl.asStateFlow()

    fun consumeCheckoutUrl() { _checkoutUrl.value = null }

    private val _state = MutableStateFlow<ActivityDetailUiState>(ActivityDetailUiState.Loading)
    val state: StateFlow<ActivityDetailUiState> = _state.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    /** URL del link de invitado a compartir; la pantalla la consume y abre el share sheet. */
    private val _guestShareUrl = MutableStateFlow<String?>(null)
    val guestShareUrl: StateFlow<String?> = _guestShareUrl.asStateFlow()

    private var isPublicCommunity: Boolean = false

    /** Modo que se ejecuta de verdad: 'agora' degrada a externo si la comunidad no cobra. */
    private var effectiveMode: PaymentMode = PaymentMode.FREE

    private var members: List<CommunityMember> = emptyList()

    init {
        load()
        screenModelScope.launch {
            RefreshBus.events.collect { tag ->
                if (tag == RefreshBus.ACTIVITY_DETAIL) load()
            }
        }
    }

    fun load() {
        _state.value = ActivityDetailUiState.Loading
        screenModelScope.launch {
            val userId = authRepository.currentUserId() ?: run {
                _state.value = ActivityDetailUiState.Error("No autenticado")
                return@launch
            }

            val activityResult = activityRepository.getActivity(activityId)
            val activity = activityResult.getOrNull() ?: run {
                _state.value = ActivityDetailUiState.Error("No se pudo cargar la actividad")
                return@launch
            }

            // Check if user is admin
            val membersResult = communityRepository.getMembers(activity.communityId)
            members = membersResult.getOrNull() ?: emptyList()
            val isAdmin = members.any { it.userId == userId && it.role == MemberRole.ADMIN }

            // Comunidad pública → habilita compartir el link de la actividad
            val community = communityRepository.getCommunity(activity.communityId).getOrNull()
            isPublicCommunity = community?.visibility?.let { it != CommunityVisibility.PRIVATE } ?: false
            effectiveMode = activity.effectiveMode(community?.stripeChargesEnabled ?: false)

            loadSlots(activity, userId, isAdmin)
        }
    }

    private suspend fun loadSlots(activity: Activity, userId: String, isAdmin: Boolean) {
        val slotsResult = slotRepository.getSlots(activityId)
        val slots = slotsResult.getOrNull() ?: emptyList()
        val substituteQueue = slotRepository.getSubstituteQueue(activityId).getOrNull() ?: emptyList()

        when (activity.slotMode) {
            SlotMode.UNLIMITED -> {
                val slotsWithProfiles = loadProfiles(slots)
                _state.value = ActivityDetailUiState.Content(
                    activity = activity,
                    slots = slotsWithProfiles,
                    substituteQueue = substituteQueue,
                    currentUserId = userId,
                    isAdmin = isAdmin,
                    participantCount = slots.count { it.status != SlotStatus.AVAILABLE },
                    isUserJoined = slots.any { it.reservedBy == userId },
                    isPublicCommunity = isPublicCommunity,
                    members = members,
                )
            }

            SlotMode.LIMITED -> {
                val slotsWithProfiles = loadProfiles(slots)
                _state.value = ActivityDetailUiState.Content(
                    activity = activity,
                    slots = slotsWithProfiles,
                    substituteQueue = substituteQueue,
                    currentUserId = userId,
                    isAdmin = isAdmin,
                    participantCount = slots.count { it.status != SlotStatus.AVAILABLE },
                    isUserJoined = slots.any { it.reservedBy == userId },
                    isPublicCommunity = isPublicCommunity,
                    members = members,
                )
            }

            SlotMode.LIMITED_WITH_POSITIONS -> {
                val positions = slotRepository.getPositions(activityId).getOrNull() ?: emptyList()
                val groups = slotRepository.getSlotGroups(activityId).getOrNull() ?: emptyList()
                val slotIds = slots.map { it.id }
                val slotPositions = if (slotIds.isNotEmpty()) {
                    slotRepository.getSlotPositions(slotIds).getOrNull() ?: emptyList()
                } else emptyList()

                val positionMap = positions.associateBy { it.id }
                val slotPositionMap = slotPositions.groupBy { it.slotId }

                val slotsWithProfiles = loadProfilesWithPositions(slots, slotPositionMap, positionMap)

                val groupsWithSlots = groups.map { group ->
                    GroupWithSlots(
                        group = group,
                        slots = slotsWithProfiles.filter { it.slot.groupId == group.id },
                    )
                }

                _state.value = ActivityDetailUiState.Content(
                    activity = activity,
                    slots = slotsWithProfiles,
                    groups = groupsWithSlots,
                    positions = positions,
                    substituteQueue = substituteQueue,
                    currentUserId = userId,
                    isAdmin = isAdmin,
                    participantCount = slots.count { it.status != SlotStatus.AVAILABLE },
                    isUserJoined = slots.any { it.reservedBy == userId },
                    isPublicCommunity = isPublicCommunity,
                    members = members,
                )
            }
        }
    }

    private suspend fun loadProfiles(slots: List<Slot>): List<SlotWithProfile> {
        val userIds = slots.mapNotNull { it.reservedBy }.distinct()
        val profiles = userIds.mapNotNull { uid ->
            profileRepository.getProfile(uid).getOrNull()
        }
        val profileMap = profiles.associateBy { it.id }
        return slots.map { slot ->
            SlotWithProfile(slot, slot.reservedBy?.let { profileMap[it] })
        }
    }

    private suspend fun loadProfilesWithPositions(
        slots: List<Slot>,
        slotPositionMap: Map<String, List<SlotPosition>>,
        positionMap: Map<String, Position>,
    ): List<SlotWithProfile> {
        val userIds = slots.mapNotNull { it.reservedBy }.distinct()
        val profiles = userIds.mapNotNull { uid ->
            profileRepository.getProfile(uid).getOrNull()
        }
        val profileMap = profiles.associateBy { it.id }
        return slots.map { slot ->
            val slotPositionEntries = slotPositionMap[slot.id] ?: emptyList()
            val posIds = slotPositionEntries.map { it.positionId }
            val posNames = slotPositionEntries.mapNotNull { sp -> positionMap[sp.positionId]?.name }
            SlotWithProfile(
                slot = slot,
                profile = slot.reservedBy?.let { profileMap[it] },
                positionIds = posIds,
                positionNames = posNames,
            )
        }
    }

    /**
     * Reservar. Solo el modo agora sale a Stripe Checkout; en gratuitas y en pago externo
     * es instantaneo, exactamente igual que siempre. Ese camino no puede ralentizarse: es
     * la accion mas usada de la app.
     */
    fun reserveSlot(slotId: String) {
        // En externo no hay nada que cobrar por aqui: reservar es instantaneo, igual que
        // en las gratuitas. Salir a Checkout para que el servidor lo rechace era un viaje
        // de red de mas en la accion mas usada de la app.
        if (effectiveMode != PaymentMode.AGORA) {
            reserveFree(slotId)
            return
        }
        screenModelScope.launch {
            paymentRepository.createCheckout(slotId)
                .onSuccess { link -> _checkoutUrl.value = link.url }
                .onError { msg, _ ->
                    when (PaymentError.from(msg)) {
                        // El cobrador se ha caido entre que se cargo la pantalla y el
                        // clic. Se reserva como en externo y el admin cobra a mano.
                        PaymentError.PAYMENTS_NOT_ENABLED,
                        PaymentError.ACTIVITY_IS_FREE -> reserveFree(slotId)

                        PaymentError.SLOT_BEING_PAID ->
                            _actionMessage.value = "Alguien está pagando esta plaza ahora mismo"
                        PaymentError.SLOT_NOT_CLAIMABLE ->
                            _actionMessage.value = "La plaza ya no está disponible"
                        PaymentError.SLOT_OFFERED_TO_SOMEONE_ELSE ->
                            _actionMessage.value = "La plaza está reservada para un suplente"
                        PaymentError.QUEUE_PRIORITY ->
                            _actionMessage.value = "Hay alguien por delante en la cola"
                        PaymentError.NOT_A_MEMBER ->
                            _actionMessage.value = "No eres miembro de esta comunidad"
                        PaymentError.UNKNOWN ->
                            _actionMessage.value = "Error: $msg"
                    }
                }
        }
    }

    /** El suplente renuncia a la plaza apalabrada: pasa el turno al siguiente de la cola. */
    fun declineOffer(slotId: String) {
        screenModelScope.launch {
            slotRepository.declineSubstituteOffer(slotId)
                .onSuccess { ok ->
                    _actionMessage.value =
                        if (ok) "Has renunciado a la plaza" else "La oferta ya no está disponible"
                    load()
                }
                .onError { msg, _ -> _actionMessage.value = "Error: $msg" }
        }
    }

    private fun reserveFree(slotId: String) {
        screenModelScope.launch {
            slotRepository.reserveSlot(slotId)
                .onSuccess { success ->
                    if (success) {
                        _actionMessage.value = "Plaza reservada"
                        load()
                    } else {
                        _actionMessage.value = "La plaza ya no está disponible"
                        // Recargar reconcilia el modo efectivo, que solo se calcula en load().
                        // Si el servidor dice 'agora' y esta pantalla creia 'external', sin
                        // esto la siguiente pulsacion daria el mismo mensaje falso para
                        // siempre; con esto ya sale a Checkout.
                        load()
                    }
                }
                .onError { msg, _ ->
                    _actionMessage.value = "Error: $msg"
                }
        }
    }

    /** Vuelta del deep link: sincroniza contra Stripe y refresca. */
    fun syncPayment(paymentId: String) {
        screenModelScope.launch {
            paymentRepository.syncPayment(paymentId)
                .onSuccess { result ->
                    _actionMessage.value = when {
                        result.isSucceeded -> "Pago confirmado, plaza reservada"
                        result.isPending -> "Aún no hemos podido confirmar el pago. " +
                            "Si lo has completado, aparecerá en unos minutos."
                        else -> "El pago no se completó"
                    }
                    load()
                }
                .onError { msg, _ -> _actionMessage.value = "Error: $msg" }
        }
    }

    fun releaseSlot(slotId: String) {
        screenModelScope.launch {
            slotRepository.releaseSlot(slotId)
                .onSuccess { success ->
                    if (success) {
                        _actionMessage.value = "Plaza liberada"
                        load()
                    } else {
                        _actionMessage.value = "No se pudo liberar la plaza"
                    }
                }
                .onError { msg, _ ->
                    _actionMessage.value = "Error: $msg"
                }
        }
    }

    fun markSlotPaid(slotId: String) {
        screenModelScope.launch {
            slotRepository.markSlotPaid(slotId)
                .onSuccess { success ->
                    if (success) {
                        _actionMessage.value = "Marcado como pagado"
                        load()
                    } else {
                        _actionMessage.value = "No se pudo marcar como pagado"
                    }
                }
                .onError { msg, _ ->
                    _actionMessage.value = "Error: $msg"
                }
        }
    }

    fun unmarkSlotPaid(slotId: String) {
        screenModelScope.launch {
            slotRepository.unmarkSlotPaid(slotId)
                .onSuccess { success ->
                    _actionMessage.value =
                        if (success) "Pago desmarcado" else "Esta plaza no consta como pagada"
                    load()
                }
                .onError { msg, _ ->
                    // El servidor es la autoridad: la pantalla no sabe si una plaza se
                    // pago por Stripe sin cargar los pagos de cada una, asi que ofrece el
                    // boton y traduce el rechazo.
                    _actionMessage.value = if (msg.contains("paid_with_stripe")) {
                        "Esta plaza se pagó por Stripe: no se puede desmarcar"
                    } else {
                        "Error: $msg"
                    }
                }
        }
    }

    fun joinUnlimited() {
        screenModelScope.launch {
            // For unlimited mode, create a new slot and reserve it
            slotRepository.createSlots(activityId, 1)
                .onSuccess {
                    // Reload to get the new slot, then reserve it
                    val slots = slotRepository.getSlots(activityId).getOrNull()
                    if (slots == null) {
                        _actionMessage.value = "No hemos podido comprobar si te has apuntado"
                        load()
                        return@onSuccess
                    }
                    val availableSlot = slots.lastOrNull { it.isAvailable }
                    if (availableSlot != null) {
                        // Por reserveSlot del modelo, NO por el repositorio: en modo agora
                        // hay que salir a Checkout. Llamar al RPC a pelo era apuntarse
                        // gratis a una actividad de pago de aforo ilimitado, y el servidor
                        // ahora lo rechaza sin que el usuario vea nada.
                        reserveSlot(availableSlot.id)
                    } else {
                        _actionMessage.value = "No hemos podido apuntarte, inténtalo de nuevo"
                        load()
                    }
                }
                .onError { msg, _ ->
                    _actionMessage.value = "Error: $msg"
                }
        }
    }

    fun leaveUnlimited() {
        val currentState = _state.value as? ActivityDetailUiState.Content ?: return
        val userId = authRepository.currentUserId() ?: return
        val mySlot = currentState.slots.firstOrNull { it.slot.reservedBy == userId }
        if (mySlot != null) {
            releaseSlot(mySlot.slot.id)
        }
    }

    /** Los RPC levantan excepciones en ingles; esto es lo unico que ve el admin. */
    private fun assignErrorMessage(msg: String): String = when {
        msg.contains("already has a slot") -> "Esa persona ya tiene plaza en esta actividad"
        msg.contains("not a member") -> "Esa persona ya no es miembro de la comunidad"
        msg.contains("Only community admins") -> "No tienes permisos de administrador"
        msg.contains("Guest label cannot be empty") -> "Escribe un nombre"
        msg.contains("unlimited-capacity") -> "Esta actividad no es de aforo abierto"
        else -> "Error: $msg"
    }

    fun assignSlot(slotId: String, userId: String?, guestLabel: String?) {
        screenModelScope.launch {
            slotRepository.adminAssignSlot(slotId, userId, guestLabel)
                .onSuccess { assigned ->
                    _actionMessage.value =
                        if (assigned) "Persona apuntada" else "Esa plaza ya está ocupada"
                    load()
                }
                .onError { msg, _ ->
                    _actionMessage.value = assignErrorMessage(msg)
                }
        }
    }

    fun assignNewSlot(userId: String?, guestLabel: String?) {
        screenModelScope.launch {
            slotRepository.adminAssignNewSlot(activityId, userId, guestLabel)
                .onSuccess {
                    _actionMessage.value = "Persona apuntada"
                    load()
                }
                .onError { msg, _ ->
                    _actionMessage.value = assignErrorMessage(msg)
                }
        }
    }

    fun joinSubstituteQueue(positionId: String? = null) {
        screenModelScope.launch {
            slotRepository.joinSubstituteQueue(activityId, positionId)
                .onSuccess {
                    _actionMessage.value = "Te has apuntado como suplente"
                    load()
                }
                .onError { msg, _ ->
                    _actionMessage.value = "Error: $msg"
                }
        }
    }

    fun leaveSubstituteQueue(positionId: String? = null) {
        screenModelScope.launch {
            slotRepository.leaveSubstituteQueue(activityId, positionId)
                .onSuccess {
                    _actionMessage.value = "Has salido de la cola de suplentes"
                    load()
                }
                .onError { msg, _ ->
                    _actionMessage.value = "Error: $msg"
                }
        }
    }

    /** Desglose del dinero antes de confirmar. null = dialogo cerrado. */
    private val _cancellationPreview = MutableStateFlow<CancellationBreakdown?>(null)
    val cancellationPreview: StateFlow<CancellationBreakdown?> = _cancellationPreview.asStateFlow()

    /** Deudas a mano que quedan tras cancelar. El admin tiene que poder volver a verlas. */
    private val _manualDebts = MutableStateFlow<List<ManualDebt>>(emptyList())
    val manualDebts: StateFlow<List<ManualDebt>> = _manualDebts.asStateFlow()

    fun dismissCancellation() { _cancellationPreview.value = null }
    fun clearManualDebts() { _manualDebts.value = emptyList() }

    /** Primero se ensena el dinero que se va a mover, y solo despues se cancela. */
    fun askToArchive() {
        screenModelScope.launch {
            activityRepository.cancellationPreview(activityId)
                .onSuccess { _cancellationPreview.value = it }
                .onError { msg, _ -> _actionMessage.value = "Error: $msg" }
        }
    }

    fun archiveActivity() {
        screenModelScope.launch {
            activityRepository.cancelActivity(activityId)
                .onSuccess { breakdown ->
                    _cancellationPreview.value = null
                    // Se conservan para que el admin pueda consultarlas: si solo se
                    // ensenaran una vez, perderia la lista de a quien debe dinero.
                    _manualDebts.value = breakdown.manual
                    RefreshBus.emit(RefreshBus.ACTIVITIES, RefreshBus.COMMUNITY_DETAIL)
                    _actionMessage.value = if (breakdown.autoCount > 0) {
                        "Actividad cancelada. Se devolverán ${breakdown.autoCount} pagos."
                    } else {
                        "Actividad cancelada"
                    }
                    load()
                }
                .onError { msg, _ ->
                    _cancellationPreview.value = null
                    _actionMessage.value = "Error: $msg"
                }
        }
    }

    fun deleteActivity() {
        screenModelScope.launch {
            activityRepository.deleteActivity(activityId)
                .onSuccess {
                    RefreshBus.emit(RefreshBus.ACTIVITIES, RefreshBus.COMMUNITY_DETAIL)
                    _actionMessage.value = "Actividad eliminada"
                    _deleted.value = true
                }
                .onError { msg, _ ->
                    _actionMessage.value = "Error: $msg"
                }
        }
    }

    fun generateGuestLink() {
        screenModelScope.launch {
            guestRepository.generateLink(activityId)
                .onSuccess { url -> _guestShareUrl.value = url }
                .onError { msg, _ -> _actionMessage.value = "Error: $msg" }
        }
    }

    fun consumeGuestShareUrl() {
        _guestShareUrl.value = null
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }
}
