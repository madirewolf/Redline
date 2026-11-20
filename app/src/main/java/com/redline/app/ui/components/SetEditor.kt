package com.redline.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.voice.SetDraft

/**
 * Edit a set by hand: fixing what the parser misheard, typing a set when
 * talking isn't an option, or correcting one already saved. Errors only show
 * once you've tried to save, so the empty form isn't a wall of red.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetEditorDialog(
    title: String,
    initial: SetDraft,
    onSave: (SetDraft) -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "Save",
    onDelete: (() -> Unit)? = null
) {
    var draft by remember { mutableStateOf(initial) }
    var showErrors by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this set?") },
            text = { Text("It comes out of this workout's totals and your history.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Field(
                    label = "Exercise",
                    value = draft.exercise,
                    onChange = { draft = draft.copy(exercise = it) },
                    error = draft.exerciseError.takeIf { showErrors },
                    keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Field(
                        label = "Weight",
                        value = draft.weight,
                        onChange = { draft = draft.copy(weight = it) },
                        error = draft.weightError.takeIf { showErrors },
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f)
                    )
                    // Top padding lines the toggle up with the field's box, not its label.
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(top = 8.dp)) {
                        listOf("lbs", "kg").forEachIndexed { index, unit ->
                            SegmentedButton(
                                selected = draft.unit == unit,
                                onClick = { draft = draft.copy(unit = unit) },
                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                                icon = {}
                            ) { Text(unit) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(
                        label = "Reps",
                        value = draft.reps,
                        onChange = { draft = draft.copy(reps = it) },
                        error = draft.repsError.takeIf { showErrors },
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f)
                    )
                    Field(
                        label = "RPE",
                        value = draft.rpe,
                        onChange = { draft = draft.copy(rpe = it) },
                        error = draft.rpeError.takeIf { showErrors },
                        placeholder = "Optional",
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f)
                    )
                }
                Field(
                    label = "Notes",
                    value = draft.notes,
                    onChange = { draft = draft.copy(notes = it) },
                    error = null,
                    placeholder = "Optional",
                    singleLine = false,
                    keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (draft.isValid) onSave(draft) else showErrors = true
            }) { Text(confirmLabel) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/** The saved set with the editor's changes, or null if the draft doesn't validate. */
fun ExerciseSet.withDraft(draft: SetDraft): ExerciseSet? {
    val edited = draft.toParsedSet(rawVoiceInput.orEmpty()) ?: return null
    return copy(
        exerciseName = edited.exerciseName,
        canonicalExerciseKey = edited.canonicalExerciseKey,
        weight = edited.weight,
        unit = edited.unit,
        reps = edited.reps,
        rpe = edited.rpe,
        notes = edited.notes
    )
}

/** A saved set, back in the editor's terms. */
fun ExerciseSet.toDraft() = SetDraft(
    exercise = exerciseName,
    weight = weight?.let(SetDraft::formatNumber).orEmpty(),
    unit = unit,
    reps = reps?.toString().orEmpty(),
    rpe = rpe?.let(SetDraft::formatNumber).orEmpty(),
    notes = notes.orEmpty()
)

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    error: String?,
    keyboard: KeyboardOptions,
    modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String? = null,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = singleLine,
        keyboardOptions = keyboard,
        modifier = modifier
    )
}
