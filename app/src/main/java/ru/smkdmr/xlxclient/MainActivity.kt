package ru.smkdmr.xlxclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmkdmrTheme {
                XlxConnectionScreen()
            }
        }
    }
}

private val defaultRooms = listOf("A", "B", "C", "D", "E", "F", "G", "H")

@Composable
private fun SmkdmrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        typography = MaterialTheme.typography,
        content = content
    )
}

@Composable
fun XlxConnectionScreen(modifier: Modifier = Modifier) {
    var host by rememberSaveable { mutableStateOf("xlx.example.org") }
    var port by rememberSaveable { mutableStateOf("40001") }
    var room by rememberSaveable { mutableStateOf("A") }
    var status by rememberSaveable { mutableStateOf("Введите параметры XLX-сервера") }

    val portNumber = port.toIntOrNull()
    val hostValid = host.trim().isNotEmpty()
    val portValid = portNumber != null && portNumber in 1..65535
    val roomValid = room.trim().isNotEmpty()
    val canConnect = hostValid && portValid && roomValid

    Scaffold { innerPadding ->
        Surface(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Header()

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            OutlinedTextField(
                                value = host,
                                onValueChange = { host = it.trim() },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("IP или домен XLX") },
                                singleLine = true,
                                isError = !hostValid,
                                supportingText = {
                                    if (!hostValid) Text("Укажите адрес сервера")
                                }
                            )

                            OutlinedTextField(
                                value = port,
                                onValueChange = { input ->
                                    port = input.filter(Char::isDigit).take(5)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Порт") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = port.isNotBlank() && !portValid,
                                supportingText = {
                                    if (port.isNotBlank() && !portValid) Text("Порт должен быть от 1 до 65535")
                                }
                            )

                            RoomPicker(
                                selectedRoom = room,
                                onRoomSelected = { room = it }
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Button(
                                onClick = {
                                    status = "Готово: ${host.trim()}:$port / комната ${room.trim().uppercase()}"
                                },
                                enabled = canConnect,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Подключиться")
                            }

                            TextButton(
                                onClick = {
                                    host = "xlx.example.org"
                                    port = "40001"
                                    room = "A"
                                    status = "Параметры сброшены"
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Сбросить")
                            }
                        }
                    }
                }

                StatusBlock(status = status)
            }
        }
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "SMKDMR XLX Client",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Кастомный Android-клиент для выбора XLX-сервера, порта и комнаты.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomPicker(
    selectedRoom: String,
    onRoomSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedRoom,
            onValueChange = { value ->
                onRoomSelected(value.take(1).uppercase())
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            label = { Text("Комната / модуль") },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            supportingText = { Text("Например: A, B, C") }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            defaultRooms.forEach { room ->
                DropdownMenuItem(
                    text = { Text(room) },
                    onClick = {
                        onRoomSelected(room)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusBlock(status: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Статус", style = MaterialTheme.typography.labelLarge)
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun XlxConnectionScreenPreview() {
    SmkdmrTheme {
        XlxConnectionScreen()
    }
}
