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
package com.kpelykh.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Kevin A. Archie <karchie@wustl.edu>
 *
 */
public class ByteArraySender implements IOFunction<OutputStream,Void> {
    private final byte[] b;
    
    public ByteArraySender(final byte[] bytes) {
        this.b = bytes;
    }

    /* (non-Javadoc)
     * @see com.kpelykh.base.ThrowingFunction#apply(java.lang.Object)
     */
    @Override
    public Void apply(final OutputStream k) throws IOException {
        k.write(b);
        return null;
    }
}
