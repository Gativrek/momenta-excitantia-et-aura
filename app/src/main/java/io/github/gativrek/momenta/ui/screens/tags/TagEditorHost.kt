package io.github.gativrek.momenta.ui.screens.tags

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.gativrek.momenta.App
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.data.tags.TagEdit
import io.github.gativrek.momenta.data.tags.TagWriteResult
import io.github.gativrek.momenta.data.tags.TagWriter
import io.github.gativrek.momenta.data.tags.WriteAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Saves tag edits. Screens get it from [LocalTagEditor]; [TagEditorHost] handles permissions and progress. */
fun interface TagEditor {
    fun save(edits: List<Pair<Track, TagEdit>>)
}

val LocalTagEditor = staticCompositionLocalOf<TagEditor> { error("TagEditorHost is missing") }

/** Progress of the batch being written, or null when nothing is being written. */
data class TagWriteProgress(
    val done: Int,
    val total: Int,
)

class TagEditViewModel(
    private val writer: TagWriter,
    /** Application scope: leaving the screen mid-album must not stop a write half-way through the batch. */
    private val scope: CoroutineScope,
) : ViewModel() {
    /** Edits waiting for the user to grant write access. */
    private var pending: List<Pair<Track, TagEdit>> = emptyList()

    private val progressState = MutableStateFlow<TagWriteProgress?>(null)
    val progress: StateFlow<TagWriteProgress?> = progressState.asStateFlow()

    private val resultChannel = Channel<TagWriteResult>(Channel.BUFFERED)
    val results = resultChannel.receiveAsFlow()

    fun hold(edits: List<Pair<Track, TagEdit>>) {
        pending = edits
    }

    fun pendingTracks(): List<Track> = pending.map { it.first }

    /** Access granted: write what was held. */
    fun writePending() {
        val edits = pending.takeIf { it.isNotEmpty() } ?: return
        pending = emptyList()
        progressState.value = TagWriteProgress(0, edits.size)
        scope.launch {
            val result = writer.write(edits) { done -> progressState.value = TagWriteProgress(done, edits.size) }
            progressState.value = null
            resultChannel.send(result)
        }
    }

    fun dropPending() {
        pending = emptyList()
    }

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    val container = (this[APPLICATION_KEY] as App).container
                    TagEditViewModel(container.tagWriter, container.applicationScope)
                }
            }
    }
}

/**
 * Provides [LocalTagEditor] to [content]. Saving asks for write access the way this Android
 * version wants it (see WriteAccess), shows progress while files are written, then reports
 * how it went.
 */
@Composable
fun TagEditorHost(content: @Composable () -> Unit) {
    val viewModel: TagEditViewModel = viewModel(factory = TagEditViewModel.Factory)
    val context = LocalContext.current
    val res = LocalResources.current

    fun declined() {
        viewModel.dropPending()
        Toast.makeText(context, R.string.tags_access_declined, Toast.LENGTH_LONG).show()
    }

    val writeRequest =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) viewModel.writePending() else declined()
        }
    val legacyPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.writePending() else declined()
        }

    val editor =
        remember(viewModel) {
            TagEditor { edits ->
                if (edits.isEmpty()) return@TagEditor
                viewModel.hold(edits)
                when {
                    WriteAccess.usesWriteRequest ->
                        writeRequest.launch(
                            IntentSenderRequest
                                .Builder(WriteAccess.writeRequest(context.contentResolver, viewModel.pendingTracks()))
                                .build(),
                        )
                    WriteAccess.hasLegacyPermission(context) -> viewModel.writePending()
                    else -> legacyPermission.launch(WriteAccess.LEGACY_PERMISSION)
                }
            }
        }

    LaunchedEffect(viewModel) {
        viewModel.results.collect { result ->
            val message =
                if (result.failed.isEmpty()) {
                    res.getQuantityString(R.plurals.tags_saved, result.written, result.written)
                } else {
                    res.getQuantityString(R.plurals.tags_failed, result.failed.size, result.failed.size)
                }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    val progress by viewModel.progress.collectAsStateWithLifecycle()
    progress?.let { WritingDialog(it) }

    CompositionLocalProvider(LocalTagEditor provides editor, content = content)
}

/** Can't be dismissed: files are being rewritten and the result toast follows. */
@Composable
private fun WritingDialog(progress: TagWriteProgress) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.tags_writing)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LinearProgressIndicator(
                    progress = { if (progress.total > 0) progress.done.toFloat() / progress.total else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.tags_progress, progress.done, progress.total))
            }
        },
        confirmButton = {},
    )
}
