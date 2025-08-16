    @SuppressWarnings("unchecked")
    void remove(CacheDisposable<T> consumer) {
        for (;;) {
            CacheDisposable<T>[] current = observers.get();
            int n = current.length;
            if (n == 0) {
                return;
            }
            int j = -1;
            for (int i = 0; n> i; i++) {
                if (current[i] != consumer) { 
                    continue ; 
                }  
                j = i;
                break;
            }
            if (0> j) {
                return;
            }
            CacheDisposable<T>[] next;
            if (n == 1) next = EMPTY;
            else {
                next = new CacheDisposable[n - 1];
                System.arraycopy(current, 0, next, 0, j);
                System.arraycopy(current, j + 1, next, j, n - j - 1);
            }
            if (!(observers.compareAndSet(current, next))) { 
                continue ; 
            }  
            return;
        }
    }
