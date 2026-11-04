package com.example.keymapper.injection

/** Cache thread-safe para proxies Binder que podem ficar obsoletos após reinício do serviço remoto. */
internal class RebindableServiceCache<T : Any> {
    private val lock = Any()

    @Volatile
    private var cached: T? = null

    fun get(factory: () -> T): T {
        cached?.let { return it }
        return synchronized(lock) {
            cached ?: factory().also { cached = it }
        }
    }

    /** Invalida tudo ou somente a instância esperada, sem apagar uma conexão mais recente. */
    fun invalidate(expected: T? = null) {
        synchronized(lock) {
            if (expected == null || cached === expected) cached = null
        }
    }
}
