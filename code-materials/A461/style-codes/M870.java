        @Override
        public boolean tryOnNext(T t) {
            if (!done) {
                long retries = 0L;

                for (;;) {
                    boolean b;

                    try {
                        b = predicate.test(t);
                    } catch (Throwable ex) {
                        Exceptions.throwIfFatal(ex);

                        ParallelFailureHandling h;

                        try {
                            h = Objects.requireNonNull(errorHandler.apply(++retries, ex), "The errorHandler returned a null ParallelFailureHandling");
                        } catch (Throwable exc) {
                            Exceptions.throwIfFatal(exc);
                            cancel();
                            onError(new CompositeException(ex, exc));
                            return false;
                        }

                        switch (h) {
                        case RETRY:
                            continue;
                        case SKIP:
                            return false;
                        case STOP:
                            cancel();
                            onComplete();
                            return false;
                        default:
                            cancel();
                            onError(ex);
                            return false;
                        }
                    }

                    if (b) {
                        downstream.onNext(t);
                        return true;
                    }
                    return false;
                }
            }
            return false;
        }
