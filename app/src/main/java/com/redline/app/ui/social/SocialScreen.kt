package com.redline.app.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.redline.app.ui.theme.OnSurfaceVariant
import com.redline.app.ui.theme.Red500
import com.redline.app.ui.theme.SurfaceVariant

@Composable
fun SocialScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text("Community", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Text("Find your gym and connect with training partners.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)

        // Map placeholder
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(SurfaceVariant, RoundedCornerShape(16.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(48.dp), tint = Red500)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Gym Finder", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Add a Google Maps API key in Settings to discover nearby gyms and find training partners.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceVariant, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text("Coming Soon", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Register at your gym, see who else trains there, and find workout partners.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}
