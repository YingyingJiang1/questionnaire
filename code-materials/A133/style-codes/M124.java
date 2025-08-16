    @Override
    public int read() throws IOException
    {
        // Critical section because we're altering __bytesAvailable,
        // __queueHead, and the contents of _queue in addition to
        // testing value of __hasReachedEOF.
        synchronized (__queue)
        {

            while (true)
            {
                if (__ioException != null)
                {
                    IOException e;
                    e = __ioException;
                    __ioException = null;
                    throw e;
                }

                if (__bytesAvailable == 0)
                {
                    // Return EOF if at end of file
                    if (__hasReachedEOF) {
                        return EOF;
                    }

                    // Otherwise, we have to wait for queue to get something
                    if(__threaded)
                    {
                        __queue.notify();
                        try
                        {
                            __readIsWaiting = true;
                            __queue.wait();
                            __readIsWaiting = false;
                        }
                        catch (InterruptedException e)
                        {
                            throw new InterruptedIOException("Fatal thread interruption during read.");
                        }
                    }
                    else
                    {
                        //__alreadyread = false;
                        __readIsWaiting = true;
                        int ch;
                        boolean mayBlock = true;    // block on the first read only

                        do
                        {
                            try
                            {
                                if ((ch = __read(mayBlock)) < 0) { // must be EOF
                                    if(ch != WOULD_BLOCK) {
                                        return (ch);
                                    }
                                }
                            }
                            catch (InterruptedIOException e)
                            {
                                synchronized (__queue)
                                {
                                    __ioException = e;
                                    __queue.notifyAll();
                                    try
                                    {
                                        __queue.wait(100);
                                    }
                                    catch (InterruptedException interrupted)
                                    {
                                        // Ignored
                                    }
                                }
                                return EOF;
                            }


                            try
                            {
                                if(ch != WOULD_BLOCK)
                                {
                                    __processChar(ch);
                                }
                            }
                            catch (InterruptedException e)
                            {
                                if (__isClosed) {
                                    return EOF;
                                }
                            }

                            // Reads should not block on subsequent iterations. Potentially, this could happen if the
                            // remaining buffered socket data consists entirely of Telnet command sequence and no "user" data.
                            mayBlock = false;

                        }
                        // Continue reading as long as there is data available and the queue is not full.
                        while (super.available() > 0 && __bytesAvailable < __queue.length - 1);

                        __readIsWaiting = false;
                    }
                    continue;
                }
                else
                {
                    int ch;

                    ch = __queue[__queueHead];

                    if (++__queueHead >= __queue.length) {
                        __queueHead = 0;
                    }

                    --__bytesAvailable;

            // Need to explicitly notify() so available() works properly
            if(__bytesAvailable == 0 && __threaded) {
                __queue.notify();
            }

                    return ch;
                }
            }
        }
    }
