# Adaptadores de reproducción

Media3 y MediaSession se integrarán cuando existan archivos reproducibles.
`AudioPlaybackController` usa milisegundos; el motor TTS usa posición textual.
La coordinación deberá impedir voz y audio simultáneos y respetar audio focus.
