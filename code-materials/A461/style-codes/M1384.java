        @Override
        public void onNext(T t) {
            if (done) {
                return;
            }
            boolean callOnOverflow = false;
            boolean callError = false;
            boolean callDrain = false;
            Deque<T> dq = deque;
            T toDrop = null;
            synchronized (dq) {
               if (dq.size() == bufferSize) {
                   switch (strategy) {
                   case DROP_LATEST:
                       toDrop = dq.pollLast();
                       dq.offer(t);
                       callOnOverflow = true;
                       break;
                   case DROP_OLDEST:
                       toDrop = dq.poll();
                       dq.offer(t);
                       callOnOverflow = true;
                       break;
                   default:
                       // signal error
                       toDrop = t;
                       callError = true;
                       break;
                   }
               } else {
                   dq.offer(t);
                   callDrain = true;
               }
            }

            if (callOnOverflow && onOverflow != null) {
                try {
                    onOverflow.run();
                } catch (Throwable ex) {
                    Exceptions.throwIfFatal(ex);
                    upstream.cancel();
                    onError(ex);
                }
            }

            if (onDropped != null && toDrop != null) {
                try {
                    onDropped.accept(toDrop);
                } catch (Throwable ex) {
                    Exceptions.throwIfFatal(ex);
                    upstream.cancel();
                    onError(ex);
                }
            }

            if (callError) {
                upstream.cancel();
                onError(MissingBackpressureException.createDefault());
            }

            if (callDrain) {
                drain();
            }
        }
