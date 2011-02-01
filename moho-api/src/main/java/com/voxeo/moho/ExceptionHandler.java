package com.voxeo.moho;

import com.voxeo.utils.Event;


public interface ExceptionHandler {

    // Returns true if event processing should continue
    public boolean handle(Exception ex, Event<?> event);

}
