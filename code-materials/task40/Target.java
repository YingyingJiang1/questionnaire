    @Override
    public void subscribe(Subscriber<? super R>[] subscribers) {
        subscribers = RxJavaPlugins.onSubscribe(this, subscribers);

        if (!validate(subscribers)) {
            return;
        }

        int n = subscribers.length;
        @SuppressWarnings("unchecked")
        Subscriber<T>[] parents = new Subscriber[n];

        for (int i = 0; i < n; i++) {

            R initialValue;

            try {
                initialValue = Objects.requireNonNull(initialSupplier.get(), "The initialSupplier returned a null value");
            } catch (Throwable ex) {
                Exceptions.throwIfFatal(ex);
                reportError(subscribers, ex);
                return;
            }

            parents[i] = new ParallelReduceSubscriber<>(subscribers[i], initialValue, reducer);
        }

        source.subscribe(parents);
    }


    @SuppressWarnings("unchecked")
    void remove(AsyncDisposable<T> ps) {
        for (;;) {
            AsyncDisposable<T>[] a = subscribers.get();
            int n = a.length;
            if (n == 0) {
                return;
            }

            int j = -1;
            for (int i = 0; i < n; i++) {
                if (a[i] == ps) {
                    j = i;
                    break;
                }
            }

            if (j < 0) {
                return;
            }

            AsyncDisposable<T>[] b;

            if (n == 1) {
                b = EMPTY;
            } else {
                b = new AsyncDisposable[n - 1];
                System.arraycopy(a, 0, b, 0, j);
                System.arraycopy(a, j + 1, b, j, n - j - 1);
            }
            if (subscribers.compareAndSet(a, b)) {
                return;
            }
        }
    }


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


        void drain() {
            if (getAndIncrement() != 0) {
                return;
            }

            Subscriber<? super R> a = downstream;
            Iterator<? extends R> iterator = this.it;

            if (outputFused && iterator != null) {
                a.onNext(null);
                a.onComplete();
                return;
            }

            int missed = 1;

            for (;;) {

                if (iterator != null) {
                    long r = requested.get();
                    long e = 0L;

                    if (r == Long.MAX_VALUE) {
                        fastPath(a, iterator);
                        return;
                    }

                    while (e != r) {
                        if (cancelled) {
                            return;
                        }

                        R v;

                        try {
                            v = Objects.requireNonNull(iterator.next(), "The iterator returned a null value");
                        } catch (Throwable ex) {
                            Exceptions.throwIfFatal(ex);
                            a.onError(ex);
                            return;
                        }

                        a.onNext(v);

                        if (cancelled) {
                            return;
                        }

                        e++;

                        boolean b;

                        try {
                            b = iterator.hasNext();
                        } catch (Throwable ex) {
                            Exceptions.throwIfFatal(ex);
                            a.onError(ex);
                            return;
                        }

                        if (!b) {
                            a.onComplete();
                            return;
                        }
                    }

                    if (e != 0L) {
                        BackpressureHelper.produced(requested, e);
                    }
                }

                missed = addAndGet(-missed);
                if (missed == 0) {
                    break;
                }

                if (iterator == null) {
                    iterator = it;
                }
            }
        }


