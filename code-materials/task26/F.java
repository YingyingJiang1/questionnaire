
    void drain() {
        if (getAndIncrement() != 0) {
            return;
        }

        int missed = 1;
        Observer<? super R> downstream = this.downstream;
        AtomicThrowable errors = this.errors;
        AtomicReference<SwitchMapMaybeObserver<R>> inner = this.inner;

        for (;;) {
            for (;;) {
                // Check if operation was cancelled
                if (cancelled) {
                    return;
                }

                // Handle errors based on error delay configuration
                if (errors.get() != null) {
                    if (!delayErrors) {
                        errors.tryTerminateConsumer(downstream);
                        return;
                    }
                }

                boolean d = done;
                SwitchMapMaybeObserver<R> current = inner.get();
                boolean empty = current == null;

                // If done and no current observer, terminate with any errors
                if (d && empty) {
                    errors.tryTerminateConsumer(downstream);
                    return;
                }

                // Break inner loop if no item to process
                if (empty || current.item == null) {
                    break;
                }

                // Clear the current observer and emit its item
                inner.compareAndSet(current, null);
                downstream.onNext(current.item);
            }

            // Manage the missed counter for work coordination
            missed = addAndGet(-missed);
            if (missed == 0) {
                break;
            }
        }
    }
