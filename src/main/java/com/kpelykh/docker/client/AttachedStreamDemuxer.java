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

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProtocolException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Kevin A. Archie <karchie@wustl.edu>
 *
 */
class AttachedStreamDemuxer<O extends OutputStream, E extends OutputStream> implements Runnable {
    private static final int STREAM_TYPE_STDOUT = 0x01000000;
    private static final int STREAM_TYPE_STDERR = 0x02000000;
    private static final int BASE_BUF_SIZE = 2048;
    
    private final Logger logger = LoggerFactory.getLogger(AttachedStreamDemuxer.class);
    
    private final DataInputStream in;
    private final O out;
    private final E err;
    private final Thread consumer;
    private Throwable throwable = null;

    /**
     * Demultiplex the docker attach stream 
     * @param in
     * @param out
     * @param err
     */
    public AttachedStreamDemuxer(final InputStream in, final O out, final E err) {
        this.in = new DataInputStream(in);
        this.consumer = new Thread(this);
        this.consumer.start();
        this.out = out;
        this.err = err;
    }
    
    public static <O extends OutputStream, E extends OutputStream> AttachedStreamDemuxer<O,E>
    create(final InputStream in, final O out, final E err) {
        return new AttachedStreamDemuxer<O,E>(in, out, err);
    }
    
    public O getOutStream() { return out; }
    
    public E getErrStream() { return err; }
    
    /**
     * Returns the exceptional condition that has occurred during run(), if any.
     * @return null if running or terminated normally; a Throwable if an exception or error occurred.
     */
    public Throwable getThrowable() { return throwable; }
    
    public void run() {
        int bufSize = BASE_BUF_SIZE;
        byte[] buf = new byte[bufSize];
        
        logger.debug("demuxer {} started", this);
        while (true) {
            final OutputStream dest;
            try {
                final int type = in.readInt();
                switch (type) {
                case STREAM_TYPE_STDOUT: dest = out; break;
                case STREAM_TYPE_STDERR: dest = err; break;
                default:
                    final ProtocolException e = new ProtocolException("unknown stream type " + (type >> 24));
                    throwable = e;
                    throw e;
                }
            } catch (EOFException eof) {
                logger.trace("stream EOF");
                break;
            } catch (IOException e) {
                logger.error("unexpected I/O error in stream header", e);
                throwable = e;
                break;
            }
            final int size;
            try {
                size = in.readInt();
            } catch (IOException e) {
                logger.error("Unexpected I/O error in stream header", e);
                throwable = e;
                break;
            }
            if (size > bufSize) {
                buf = new byte[bufSize = size]; // grow the buffer if it's not big enough
            }
            try {
                in.readFully(buf, 0, size);
                logger.trace("received frame: {}", new String(buf));
                if (null != dest) {
                    dest.write(buf);
                }
            } catch (IOException e) {
                logger.error("unexpected I/O error in frame", e);
                throwable = e;
                break;
            }
        }
    }
    
    public void waitFor() throws InterruptedException {
        logger.debug("waiting for demuxer {} to complete", this);
        consumer.join();
        logger.debug("demuxer {} finished", this);
    }
}
