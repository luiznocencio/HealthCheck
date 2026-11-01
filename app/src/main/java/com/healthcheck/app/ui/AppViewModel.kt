package com.healthcheck.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthcheck.app.alarm.AlarmScheduler
import com.healthcheck.app.alarm.AlarmService
import com.healthcheck.app.alarm.DoseExtras
import com.healthcheck.app.alarm.DoseLogger
import com.healthcheck.app.alarm.Notifications
import com.healthcheck.app.data.CareLink
import com.healthcheck.app.data.DoseItem
import com.healthcheck.app.data.LocalStore
import com.healthcheck.app.data.Medication
import com.healthcheck.app.data.Profile
import com.healthcheck.app.data.Repository
import com.healthcheck.app.data.Supabase
import com.healthcheck.app.data.buildDoses
import com.healthcheck.app.sync.SyncWorker
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthState { Loading, LoggedOut, LoggedIn }

data class UiState(
    val auth: AuthState = AuthState.Loading,
    val busy: Boolean = false,
    val me: Profile? = null,
    val myMeds: List<Medication> = emptyList(),
    val myDoses: List<DoseItem> = emptyList(),
    val links: List<CareLink> = emptyList(),
    val people: Map<String, Profile> = emptyMap(),
    val message: String? = null,
)

data class PersonState(
    val personId: String = "",
    val loading: Boolean = false,
    val meds: List<Medication> = emptyList(),
    val doses: List<DoseItem> = emptyList(),
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = LocalStore(app)

    private val _state = MutableStateFlow(UiState(myMeds = store.myMedications))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _person = MutableStateFlow(PersonState())
    val person: StateFlow<PersonState> = _person.asStateFlow()

    val configured = Supabase.isConfigured

    init {
        if (configured) {
            viewModelScope.launch {
                Supabase.client.auth.sessionStatus.collect { status ->
                    when (status) {
                        is SessionStatus.Authenticated -> {
                            if (_state.value.auth != AuthState.LoggedIn) {
                                _state.update { it.copy(auth = AuthState.LoggedIn) }
                                refresh()
                            }
                        }
                        is SessionStatus.NotAuthenticated -> _state.update { UiState(auth = AuthState.LoggedOut) }
                        else -> Unit
                    }
                }
            }
        } else {
            _state.update { it.copy(auth = AuthState.LoggedOut) }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun say(msg: String) = _state.update { it.copy(message = msg) }

    private fun friendly(e: Throwable): String {
        val raw = e.message ?: e.toString()
        return when {
            raw.contains("Invalid login credentials", true) -> "E-mail ou senha incorretos."
            raw.contains("Email not confirmed", true) -> "Confirme seu e-mail antes de entrar."
            raw.contains("already registered", true) -> "Este e-mail já está cadastrado."
            raw.contains("Password should be", true) -> "A senha precisa ter pelo menos 6 caracteres."
            raw.contains("Nenhum usuário encontrado", true) -> "Nenhum usuário encontrado com este e-mail. A pessoa precisa criar uma conta no app primeiro."
            raw.contains("si mesmo", true) -> "Você não pode enviar convite para si mesmo."
            raw.contains("Unable to resolve host", true) || raw.contains("timeout", true) ->
                "Sem conexão com a internet."
            else -> "Erro: " + (raw.lineSequence().firstOrNull()?.take(300) ?: e.javaClass.simpleName)
        }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                android.util.Log.e("HealthCheck", "Erro", e)
                say(friendly(e))
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    // ---------------- Autenticação ----------------

    fun signIn(email: String, password: String) = launchBusy {
        Repository.signIn(email, password)
    }

    fun signInWithGoogle() = launchBusy {
        Repository.signInWithGoogle()
    }

    fun signUp(name: String, email: String, password: String) = launchBusy {
        Repository.signUp(name, email, password)
        if (Repository.currentUserId() == null) {
            say("Conta criada! Confirme o e-mail que enviamos e depois entre.")
        }
    }

    fun signOut() = launchBusy {
        val ctx = getApplication<Application>()
        store.myMedications = emptyList()
        AlarmScheduler.rescheduleAll(ctx)
        store.clear()
        SyncWorker.cancelAll(ctx)
        Repository.signOut()
    }

    // ---------------- Dados ----------------

    fun refresh() = launchBusy {
        val ctx = getApplication<Application>()
        val me = Repository.myProfile()
        // Sincroniza meus remédios (cache local + alarmes)
        SyncWorker.sync(ctx)
        SyncWorker.schedulePeriodic(ctx)
        val meds = store.myMedications
        val logs = me?.let { Repository.logsForDay(it.id) } ?: emptyList()
        val pendingLogs = store.pendingLogs
        val links = Repository.links()
        val ids = links.flatMap { listOf(it.caregiverId, it.patientId) }.toSet()
        val people = Repository.profiles(ids).associateBy { it.id }
        _state.update {
            it.copy(
                me = me,
                myMeds = meds,
                myDoses = buildDoses(meds, logs + pendingLogs),
                links = links,
                people = people,
            )
        }
    }

    fun markDose(item: DoseItem, status: String) = launchBusy {
        val ctx = getApplication<Application>()
        val log = Repository.newLog(item.medication, item.scheduledAt, status)
        DoseLogger.record(ctx, log)
        val extras = DoseExtras.from(item.medication, item.time.toString(), item.scheduledAt.toEpochMilli())
        AlarmService.dismiss(ctx, extras)
        AlarmScheduler.cancelTimeout(ctx, extras)
        Notifications.cancel(ctx, extras.notificationId)
        _state.update { s ->
            s.copy(myDoses = s.myDoses.map {
                if (it.medication.id == item.medication.id && it.scheduledAt == item.scheduledAt) it.copy(status = status) else it
            })
        }
    }

    // ---------------- Vínculos ----------------

    fun invite(email: String) = launchBusy {
        Repository.invite(email)
        say("Convite enviado! Quando a pessoa aceitar, você poderá cadastrar remédios para ela.")
        refreshLinks()
    }

    fun accept(link: CareLink) = launchBusy {
        Repository.acceptLink(link.id)
        refreshLinks()
    }

    fun removeLink(link: CareLink) = launchBusy {
        Repository.removeLink(link.id)
        refreshLinks()
    }

    private suspend fun refreshLinks() {
        val links = Repository.links()
        val ids = links.flatMap { listOf(it.caregiverId, it.patientId) }.toSet()
        val people = Repository.profiles(ids).associateBy { it.id }
        _state.update { it.copy(links = links, people = people) }
    }

    // ---------------- Remédios de outra pessoa ----------------

    fun loadPerson(personId: String) {
        _person.value = PersonState(personId = personId, loading = true)
        viewModelScope.launch {
            try {
                val meds = Repository.medications(personId)
                val logs = Repository.logsForDay(personId)
                _person.value = PersonState(personId, false, meds, buildDoses(meds, logs))
            } catch (e: Exception) {
                _person.update { it.copy(loading = false) }
                say(friendly(e))
            }
        }
    }

    fun medicationsOf(ownerId: String): List<Medication> =
        if (ownerId == _state.value.me?.id) _state.value.myMeds else _person.value.meds

    fun saveMedication(med: Medication, isNew: Boolean, onDone: () -> Unit) = launchBusy {
        val userId = Repository.currentUserId() ?: error("Sessão expirada. Saia e entre novamente.")
        val toSave = if (isNew) med.copy(createdBy = userId) else med
        Repository.saveMedication(toSave, isNew)
        afterChange(med.ownerId)
        say(if (isNew) "Remédio cadastrado!" else "Remédio atualizado!")
        onDone()
    }

    fun deleteMedication(med: Medication, onDone: () -> Unit) = launchBusy {
        Repository.deleteMedication(med.id)
        afterChange(med.ownerId)
        say("Remédio removido.")
        onDone()
    }

    private suspend fun afterChange(ownerId: String) {
        if (ownerId == Repository.currentUserId()) {
            SyncWorker.sync(getApplication())
            val meds = store.myMedications
            val logs = Repository.logsForDay(ownerId)
            _state.update { it.copy(myMeds = meds, myDoses = buildDoses(meds, logs + store.pendingLogs)) }
        } else {
            val meds = Repository.medications(ownerId)
            val logs = Repository.logsForDay(ownerId)
            _person.value = PersonState(ownerId, false, meds, buildDoses(meds, logs))
        }
    }
}
