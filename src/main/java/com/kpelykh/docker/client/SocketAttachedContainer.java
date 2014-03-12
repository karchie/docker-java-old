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
import java.net.Socket;
import java.net.SocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author Kevin A. Archie <karchie@wustl.edu>
 *
 */
final class SocketAttachedContainer implements AttachedContainer {
    private static final String CRLF = "\r\n";
    private final Logger logger = LoggerFactory.getLogger(SocketAttachedContainer.class);
    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final byte[] entity;
    private final OutputStream stdout, stderr;

    public SocketAttachedContainer(final Socket socket, final String path,
            final String containerId, final byte[] stdin,
            final OutputStream stdout, final OutputStream stderr,
            final boolean logs, final boolean stream) throws IOException {
        this.socket = socket;
        if (!socket.isConnected()) {
            throw new IllegalStateException("socket must be connected");
        }
        entity = stdin;
        in = socket.getInputStream();
        out = socket.getOutputStream();

        this.stdout = stdout;
        this.stderr = stderr;

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
        req.append("&stdin=").append(null != entity);
        req.append("&stdout=").append(null != stdout);
        req.append("&stderr=").append(null != stderr);
        req.append(" HTTP/1.1").append(CRLF).append(CRLF);
        out.write(req.toString().getBytes());
        out.flush();
        logger.trace("sent request header: {}", req);

        consumeHTTPHeader(in);
    }

    public SocketAttachedContainer(final SocketAddress addr, final String path,
            final String containerId, final byte[] stdin,
            final OutputStream stdout, final OutputStream stderr,
            final boolean logs, final boolean stream) throws IOException {
        this(socketFromAddress(addr), path, containerId, stdin, stdout, stderr, logs, stream);
    }

    
    private static Socket socketFromAddress(final SocketAddress addr) throws IOException {
        final Socket socket = new Socket();
        socket.connect(addr);
        return socket;
    }

    /**
     * Consume the HTTP header up to and including terminating CRLFCRLF
     * @return header as byte array
     * @throws IOException
     */
    private byte[] consumeHTTPHeader(final InputStream in) throws IOException {
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
    @Override
    public void close() throws IOException {
        socket.close();
    }

    /* (non-Javadoc)
     * @see com.kpelykh.docker.client.AttachedContainer#run(byte[])
     */
    @Override
    public void run() throws DockerException {
        DockerException thrown = null;
        try {
            final AttachedStreamDemuxer<?,?> demuxer = AttachedStreamDemuxer.create(in, stdout, stderr);
            if (null != entity) {
                out.write(entity);
                out.flush();
                socket.shutdownOutput();
                logger.trace("sent request and FIN");
            } 
            demuxer.waitFor();
        } catch (IOException e) {
            throw thrown = new DockerException("error reading container output", e);
        } catch (InterruptedException e) {
            throw thrown = new DockerException("container output reader interrupted", e);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                if (null != thrown) {
                    logger.error("unable to close container attach socket", e);
                    throw thrown;
                } else {
                    throw new DockerException("unable to close container attach socket", e);
                }
            }
        }
    }
}
