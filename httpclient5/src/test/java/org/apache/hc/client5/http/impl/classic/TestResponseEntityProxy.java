/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.hc.client5.http.impl.classic;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.impl.io.ChunkedInputStream;
import org.apache.hc.core5.http.impl.io.SessionInputBufferImpl;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

class TestResponseEntityProxy {

    @Mock
    private ClassicHttpResponse response;
    @Mock
    private ExecRuntime execRuntime;
    @Mock
    private HttpEntity entity;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Mockito.when(entity.isStreaming()).thenReturn(Boolean.TRUE);
        Mockito.when(response.getEntity()).thenReturn(entity);
    }

    @Test
    void testGetTrailersWithNoChunkedInputStream() throws Exception {
        final ByteArrayInputStream inputStream = new ByteArrayInputStream("Test payload".getBytes());
        Mockito.when(entity.getContent()).thenReturn(inputStream);
        final ArgumentCaptor<HttpEntity> httpEntityArgumentCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        ResponseEntityProxy.enhance(response, execRuntime);

        Mockito.verify(response).setEntity(httpEntityArgumentCaptor.capture());
        final HttpEntity wrappedEntity = httpEntityArgumentCaptor.getValue();

        final InputStream is = wrappedEntity.getContent();
        while (is.read() != -1) {
        } // read until the end
        final Supplier<List<? extends Header>> trailers = wrappedEntity.getTrailers();

        Assertions.assertTrue(trailers.get().isEmpty());
    }

    @Test
    void testGetTrailersWithChunkedInputStream() throws Exception {
        final SessionInputBuffer sessionInputBuffer = new SessionInputBufferImpl(100);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream("0\r\nX-Test-Trailer-Header: test\r\n".getBytes());
        final ChunkedInputStream chunkedInputStream = new ChunkedInputStream(sessionInputBuffer, inputStream);

        Mockito.when(entity.getContent()).thenReturn(chunkedInputStream);
        final ArgumentCaptor<HttpEntity> httpEntityArgumentCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        ResponseEntityProxy.enhance(response, execRuntime);

        Mockito.verify(response).setEntity(httpEntityArgumentCaptor.capture());
        final HttpEntity wrappedEntity = httpEntityArgumentCaptor.getValue();

        final InputStream is = wrappedEntity.getContent();
        while (is.read() != -1) {
        } // consume the stream so it can reach to trailers and parse
        final Supplier<List<? extends Header>> trailers = wrappedEntity.getTrailers();
        final List<? extends Header> headers = trailers.get();

        Assertions.assertEquals(1, headers.size());
        final Header header = headers.get(0);
        Assertions.assertEquals("X-Test-Trailer-Header", header.getName());
        Assertions.assertEquals("test", header.getValue());
    }

    @Test
    void testWriteToNullDrainsAndReleasesStream() throws Exception {
        final SessionInputBuffer sessionInputBuffer = new SessionInputBufferImpl(100);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream("0\r\nX-Test-Trailer-Header: test\r\n".getBytes());
        final ChunkedInputStream chunkedInputStream = new ChunkedInputStream(sessionInputBuffer, inputStream);
        final CloseableHttpResponse resp = new CloseableHttpResponse(new BasicClassicHttpResponse(200), execRuntime);
        final HttpEntity entity = new BasicHttpEntity(chunkedInputStream, null, true);
        Assertions.assertTrue(entity.isStreaming());
        resp.setEntity(entity);

        ResponseEntityProxy.enhance(resp, execRuntime);

        final HttpEntity wrappedEntity = resp.getEntity();

        wrappedEntity.writeTo(null);
        Mockito.verify(execRuntime).releaseEndpoint();

        final Supplier<List<? extends Header>> trailers = wrappedEntity.getTrailers();
        final List<? extends Header> headers = trailers.get();

        Assertions.assertEquals(1, headers.size());
        final Header header = headers.get(0);
        Assertions.assertEquals("X-Test-Trailer-Header", header.getName());
        Assertions.assertEquals("test", header.getValue());


    }
}