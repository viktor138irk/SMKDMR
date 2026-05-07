package ru.smkdmr.xlxclient

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

private const val TEST_HOST = "212.3.149.253"
private const val TEST_PORT = "62030"
private const val TEST_ROOM = "F"
private const val TEST_CALLSIGN = "PHOENIX"
private const val TEST_DMR_ID = "1030217"
private const val AUDIO_SAMPLE_RATE = 8_000
private const val AUDIO_PACKET_SIZE = 320

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val voiceClient = remember { UdpVoiceTestClient() }

    DisposableEffect(Unit) {
        onDispose { voiceClient.stop() }
    }

    var host by rememberSaveable { mutableStateOf(TEST_HOST) }
    var port by rememberSaveable { mutableStateOf(TEST_PORT) }
    var room by rememberSaveable { mutableStateOf(TEST_ROOM) }
    var callsign by rememberSaveable { mutableStateOf(TEST_CALLSIGN) }
    var dmrId by rememberSaveable { mutableStateOf(TEST_DMR_ID) }
    var status by rememberSaveable { mutableStateOf("Тестовый профиль готов: $TEST_HOST:$TEST_PORT, комната $TEST_ROOM") }
    var isTransmitting by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            status = if (granted) {
                "Микрофон разрешён. Удерживайте PTT для передачи."
            } else {
                "Нет разрешения на микрофон — PTT не сможет передавать голос."
            }
        }
    )

    val portNumber = port.toIntOrNull()
    val hostValid = host.trim().isNotEmpty()
    val portValid = portNumber != null && portNumber in 1..65535
    val roomValid = room.trim().isNotEmpty()
    val callsignValid = callsign.trim().isNotEmpty()
    val dmrIdValid = dmrId.trim().all(Char::isDigit) && dmrId.trim().isNotEmpty()
    val canConnect = hostValid && portValid && roomValid && callsignValid && dmrIdValid

    fun startPtt() {
        if (!canConnect || portNumber == null) {
            status = "Проверьте IP, порт, комнату, позывной и DMR ID."
            return
        }

        val hasMicPermission = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasMicPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        isTransmitting = true
        status = "PTT ON: микрофон отправляет тестовый PCM UDP поток на ${host.trim()}:$port"
        voiceClient.start(
            host = host.trim(),
            port = portNumber,
            room = room.trim().uppercase(),
            callsign = callsign.trim().uppercase(),
            dmrId = dmrId.trim(),
            onError = { message ->
                isTransmitting = false
                status = message
            }
        )
    }

    fun stopPtt() {
        voiceClient.stop()
        if (isTransmitting) {
            status = "PTT OFF: передача остановлена"
        }
        isTransmitting = false
    }

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
                                isError = !hostValid
                            )

                            OutlinedTextField(
                                value = port,
                                onValueChange = { input -> port = input.filter(Char::isDigit).take(5) },
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

                            OutlinedTextField(
                                value = callsign,
                                onValueChange = { callsign = it.uppercase().take(12) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Позывной") },
                                singleLine = true,
                                isError = !callsignValid
                            )

                            OutlinedTextField(
                                value = dmrId,
                                onValueChange = { input -> dmrId = input.filter(Char::isDigit).take(9) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("DMR ID") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = !dmrIdValid
                            )

                            Button(
                                onClick = {
                                    scope.launch {
                                        status = withContext(Dispatchers.IO) {
                                            sendUdpProbe(
                                                host = host.trim(),
                                                port = portNumber ?: 0,
                                                room = room.trim().uppercase(),
                                                callsign = callsign.trim().uppercase(),
                                                dmrId = dmrId.trim()
                                            )
                                        }
                                    }
                                },
                                enabled = canConnect,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Проверить UDP подключение")
                            }

                            Button(
                                onClick = {},
                                enabled = canConnect,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                                    .pointerInput(canConnect) {
                                        detectTapGestures(
                                            onPress = {
                                                startPtt()
                                                tryAwaitRelease()
                                                stopPtt()
                                            }
                                        )
                                    }
                            ) {
                                Text(if (isTransmitting) "PTT: ПЕРЕДАЧА..." else "Удерживать PTT")
                            }

                            TextButton(
                                onClick = {
                                    host = TEST_HOST
                                    port = TEST_PORT
                                    room = TEST_ROOM
                                    callsign = TEST_CALLSIGN
                                    dmrId = TEST_DMR_ID
                                    status = "Тестовый профиль восстановлен"
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Вернуть тестовые параметры")
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
            text = "PTT тест: микрофон → UDP. Для настоящего DMR нужен AMBE+2 vocoder и MMDVM Homebrew кадры.",
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
            onValueChange = { value -> onRoomSelected(value.take(1).uppercase()) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            label = { Text("Комната / модуль") },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            supportingText = { Text("F = тестовый модуль") }
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

private fun sendUdpProbe(
    host: String,
    port: Int,
    room: String,
    callsign: String,
    dmrId: String
): String {
    return try {
        DatagramSocket().use { socket ->
            socket.soTimeout = 1_000
            val address = InetAddress.getByName(host)
            val payload = "SMKDMR-PROBE|CALL=$callsign|ID=$dmrId|ROOM=$room".toByteArray(Charsets.US_ASCII)
            socket.send(DatagramPacket(payload, payload.size, address, port))
        }
        "UDP probe отправлен на $host:$port, комната $room"
    } catch (error: Exception) {
        "Ошибка UDP probe: ${error.message ?: error::class.java.simpleName}"
    }
}

private class UdpVoiceTestClient {
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null

    @SuppressLint("MissingPermission")
    fun start(
        host: String,
        port: Int,
        room: String,
        callsign: String,
        dmrId: String,
        onError: (String) -> Unit
    ) {
        if (!running.compareAndSet(false, true)) return

        worker = Thread {
            var recorder: AudioRecord? = null
            try {
                val minBuffer = AudioRecord.getMinBufferSize(
                    AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(minBuffer, AUDIO_PACKET_SIZE * 4)
                val audioBuffer = ByteArray(AUDIO_PACKET_SIZE)
                val address = InetAddress.getByName(host)
                var sequence = 0

                recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    onError("AudioRecord не инициализирован. Проверьте разрешение микрофона.")
                    running.set(false)
                    return@Thread
                }

                DatagramSocket().use { socket ->
                    recorder.startRecording()

                    while (running.get()) {
                        val read = recorder.read(audioBuffer, 0, audioBuffer.size)
                        if (read > 0) {
                            val header = "SMKDMR-PCM|CALL=$callsign|ID=$dmrId|ROOM=$room|SEQ=${sequence++}|".toByteArray(Charsets.US_ASCII)
                            val packetBytes = ByteArray(header.size + read)
                            System.arraycopy(header, 0, packetBytes, 0, header.size)
                            System.arraycopy(audioBuffer, 0, packetBytes, header.size, read)
                            socket.send(DatagramPacket(packetBytes, packetBytes.size, address, port))
                        }
                    }
                }
            } catch (error: Exception) {
                onError("Ошибка PTT передачи: ${error.message ?: error::class.java.simpleName}")
            } finally {
                try {
                    recorder?.stop()
                } catch (_: Exception) {
                }
                recorder?.release()
                running.set(false)
            }
        }.apply {
            name = "SMKDMR-PTT-UDP"
            start()
        }
    }

    fun stop() {
        running.set(false)
        worker?.interrupt()
        worker = null
    }
}

@Preview(showBackground = true)
@Composable
private fun XlxConnectionScreenPreview() {
    SmkdmrTheme {
        XlxConnectionScreen()
    }
}
