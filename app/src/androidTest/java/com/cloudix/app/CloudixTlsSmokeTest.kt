package com.cloudix.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import android.util.Log
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/** Exercises the real AndroidKeyStore identity on an Android runtime. */
@RunWith(AndroidJUnit4::class)
class CloudixTlsSmokeTest {
    @Test fun keystoreServerAndPinnedClientCompleteHandshake() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val tls = TlsIdentity.serverContext(ctx)
        val pin = TlsIdentity.fingerprint(ctx)
        val executor = Executors.newSingleThreadExecutor()
        val serverFailure = AtomicReference<Throwable?>(null)
        (tls.serverSocketFactory.createServerSocket(0) as SSLServerSocket).use { server ->
            val future = executor.submit<Boolean> {
                try {
                    (server.accept() as SSLSocket).use { socket ->
                        socket.soTimeout = 20000
                        socket.startHandshake()
                        val byte = socket.inputStream.read()
                        socket.outputStream.write(byte + 1)
                        socket.outputStream.flush()
                        true
                    }
                } catch (e: Throwable) {
                    serverFailure.set(e)
                    Log.e("CloudixTlsTest", "TLS server failed", e)
                    false
                }
            }
            try {
                try {
                    (TlsIdentity.clientContext(pin).socketFactory.createSocket("127.0.0.1", server.localPort)
                        as SSLSocket).use { socket ->
                        socket.soTimeout = 20000
                        socket.startHandshake()
                        socket.outputStream.write(41)
                        socket.outputStream.flush()
                        assertEquals(42, socket.inputStream.read())
                    }
                } catch (e: Throwable) {
                    future.get(20, TimeUnit.SECONDS)
                    throw AssertionError("Client TLS failure, server cause: ${serverFailure.get()}", e)
                }
                assertTrue("Server handshake: ${serverFailure.get()}", future.get(20, TimeUnit.SECONDS))
            } finally { executor.shutdownNow() }
        }
    }
}
