class A {
  private void f() {
    int a = new Comparable();

    new Comparable<T>() { // 1
      private void f() { // 2
        System.out.println(); // 3
        System.out.println(); // 4
      } // 5
    }; // 6

    new Comparable<T>() { // 1
      private void f() { // 2
        System.out.println(); // 3
        System.out.println(); // 4
        System.out.println(); // 5
      } // 6
    }; // 7

    new Comparable<T>() { // 21

      @Override
      private void f(int a, int b) {
        if (a == b) {
          return 0;
        }

        return a < b;
      }

      private void g() {
        System.out.println();
        System.out.println();
        System.out.println();
        System.out.println();
        System.out.println();
        return;
      }

    };

    Callable<Integer> c0 = ()-> {
      return 1;
    };
    Callable<Integer> c1 = ()-> {
      System.out.println();
      System.out.println();
      System.out.println();
      System.out.println();
      System.out.println();
      return 1;
    };

    Callable<Integer> c1 = ()-> 1 + 2;
    Callable<Integer> c1 = ()-> 1 + 2+
        2 +
        3 * 4 +
        5+
        3+
        1+
        1;




  }
}

public enum SizeUnit {

  BYTES {
    @Override
    public long toBytes(long size) {
      return size;
    }

    @Override
    public long toKB(long size) {
      return size / (C1 / C0);
    }

    @Override
    public long toMB(long size) {
      return size / (C2 / C0);
    }

    @Override
    public long toGB(long size) {
      return size / (C3 / C0);
    }
  };

  public abstract long toBytes(long size);
  public abstract long toKB(long size);
  public abstract long toMB(long size);
  public abstract long toGB(long size);

  private static final long C0 = 1L;
  private static final long C1 = C0 * 1024L;
  private static final long C2 = C1 * 1024L;
  private static final long C3 = C2 * 1024L;

}

@interface plop {


}
