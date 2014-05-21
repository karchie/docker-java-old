/**
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
 */
package com.kpelykh.docker.client;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kpelykh.io.IOFunction;


/**
 * @author Kevin A. Archie <karchie@wustl.edu>
 *
 */
final class SocketAttachedContainer implements AttachedContainer {
    private static final String CRLF = "\r\n";
    private final Logger logger = LoggerFactory.getLogger(SocketAttachedContainer.class);
    private final Socket socket;
    private final AttachedStreamDemuxer<?,?> demuxer;

    public SocketAttachedContainer(final Socket socket, final String path,
            final String containerId, final IOFunction<OutputStream,?> sendBody,
            final OutputStream stdout, final OutputStream stderr,
            final boolean logs, final boolean stream) throws DockerException,IOException {
        this.socket = socket;
        if (!socket.isConnected()) {
            throw new IllegalStateException("socket must be connected");
        }

        final StringBuilder req = new StringBuilder("POST /");
        req.append(DockerClient.DOCKER_API_VERSION);
        if (null == path || "".equals(path)) {
            req.append('/');
        } else {
            if (!path.startsWith("/")) {
                req.append('/');
            }
            req.append(path);
            if (!path.endsWith("/")) {
                req.append('/');
            }
        }
        assert '/' == req.charAt(req.length() - 1);
        req.append("containers/");
        req.append(containerId).append("/attach?");
        req.append("logs=").append(logs);
        req.append("&stream=").append(stream);
        req.append("&stdin=").append(null != sendBody);
        req.append("&stdout=").append(null != stdout);
        req.append("&stderr=").append(null != stderr);
        req.append(" HTTP/1.1").append(CRLF).append(CRLF);
        
        final OutputStream out = socket.getOutputStream();
        out.write(req.toString().getBytes());
        out.flush();
        logger.trace("sent request header: {}", req);

        final InputStream in = socket.getInputStream();
        consumeHTTPHeader(in, logger);
        
        try {
            demuxer = AttachedStreamDemuxer.create(in, stdout, stderr);
            if (null != sendBody) {
                sendBody.apply(out);
                out.flush();
            }
            socket.shutdownOutput();
            logger.trace("sent request and FIN");
        } catch (IOException e) {
            throw new DockerException("error writing attach body", e);
        }
    }
    
    public SocketAttachedContainer(final SocketAddress addr, final String path,
            final String containerId, final IOFunction<OutputStream,?> sendBody,
            final OutputStream stdout, final OutputStream stderr,
            final boolean logs, final boolean stream) throws DockerException,IOException {
        this(makeSocket(addr), path, containerId, sendBody, stdout, stderr, logs, stream);
    }
    
    public SocketAttachedContainer(final URL url, final String containerId,
            final IOFunction<OutputStream,?> sendBody,
            final OutputStream stdout, final OutputStream stderr,
            final boolean logs, final boolean stream) throws DockerException,IOException {
        this(getSocketAddress(url), url.getPath(), containerId, sendBody, stdout, stderr, logs, stream);
    }

    
    private static SocketAddress getSocketAddress(final URL url) {
        int port = url.getPort();
        if (-1 == port) {
            port = 4243;
        }
        return new InetSocketAddress(url.getHost(), port);
    }


    private static Socket makeSocket(final SocketAddress addr) throws IOException {
        final Socket socket = new Socket();
        socket.connect(addr);
        return socket;
    }

    /**
     * Consume the HTTP header up to and including terminating CRLFCRLF
     * @return header as byte array
     * @throws IOException
     */
    private static byte[] consumeHTTPHeader(final InputStream in, final Logger logger) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        logger.debug("starting header consumer");
        try {
            for (int state = 0; state < 4;) {
                int b = in.read();
                if ('\n' == b && 1 == state % 2) {
                    state++;
                } else if ('\r' == b && 0 == state % 2) {
                    state++;
                } else if (-1 == b) {
                    bytes.close();
                    logger.error("unexpected EOF indicates incomplete response header: {}",
                            new String(bytes.toByteArray()));
                    throw new EOFException("reached unexpected end of input in header");
                } else {
                    // not part of CRLFx2. Unwind any pushed CR/LFs and start over
                    while (state > 0) {
                        bytes.write(0 == state-- % 2 ? '\n' : '\r');
                    }
                    bytes.write(b);
                    state = 0;
                }
            }
            bytes.write(CRLF.getBytes());
            logger.trace("received header: {}", bytes);
            return bytes.toByteArray();
        } finally {
            bytes.close();  // noop close prevents dumb compiler warnings
        }
    }

    /* (non-Javadoc)
     * @see java.io.Closeable#close()
     */
    public void close() throws IOException {
        socket.close();
    }
    
    /*
     * (non-Javadoc)
     * @see com.kpelykh.docker.client.AttachedContainer#waitFor()
     */
    public void waitFor() throws InterruptedException,IOException {
        try {
            demuxer.waitFor();                    
        } finally {
            socket.close();
        }
    }
 }
