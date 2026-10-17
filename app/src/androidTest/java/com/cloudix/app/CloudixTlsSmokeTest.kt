package com.cloudix.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/** Exercises the real AndroidKeyStore identity on an Android runtime. */
@RunWith(AndroidJUnit4::class)
class CloudixTlsSmokeTest {
    @Test fun keystoreServerAndPinnedClientCompleteHandshake() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val tls = TlsIdentity.serverContext()
        val pin = TlsIdentity.fingerprint(ctx)
        val executor = Executors.newSingleThreadExecutor()
        (tls.serverSocketFactory.createServerSocket(0) as SSLServerSocket).use { server ->
            val future = executor.submit<Boolean> {
                (server.accept() as SSLSocket).use { socket ->
                    socket.soTimeout = 20000
                    socket.startHandshake()
                    val byte = socket.inputStream.read()
                    socket.outputStream.write(byte + 1)
                    socket.outputStream.flush()
                    true
                }
            }
            try {
                (TlsIdentity.clientContext(pin).socketFactory.createSocket("127.0.0.1", server.localPort)
                    as SSLSocket).use { socket ->
                    socket.soTimeout = 20000
                    socket.startHandshake()
                    socket.outputStream.write(41)
                    socket.outputStream.flush()
                    assertEquals(42, socket.inputStream.read())
                }
                assertTrue(future.get(20, TimeUnit.SECONDS))
            } finally { executor.shutdownNow() }
        }
    }
}
