package com.javargasp.tiominutermvi.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.ui.base.CollectEffects
import org.koin.androidx.compose.koinViewModel

/**
 * Home screen composable
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToRoutineSelection: () -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    
    // Handle effects
    CollectEffects(viewModel.effect) { effect ->
        when (effect) {
            is HomeEffect.NavigateToRoutineSelection -> {
                onNavigateToRoutineSelection()
            }
        }
    }
    
    // Load data on first composition
    LaunchedEffect(Unit) {
        viewModel.handleIntent(HomeIntent.OnStart)
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Uncle image
            Image(
                painter = painterResource(id = R.drawable.uncle),
                contentDescription = "Uncle",
                modifier = Modifier
                    .size(120.dp)
                    .padding(bottom = 16.dp)
            )
            
            // Title
            Text(
                text = stringResource(R.string.home_screen_title),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Tagline
            Text(
                text = stringResource(R.string.home_screen_tagline),
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Current day info (if loaded)
            if (!state.isLoading && state.currentDay != null) {
                Text(
                    text = "Day ${state.currentDay} Available",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(32.dp))
            }
            
            // Start button
            Button(
                onClick = { 
                    viewModel.handleIntent(HomeIntent.OnStartRoutineClicked)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = stringResource(R.string.start_today_routine),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}