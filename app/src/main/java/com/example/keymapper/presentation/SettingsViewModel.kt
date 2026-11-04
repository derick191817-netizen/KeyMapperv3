package com.example.keymapper.presentation

import android.util.Log
import com.example.keymapper.data.AppSettings
import com.example.keymapper.data.AppProfile
import com.example.keymapper.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Presentation state holder for settings. The StateFlow remains responsive while writes are
 * serialized. A revision guard prevents an older DataStore emission from overwriting a newer
 * edit, and a failed latest write rolls the UI back to the last persisted snapshot.
 */
class SettingsViewModel(private val repository: SettingsRepository) : AutoCloseable {
    private data class WriteTask(
        val label: String,
        val action: suspend () -> Unit,
        val completion: CompletableDeferred<Unit>? = null,
        val revision: Long? = null,
        val snapshot: AppSettings? = null
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val writerJob = SupervisorJob()
    private val writerScope = CoroutineScope(writerJob + Dispatchers.IO)
    private val writeQueue = Channel<WriteTask>(
        capacity = Channel.UNLIMITED,
        onUndeliveredElement = { task ->
            task.completion?.cancel(CancellationException("Gravação descartada porque a fila foi encerrada"))
        }
    )
    private val mutableSettings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = mutableSettings.asStateFlow()

    private val requestedRevision = AtomicLong(0L)
    private val confirmedRevision = AtomicLong(0L)

    @Volatile
    private var requestedSettings = AppSettings()

    @Volatile
    private var lastPersistedSettings = AppSettings()

    init {
        scope.launch {
            repository.flow.collect { persisted ->
                lastPersistedSettings = persisted
                // A repository emission from an earlier transaction must not clobber a newer
                // optimistic UI edit waiting in the serialized write queue.
                if (confirmedRevision.get() >= requestedRevision.get()) {
                    requestedSettings = persisted
                    mutableSettings.value = persisted
                }
            }
        }

        val writer = writerScope.launch {
            for (task in writeQueue) {
                try {
                    task.action()
                    task.revision?.let { reconcileRevision(it, task.snapshot) }
                    task.completion?.complete(Unit)
                } catch (cancelled: CancellationException) {
                    task.completion?.cancel(cancelled)
                    writeQueue.cancel(cancelled)
                    throw cancelled
                } catch (t: Throwable) {
                    task.revision?.let { reconcileRevision(it, fallback = null) }
                    Log.e(TAG, "Não foi possível gravar ${task.label}", t)
                    task.completion?.completeExceptionally(t)
                }
            }
        }

        // close() lets the writer drain queued transactions instead of cancelling them.
        writer.invokeOnCompletion { cause ->
            if (cause != null) {
                val cancellation = cause as? CancellationException
                    ?: CancellationException("Fila de gravação encerrada inesperadamente").apply { initCause(cause) }
                writeQueue.cancel(cancellation)
            }
            writerJob.cancel()
        }
    }

    private fun enqueueWrite(task: WriteTask) {
        val result = writeQueue.trySend(task)
        if (result.isFailure) {
            val error = result.exceptionOrNull() ?: IllegalStateException("Fila de persistência fechada")
            task.completion?.completeExceptionally(error)
            val revision = task.revision
            if (revision != null && requestedRevision.get() == revision) {
                val persisted = lastPersistedSettings
                confirmedRevision.set(revision)
                requestedSettings = persisted
                mutableSettings.value = persisted
            }
            Log.e(TAG, "A fila de persistência está fechada; ${task.label} não foi enfileirado", error)
        }
    }

    private fun enqueueSnapshot(
        label: String,
        snapshot: AppSettings,
        completion: CompletableDeferred<Unit>? = null,
        action: suspend () -> Unit
    ) {
        val revision = requestedRevision.incrementAndGet()
        requestedSettings = snapshot
        mutableSettings.value = snapshot
        enqueueWrite(WriteTask(label, action, completion, revision, snapshot))
    }

    /** Read back the committed DataStore value. On failure, restore the latest durable snapshot. */
    private suspend fun reconcileRevision(revision: Long, fallback: AppSettings?) {
        val persisted = try {
            repository.flow.first()
        } catch (t: Throwable) {
            Log.w(TAG, "Não foi possível reler as configurações após a gravação", t)
            fallback ?: lastPersistedSettings
        }

        lastPersistedSettings = persisted
        confirmedRevision.set(revision)
        if (requestedRevision.get() == revision) {
            requestedSettings = persisted
            mutableSettings.value = persisted
        }
    }

    fun save(value: AppSettings) {
        enqueueSnapshot("as configurações", value) { repository.save(value) }
    }

    /** Returns only after DataStore has completed the transaction and the visible state is reconciled. */
    suspend fun saveAndAwait(value: AppSettings) {
        val completion = CompletableDeferred<Unit>()
        enqueueSnapshot("as configurações", value, completion) { repository.save(value) }
        completion.await()
    }

    fun update(transform: (AppSettings) -> AppSettings) = save(transform(requestedSettings))

    fun selectProfile(packageName: String) {
        val snapshot = requestedSettings.copy(selectedProfilePackage = packageName)
        enqueueSnapshot("a seleção do perfil $packageName", snapshot) {
            repository.selectProfile(packageName)
        }
    }

    suspend fun selectProfileAndAwait(packageName: String) {
        val completion = CompletableDeferred<Unit>()
        val snapshot = requestedSettings.copy(selectedProfilePackage = packageName)
        enqueueSnapshot("a seleção do perfil $packageName", snapshot, completion) {
            repository.selectProfile(packageName)
        }
        completion.await()
    }

    fun saveProfile(packageName: String, profile: AppProfile) {
        val current = requestedSettings
        val snapshot = current.copy(
            profiles = current.profiles + (packageName to profile),
            selectedProfilePackage = packageName
        )
        enqueueSnapshot("o perfil $packageName", snapshot) {
            repository.saveProfile(packageName, profile, selected = true)
        }
    }

    suspend fun saveProfileAndAwait(packageName: String, profile: AppProfile) {
        val completion = CompletableDeferred<Unit>()
        val current = requestedSettings
        val snapshot = current.copy(
            profiles = current.profiles + (packageName to profile),
            selectedProfilePackage = packageName
        )
        enqueueSnapshot("o perfil $packageName", snapshot, completion) {
            repository.saveProfile(packageName, profile, selected = true)
        }
        completion.await()
    }

    override fun close() {
        writeQueue.close()
        scope.cancel()
    }

    private companion object { const val TAG = "KeyMapperSettingsVM" }
}
