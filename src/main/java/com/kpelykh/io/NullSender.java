/**
 * Copyright (c) 2014 Washington University School of Medicine
 */
package com.kpelykh.io;

import java.io.OutputStream;

/**
 * @author Kevin A. Archie <karchie@wustl.edu>
 *
 */
public final class NullSender implements IOFunction<OutputStream, Void> {
    public static final NullSender instance = new NullSender();
    
    private NullSender() {}
    
    public static NullSender getInstance() {
        return instance;
    }
    
    /*
     * (non-Javadoc)
     * @see com.kpelykh.base.ThrowingFunction#apply(java.lang.Object)
     */
    public Void apply(final OutputStream _) {
        return null;
    }
}
