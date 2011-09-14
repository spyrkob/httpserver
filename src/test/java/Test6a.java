/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * @test
 * @bug 6270015
 * @run main/othervm Test6a
 * @summary  Light weight HTTP server
 */

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.com.sun.net.httpserver.Headers;
import org.jboss.com.sun.net.httpserver.HttpContext;
import org.jboss.com.sun.net.httpserver.HttpExchange;
import org.jboss.com.sun.net.httpserver.HttpHandler;
import org.jboss.com.sun.net.httpserver.HttpsConfigurator;
import org.jboss.com.sun.net.httpserver.HttpsServer;

/**
 * Test https POST large file via chunked encoding (unusually small chunks)
 */

public class Test6a extends Test {

    public static void main (String[] args) throws Exception {
        Handler handler = new Handler();
        InetSocketAddress addr = new InetSocketAddress (0);
        HttpsServer server = HttpsServer.create (addr, 0);
        HttpContext ctx = server.createContext ("/test", handler);
        ExecutorService executor = Executors.newCachedThreadPool();
        SSLContext ssl = new SimpleSSLContext(System.getProperty("test.src")).get();
        server.setExecutor (executor);
        server.setHttpsConfigurator(new HttpsConfigurator (ssl));
        server.start ();

        URL url = new URL ("https://localhost:"+server.getAddress().getPort()+"/test/foo.html");
        System.out.print ("Test6a: " );
        HttpsURLConnection urlc = (HttpsURLConnection)url.openConnection ();
        urlc.setDoOutput (true);
        urlc.setRequestMethod ("POST");
        urlc.setChunkedStreamingMode (32); // small chunks
        urlc.setSSLSocketFactory (ssl.getSocketFactory());
        urlc.setHostnameVerifier (new DummyVerifier());
        OutputStream os = new BufferedOutputStream (urlc.getOutputStream());
        for (int i=0; i<SIZE; i++) {
            os.write (i % 100);
        }
        os.close();
        int resp = urlc.getResponseCode();
        if (resp != 200) {
            throw new RuntimeException ("test failed response code");
        }
        if (error) {
            throw new RuntimeException ("test failed error");
        }
        delay();
        server.stop(2);
        executor.shutdown();
        System.out.println ("OK");

    }

    public static boolean error = false;
    final static int SIZE = 999999;

    static class Handler implements HttpHandler {
        int invocation = 1;
        public void handle (HttpExchange t)
            throws IOException
        {
            InputStream is = t.getRequestBody();
            Headers map = t.getRequestHeaders();
            Headers rmap = t.getResponseHeaders();
            int c, count=0;
            while ((c=is.read ()) != -1) {
                if (c != (count % 100)) {
                    error = true;
                    break;
                }
                count ++;
            }
            if (count != SIZE) {
                error = true;
            }
            is.close();
            t.sendResponseHeaders (200, -1);
            t.close();
        }
    }
}
