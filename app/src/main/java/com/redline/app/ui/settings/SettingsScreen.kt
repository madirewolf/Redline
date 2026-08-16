package com.redline.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.redline.app.ui.theme.OnSurfaceVariant
import com.redline.app.ui.theme.Red500
import com.redline.app.ui.theme.SurfaceVariant

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val llmEndpoint by viewModel.llmEndpoint.collectAsStateWithLifecycle()
    val defaultUnit by viewModel.defaultUnit.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Name
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceVariant, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text("Profile", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = userName,
                onValueChange = viewModel::setUserName,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Name") },
                placeholder = { Text("Your name") }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceVariant, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "Voice parser",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Use a secure HTTPS endpoint that calls Anthropic and returns structured set JSON.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = llmEndpoint,
                onValueChange = viewModel::setLlmEndpoint,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Parser endpoint") },
                placeholder = { Text("https://your-api.com/redline/parse-set") }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceVariant, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "Units",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row {
                UnitButton(
                    label = "lbs",
                    selected = defaultUnit == "lbs",
                    onClick = { viewModel.setDefaultUnit("lbs") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                UnitButton(
                    label = "kg",
                    selected = defaultUnit == "kg",
                    onClick = { viewModel.setDefaultUnit("kg") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Local regex parsing works without a server. The endpoint upgrades parsing when configured.",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant,
        )
    }
}

@Composable
private fun UnitButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(containerColor = Red500),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(label)
        }
    }
}
