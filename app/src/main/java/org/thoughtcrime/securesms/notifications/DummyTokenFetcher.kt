package org.thoughtcrime.securesms.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import org.session.libsession.messaging.notifications.TokenFetcher
import org.session.libsignal.utilities.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uma implementação de TokenFetcher que não depende do Firebase.
 * Usada quando o Firebase está desabilitado ou não disponível.
 */
@Singleton
class DummyTokenFetcher @Inject constructor() : TokenFetcher {
    override val token = MutableStateFlow<String?>(null)
    
    init {
        Log.d("DummyTokenFetcher", "Inicializando com token vazio")
    }

    override fun onNewToken(token: String) {
        Log.d("DummyTokenFetcher", "Novo token recebido: $token")
        this.token.value = token
    }

    override suspend fun resetToken() {
        Log.d("DummyTokenFetcher", "Reset de token solicitado")
        token.value = null
    }
}