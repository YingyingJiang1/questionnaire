  @SuppressWarnings("unchecked")
  void remove(CacheDisposable<T> consumer) {

    for (;;) {

      CacheDisposable<T>[] current = observers.get();

      int n = current.length;
      if (n == 0) {
        return;
      }
      int j = -1;
      int i = 0; 
      while (i < n) { 
        if (current[i] != consumer) { 
          continue ; 
        }  
        j = i;
        break;

        i++;

      } 
      if (j < 0) {
        return;
      }
      CacheDisposable<T>[] next;
      if (n != 1) {
        next = new CacheDisposable[n - 1];
        System.arraycopy(current, 0, next, 0, j);

        System.arraycopy(current, j + 1, next, j, n - j - 1);
      }

      else {
        next = EMPTY;
      } 
      if (!(observers.compareAndSet(current, next))) { 
        continue ; 
      }  
      return;

    }

  }
