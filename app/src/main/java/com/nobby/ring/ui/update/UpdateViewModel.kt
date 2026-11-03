package com.nobby.ring.ui.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobby.ring.BuildConfig
import com.nobby.ring.di.ApplicationScope
import com.nobby.ring.update.AppVersion
import com.nobby.ring.update.UpdateCoordinator
import com.nobby.ring.update.UpdateResult
import com.nobby.ring.update.UpdateStore
import com.nobby.ring.update.selectOffer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Result of a manual check in Settings > About. */
sealed interface UpdateCheckUi {
    data object Idle : UpdateCheckUi
    data object Checking : UpdateCheckUi
    data object UpToDate : UpdateCheckUi
    data class Available(val offer: UpdateResult.Available) : UpdateCheckUi
    data object Failed : UpdateCheckUi
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val store: UpdateStore,
    private val coordinator: UpdateCoordinator,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val installed = AppVersion.parse(BuildConfig.VERSION_NAME) ?: AppVersion(0, 0, 0)

    /** The release to show on Home: newer than the installed one and not dismissed. */
    val offer: StateFlow<UpdateResult.Available?> = store.state.map { selectOffer(it, installed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _check = MutableStateFlow<UpdateCheckUi>(UpdateCheckUi.Idle)
    val check: StateFlow<UpdateCheckUi> = _check

    fun dismiss(version: String) {
        appScope.launch {
            try {
                store.dismiss(version)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("NoBby", "dismissing the update banner failed", e)
            }
        }
    }

    fun checkNow() {
        if (_check.value == UpdateCheckUi.Checking) return
        _check.value = UpdateCheckUi.Checking
        viewModelScope.launch {
            _check.value = try {
                when (val r = coordinator.checkNow()) {
                    UpdateResult.UpToDate -> UpdateCheckUi.UpToDate
                    is UpdateResult.Available -> UpdateCheckUi.Available(r)
                    UpdateResult.Failed -> UpdateCheckUi.Failed
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("NoBby", "manual update check failed", e)
                UpdateCheckUi.Failed
            }
        }
    }
}

/** Opens [url] in the browser; a device without one must not crash the app. */
fun Context.openUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { Log.e("NoBby", "Cannot open $url", it) }
}

/** The apk when the release has one, else the release page. */
fun UpdateResult.Available.downloadUrl(): String = apkUrl ?: pageUrl
