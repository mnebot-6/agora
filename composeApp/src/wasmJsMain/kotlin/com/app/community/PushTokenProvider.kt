package com.app.community

// Sin push en web v1 (Web Push en iOS Safari requiere PWA instalada y 16.4+;
// anotado como mejora futura en la spec). App.kt ignora el token cuando es null.
actual suspend fun fetchPushToken(): String? = null
