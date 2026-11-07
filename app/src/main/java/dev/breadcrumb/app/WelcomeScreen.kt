package dev.breadcrumb.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun WelcomeScreen(continueToNotebook: () -> Unit, github: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = github) {
                Icon(Icons.Outlined.StarOutline, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("See GitHub & star")
            }
        }
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(128.dp).background(Amber, CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(72.dp).background(Color(0xFFFF9F2E), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Check, null, Modifier.size(38.dp), tint = Ink)
                }
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp).background(Color(0xFFFF9F2E), CircleShape))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Welcome to Breadcrumb", style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("A trail back to your handwritten solutions.", modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center, color = Muted)
        Spacer(Modifier.height(24.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Choose a notebook folder. Scan a question or paste its link, check the title, and choose its site. Add a photo of your solution. Search its title whenever you want to revise.",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = continueToNotebook, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(28.dp)) {
            Text("Continue")
        }
        Spacer(Modifier.height(24.dp))
    }
}
