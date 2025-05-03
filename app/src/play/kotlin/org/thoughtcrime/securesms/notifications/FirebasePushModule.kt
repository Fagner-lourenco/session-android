package org.thoughtcrime.securesms.notifications

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import network.loki.messenger.BuildConfig
import org.session.libsession.messaging.notifications.TokenFetcher
import org.session.libsignal.utilities.Log
import javax.inject.Singleton
import com.google.firebase.FirebaseApp
import javax.inject.Inject

@Module
@InstallIn(SingletonComponent::class)
class FirebaseBindingModule {
    
    @Provides
    @Singleton
    fun provideTokenFetcher(): TokenFetcher {
        return if (BuildConfig.PLAY_STORE_DISABLED) {
            Log.i("FirebaseBindingModule", "Play Store está desativada, usando DummyTokenFetcher")
            DummyTokenFetcher()
        } else {
            Log.i("FirebaseBindingModule", "Play Store está ativada, usando FirebaseTokenFetcher")
            try {
                FirebaseTokenFetcher()
            } catch (e: Exception) {
                Log.e("FirebaseBindingModule", "Erro ao inicializar FirebaseTokenFetcher, usando DummyTokenFetcher", e)
                DummyTokenFetcher()
            }
        }
    }
}
