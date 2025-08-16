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
    @NonNull
    private U assertError(@NonNull Predicate<Throwable> errorPredicate, boolean exact) {
        int s = errors.size();
        if (s == 0) {
            throw fail("No errors");
        }

        boolean found = false;

        for (Throwable e : errors) {
            try {
                if (errorPredicate.test(e)) {
                    found = true;
                    break;
                }
            } catch (Throwable ex) {
                throw ExceptionHelper.wrapOrThrow(ex);
            }
        }

        if (found) {
            if (s != 1) {
                if (exact) {
                    throw fail("Error present but other errors as well");
                }
                throw fail("One error passed the predicate but other errors are present as well");
            }
        } else {
            if (exact) {
                throw fail("Error not present");
            }
            throw fail("No error(s) passed the predicate");
        }
        return (U)this;
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
        public void onSuccess(T value) {
            Observer<? super R> a = downstream;

            Iterator<? extends R> iterator;
            boolean has;
            try {
                iterator = mapper.apply(value).iterator();

                has = iterator.hasNext();
            } catch (Throwable ex) {
                Exceptions.throwIfFatal(ex);
                a.onError(ex);
                return;
            }

            if (!has) {
                a.onComplete();
                return;
            }

            this.it = iterator;

            if (outputFused) {
                a.onNext(null);
                a.onComplete();
                return;
            }

            for (;;) {
                if (cancelled) {
                    return;
                }

                R v;

                try {
                    v = iterator.next();
                } catch (Throwable ex) {
                    Exceptions.throwIfFatal(ex);
                    a.onError(ex);
                    return;
                }

                a.onNext(v);

                if (cancelled) {
                    return;
                }

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


