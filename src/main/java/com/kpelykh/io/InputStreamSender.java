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
 */package com.kpelykh.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Kevin A. Archie <karchie@wustl.edu>
 *
 */
public final class InputStreamSender implements IOFunction<OutputStream,Void> {
    private static final int BUFSIZE = 65536;
    private final InputStream in;

    public InputStreamSender(final InputStream in) {
        this.in = in;
    }

    /* (non-Javadoc)
     * @see com.kpelykh.base.ThrowingFunction#apply(java.lang.Object)
     */
    @Override
    public Void apply(final OutputStream out) throws IOException {
        final byte[] buf = new byte[BUFSIZE];
        int n;
        while (0 < (n = in.read(buf))) {
            out.write(buf, 0, n);
        }
        return null;
    }
}
