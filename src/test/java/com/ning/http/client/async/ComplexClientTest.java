/*
 * Copyright 2010 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.ning.http.client.async;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Response;
import org.mortbay.jetty.handler.AbstractHandler;
import org.testng.annotations.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class ComplexClientTest extends AbstractBasicTest {

    @Test
    public void multipleRequestsTest() throws Throwable {
        AsyncHttpClient c = new AsyncHttpClient();

        String body = "hello there";

        // once
        Response response = c.preparePost(TARGET_URL)
                .setBody(body)
                .execute().get(TIMEOUT, TimeUnit.SECONDS);

        assertEquals(response.getResponseBody(), body);

        // twice
        response = c.preparePost(TARGET_URL)
                .setBody(body)
                .execute().get(TIMEOUT, TimeUnit.SECONDS);

        assertEquals(response.getResponseBody(), body);
    }

    @Test
    public void multipleMaxConnectionOpenTest() throws Throwable {
        AsyncHttpClientConfig cg = new AsyncHttpClientConfig.Builder().setKeepAlive(true)
                .setConnectionTimeoutInMs(5000).setMaximumConnectionsTotal(1).build();
        AsyncHttpClient c = new AsyncHttpClient(cg);

        String body = "hello there";

        // once
        Response response = c.preparePost(TARGET_URL)
                .setBody(body)
                .execute().get(TIMEOUT, TimeUnit.SECONDS);

        assertEquals(response.getResponseBody(), body);

        // twice
        Exception exception = null;
        try {
            response = c.preparePost(TARGET_URL)
                    .setBody(body)
                    .execute().get(TIMEOUT, TimeUnit.SECONDS);
        } catch (Exception ex) {
            ex.printStackTrace();
            exception = ex;
        }
        assertNotNull(exception);
        assertEquals(exception.getMessage(), "Too many connections");
    }

    @Test
    public void multipleMaxConnectionOpenTestWithQuery() throws Throwable {
        AsyncHttpClientConfig cg = new AsyncHttpClientConfig.Builder().setKeepAlive(true)
                .setConnectionTimeoutInMs(5000).setMaximumConnectionsTotal(1).build();
        AsyncHttpClient c = new AsyncHttpClient(cg);

        String body = "hello there";

        // once
        Response response = c.preparePost(TARGET_URL + "?foo=bar")
                .setBody(body)
                .execute().get(TIMEOUT, TimeUnit.SECONDS);

        assertEquals(response.getResponseBody(), "foo_" + body);

        // twice
        Exception exception = null;
        try {
            response = c.preparePost(TARGET_URL)
                    .setBody(body)
                    .execute().get(TIMEOUT, TimeUnit.SECONDS);
        } catch (Exception ex) {
            ex.printStackTrace();
            exception = ex;
        }
        assertNotNull(exception);
        assertEquals(exception.getMessage(), "Too many connections");
    }

    @Test
    public void redirected302Test() throws Throwable {
        AsyncHttpClientConfig cg = new AsyncHttpClientConfig.Builder().setFollowRedirects(true).build();
        AsyncHttpClient c = new AsyncHttpClient(cg);

        // once
        Response response = c.preparePost(TARGET_URL)
                .setHeader("X-redirect", "http://www.google.com/")
                .execute().get();

        assertNotNull(response);
        assertEquals(response.getStatusCode(),200);
        assertEquals(response.getUrl().toString(), "http://www.google.com/");
    }

    @Test
    public void redirected302InvalidTest() throws Throwable {
        AsyncHttpClientConfig cg = new AsyncHttpClientConfig.Builder().setFollowRedirects(true).build();
        AsyncHttpClient c = new AsyncHttpClient(cg);

        // once
        Response response = c.preparePost(TARGET_URL)
                .setHeader("X-redirect", "http://www.grroooogle.com/")
                .execute().get();

        assertNotNull(response);
        assertEquals(response.getStatusCode(),404);
    }

    @Test
    public void relativeLocationUrl() throws Throwable {

        server.stop();
        server.setHandler(new AbstractHandler() {

            private final AtomicBoolean isSet = new AtomicBoolean(false);

            /* @Override */
            public void handle(String pathInContext,
                               HttpServletRequest httpRequest,
                               HttpServletResponse httpResponse,
                               int dispatch) throws ServletException, IOException {

                String param;
                httpResponse.setContentType("text/html; charset=utf-8");
                Enumeration<?> e = httpRequest.getHeaderNames();
                while (e.hasMoreElements()) {
                    param = e.nextElement().toString();

                    if (!isSet.getAndSet(true) && param.startsWith("X-redirect")) {
                        httpResponse.addHeader("Location", httpRequest.getHeader(param));
                        httpResponse.setStatus(302);
                        httpResponse.getOutputStream().flush();
                        httpResponse.getOutputStream().close();
                        return;
                    }
                }
                httpResponse.setStatus(200);
                httpResponse.getOutputStream().flush();
                httpResponse.getOutputStream().close();
            }
        });
        server.start();

        AsyncHttpClientConfig cg = new AsyncHttpClientConfig.Builder().setFollowRedirects(true).build();
        AsyncHttpClient c = new AsyncHttpClient(cg);

        Response response = c.preparePost(TARGET_URL)
                .setHeader("X-redirect", "/foo/test")
                .execute().get();
        assertNotNull(response);
        assertEquals(response.getStatusCode(),200);
        assertEquals(response.getUrl().toString(), TARGET_URL);

        server.stop();
        configureHandler();
        server.start();
    }
}
