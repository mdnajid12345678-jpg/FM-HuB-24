package com.fmhub24.android.stream

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamEngineTest {
    @Test
    fun `connection and DNS failures are offline`() {
        assertEquals(StreamError.OFFLINE, StreamEngine.classify(UnknownHostException()))
        assertEquals(StreamError.OFFLINE, StreamEngine.classify(ConnectException()))
    }

    @Test
    fun `timeouts and TLS failures are not reported as offline`() {
        assertEquals(StreamError.FAILED, StreamEngine.classify(SocketTimeoutException()))
        assertEquals(StreamError.FAILED, StreamEngine.classify(SSLException("certificate")))
    }

    @Test
    fun `ordinary missing file is not assumed to be an expired signed link`() {
        assertEquals(StreamError.FAILED, StreamEngine.classify(IOException("HTTP 404")))
    }
}
