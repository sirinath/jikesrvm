/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package test.org.jikesrvm.basic.core.bytecode;

class TestArithmetic {
  private static int int_3 = -3;
  private static int int1 = 1;
  private static int int3 = 3;
  private static int int5 = 5;
  private static int int33 = 33;
  private static int int65 = 65;
  private static long long2 = 2;
  private static long long10000000000 = 10000000000L;
  private static long long0x0110000000000011 = 0x0110000000000011L;
  private static long long0x1010000000000101 = 0x1010000000000101L;
  private static long long0xBEEFBEEFCAFEBABE = 0xBEEFBEEFCAFEBABEL;

  public static void main(String[] args) {
    System.out.println();
    System.out.println("-- itest --");
    itest();
    System.out.println();
    System.out.println("-- ltest --");
    ltest();
    System.out.println();
    System.out.println("-- ftest --");
    ftest();
    System.out.println();
    System.out.println("-- dtest --");
    dtest();
    System.out.println();
    System.out.println("-- nanTestFloat --");
    nanTestFloat();
    System.out.println();
    System.out.println("-- nanTestDouble --");
    nanTestDouble();
    System.out.println();
    System.out.println("-- remTest --");
    remTest();
  }

  private static void itest() {
    int a = int3;
    System.out.print("Expected: 4 Actual: ");
    System.out.println(a + 1);  // iadd
    System.out.print("Expected: 2 Actual: ");
    System.out.println(a - 1);  // isub
    System.out.print("Expected: 9 Actual: ");
    System.out.println(a * 3);  // imul
    System.out.print("Expected: 1 Actual: ");
    System.out.println(a / 2);  // idiv
    System.out.print("Expected: 1 Actual: ");
    System.out.println(a % 2);  // irem
    System.out.print("Expected: -3 Actual: ");
    System.out.println(-a);  // ineg
    System.out.print("Expected: 4 Actual: ");
    System.out.println(++a);  // iinc

    a = int3;     // 0x00000011
    int b = int5; // 0x00000101

    System.out.print("Expected: 1 Actual: ");
    System.out.println(a & b);  // iand
    System.out.print("Expected: 7 Actual: ");
    System.out.println(a | b);  // ior
    System.out.print("Expected: 6 Actual: ");
    System.out.println(a ^ b);  // ixor

    a = int_3; // 0xfffffffd;

    System.out.print("Expected: -6 Actual: ");
    System.out.println(a << 1);  // ishl
    System.out.print("Expected: -2 Actual: ");
    System.out.println(a >> 1);  // ishr
    System.out.print("Expected: 2147483646 Actual: ");
    System.out.println(a >>> 1);  // iushr
    
    // funny Java shift cases
    a = int1;

    System.out.print("Expected: 1 Actual: ");
    System.out.println(a << 32);
    System.out.print("Expected: 0 Actual: ");
    System.out.println((a << 16) << 16);
    System.out.print("Expected: 2 Actual: ");
    System.out.println(a << 33);
    System.out.print("Expected: -2147483648 Actual: ");
    System.out.println(a << -1);    
    System.out.print("Expected: 1 Actual: ");
    System.out.println(a << -32);    
    System.out.print("Expected: -2147483648 Actual: ");
    System.out.println(a << -33);    
    System.out.print("Expected: 2 Actual: ");
    System.out.println(1 << int33);    

    System.out.print("Expected: 1 Actual: ");
    System.out.println(a >> 32);
    System.out.print("Expected: 0 Actual: ");
    System.out.println((a >> 16) >> 16);
    System.out.print("Expected: 0 Actual: ");
    System.out.println(a >> 33);
    System.out.print("Expected: 0 Actual: ");
    System.out.println(a >> -1);    
    System.out.print("Expected: 1 Actual: ");
    System.out.println(a >> -32);    
    System.out.print("Expected: 0 Actual: ");
    System.out.println(a >> -33);    
    System.out.print("Expected: -2 Actual: ");
    System.out.println(-4 >> int33);

    System.out.print("Expected: 1 Actual: ");
    System.out.println(a >>> 32);
    System.out.print("Expected: 0 Actual: ");
    System.out.println((a >>> 16) >>> 16);
    System.out.print("Expected: 0 Actual: ");
    System.out.println(a >>> 33);
    System.out.print("Expected: 0 Actual: ");
    System.out.println(a >>> -1);    
    System.out.print("Expected: 1 Actual: ");
    System.out.println(a >>> -32);    
    System.out.print("Expected: 0 Actual: ");
    System.out.println(a >>> -33);    
    System.out.print("Expected: 2147483646 Actual: ");
    System.out.println(-4 >>> int33);
  }

  private static void ltest() {
    long a = long10000000000;
    long b = long2;

    System.out.print("Expected: 10000000002 Actual: ");
    System.out.println(a + b);  // ladd
    System.out.print("Expected: 9999999998 Actual: ");
    System.out.println(a - b);  // lsub
    System.out.print("Expected: 20000000000 Actual: ");
    System.out.println(a * b);  // lmul
    System.out.print("Expected: 5000000000 Actual: ");
    System.out.println(a / b);  // ldiv
    System.out.print("Expected: 0 Actual: ");
    System.out.println(a % b);  // lrem
    System.out.print("Expected: -2 Actual: ");
    System.out.println(-b);  // lneg
    System.out.print("Expected: -10000000000 Actual: ");
    System.out.println(-a);  // lneg

    a = long0x0110000000000011;
    b = long0x1010000000000101;

    System.out.print("Expected: 4503599627370497 Actual: ");
    System.out.println(a & b);  // land
    System.out.print("Expected: 1229482698272145681 Actual: ");
    System.out.println(a | b);  // lor
    System.out.print("Expected: 1224979098644775184 Actual: ");
    System.out.println(a ^ b);  // lxor

    a = long0xBEEFBEEFCAFEBABE;

    System.out.print("Expected: 9070106573795063164 Actual: ");
    System.out.println(a << 1);  // lshl
    System.out.print("Expected: -306530926119425288 Actual: ");
    System.out.println(a << 2);
    System.out.print("Expected: -613061852238850576 Actual: ");
    System.out.println(a << 3);
    System.out.print("Expected: -1226123704477701152 Actual: ");
    System.out.println(a << 4);
    System.out.print("Expected: -1171235197933666816 Actual: ");
    System.out.println(a << 8);
    System.out.print("Expected: -4688305491665879040 Actual: ");
    System.out.println(a << 16);
    System.out.print("Expected: -3819410108757049344 Actual: ");
    System.out.println(a << 32);
    System.out.print("Expected: -7638820217514098688 Actual: ");
    System.out.println(a << 33);
    System.out.print("Expected: 3169103638681354240 Actual: ");
    System.out.println(a << 34);
    System.out.print("Expected: 6338207277362708480 Actual: ");
    System.out.println(a << 35);
    System.out.print("Expected: -5770329518984134656 Actual: ");
    System.out.println(a << 36);
    System.out.print("Expected: -91551935198396416 Actual: ");
    System.out.println(a << 40);
    System.out.print("Expected: -4990551337079930880 Actual: ");
    System.out.println(a << 48);
    System.out.print("Expected: -4755801206503243776 Actual: ");
    System.out.println(a << 56);
    System.out.print("Expected: 0 Actual: ");
    System.out.println(a << 63);
    System.out.print("Expected: -4688318749957244226 Actual: ");
    System.out.println(a << 64);
    System.out.print("Expected: 9070106573795063164 Actual: ");
    System.out.println(a << 65);

    System.out.print("Expected: -2344159374978622113 Actual: ");
    System.out.println(a >> 1);  // lshr
    System.out.print("Expected: -1172079687489311057 Actual: ");
    System.out.println(a >> 2);
    System.out.print("Expected: -586039843744655529 Actual: ");
    System.out.println(a >> 3);
    System.out.print("Expected: -293019921872327765 Actual: ");
    System.out.println(a >> 4);
    System.out.print("Expected: -18313745117020486 Actual: ");
    System.out.println(a >> 8);
    System.out.print("Expected: -71538066863362 Actual: ");
    System.out.println(a >> 16);
    System.out.print("Expected: -1091584273 Actual: ");
    System.out.println(a >> 32);
    System.out.print("Expected: -545792137 Actual: ");
    System.out.println(a >> 33);
    System.out.print("Expected: -272896069 Actual: ");
    System.out.println(a >> 34);
    System.out.print("Expected: -136448035 Actual: ");
    System.out.println(a >> 35);
    System.out.print("Expected: -68224018 Actual: ");
    System.out.println(a >> 36);
    System.out.print("Expected: -4264002 Actual: ");
    System.out.println(a >> 40);
    System.out.print("Expected: -16657 Actual: ");
    System.out.println(a >> 48);
    System.out.print("Expected: -66 Actual: ");
    System.out.println(a >> 56);
    System.out.print("Expected: -1 Actual: ");
    System.out.println(a >> 63);
    System.out.print("Expected: -4688318749957244226 Actual: ");
    System.out.println(a >> 64);
    System.out.print("Expected: -2344159374978622113 Actual: ");
    System.out.println(a >> 65);

    System.out.print("Expected: 6879212661876153695 Actual: ");
    System.out.println(a >>> 1);  // lushr
    System.out.print("Expected: 3439606330938076847 Actual: ");
    System.out.println(a >>> 2);
    System.out.print("Expected: 1719803165469038423 Actual: ");
    System.out.println(a >>> 3);
    System.out.print("Expected: 859901582734519211 Actual: ");
    System.out.println(a >>> 4);
    System.out.print("Expected: 53743848920907450 Actual: ");
    System.out.println(a >>> 8);
    System.out.print("Expected: 209936909847294 Actual: ");
    System.out.println(a >>> 16);
    System.out.print("Expected: 3203383023 Actual: ");
    System.out.println(a >>> 32);
    System.out.print("Expected: 1601691511 Actual: ");
    System.out.println(a >>> 33);
    System.out.print("Expected: 800845755 Actual: ");
    System.out.println(a >>> 34);
    System.out.print("Expected: 400422877 Actual: ");
    System.out.println(a >>> 35);
    System.out.print("Expected: 200211438 Actual: ");
    System.out.println(a >>> 36);
    System.out.print("Expected: 12513214 Actual: ");
    System.out.println(a >>> 40);
    System.out.print("Expected: 48879 Actual: ");
    System.out.println(a >>> 48);
    System.out.print("Expected: 190 Actual: ");
    System.out.println(a >>> 56);
    System.out.print("Expected: 1 Actual: ");
    System.out.println(a >>> 63);
    System.out.print("Expected: -4688318749957244226 Actual: ");
    System.out.println(a >>> 64);
    System.out.print("Expected: 6879212661876153695 Actual: ");
    System.out.println(a >>> 65);
  }

  private static void ftest() {
    float a = 1;
    float b = 2;

    System.out.print("Expected: " + Integer.toHexString(Float.floatToIntBits(3.0F)) + " Actual: ");
    System.out.println(Integer.toHexString(Float.floatToIntBits(a + b)));  // fadd

    System.out.print("Expected: " + Integer.toHexString(Float.floatToIntBits(-1.0F)) + " Actual: ");
    System.out.println(Integer.toHexString(Float.floatToIntBits(a - b)));  // fsub

    System.out.print("Expected: " + Integer.toHexString(Float.floatToIntBits(2.0F)) + " Actual: ");
    System.out.println(Integer.toHexString(Float.floatToIntBits(a * b)));  // fmul

    System.out.print("Expected: " + Integer.toHexString(Float.floatToIntBits(0.5F)) + " Actual: ");
    System.out.println(Integer.toHexString(Float.floatToIntBits(a / b)));  // fdiv

    System.out.print("Expected: " + Integer.toHexString(Float.floatToIntBits(-1.0F)) + " Actual: ");
    System.out.println(Integer.toHexString(Float.floatToIntBits(-a)));  // fneg

    a = 1.5F;
    b = 0.9F;
    System.out.print("Expected: " + Integer.toHexString(Float.floatToIntBits(0.6F)) + " Actual: ");
    System.out.println(Integer.toHexString(Float.floatToIntBits(a % b)));  // frem
  }

  private static void dtest() {
    double a = 1;
    double b = 2;

    System.out.print("Expected: " + Long.toHexString(Double.doubleToLongBits(3.0D)) + " Actual: ");
    System.out.println(Long.toHexString(Double.doubleToLongBits(a + b)));  // dadd

    System.out.print("Expected: " + Long.toHexString(Double.doubleToLongBits(-1.0D)) + " Actual: ");
    System.out.println(Long.toHexString(Double.doubleToLongBits(a - b)));  // dsub

    System.out.print("Expected: " + Long.toHexString(Double.doubleToLongBits(2.0D)) + " Actual: ");
    System.out.println(Long.toHexString(Double.doubleToLongBits(a * b)));  // dmul

    System.out.print("Expected: " + Long.toHexString(Double.doubleToLongBits(0.5D)) + " Actual: ");
    System.out.println(Long.toHexString(Double.doubleToLongBits(a / b)));  // ddiv

    System.out.print("Expected: " + Long.toHexString(Double.doubleToLongBits(-1.0D)) + " Actual: ");
    System.out.println(Long.toHexString(Double.doubleToLongBits(-a)));  // dneg

    a = 1.5;
    b = 0.9;
    System.out.print("Expected: " + Long.toHexString(Double.doubleToLongBits(0.6D)) + " Actual: ");
    System.out.println(Long.toHexString(Double.doubleToLongBits(a % b)));  // drem
  }

  private static void nanTestFloat() {
    float zero = 0;
    float NaN = zero / zero;

    System.out.print("  expr     expected    got    \n");
    System.out.print("---------- -------- ----------\n");
    System.out.print("NaN <  NaN  false     " + (NaN < NaN) + "\n");
    System.out.print("NaN <= NaN  false     " + (NaN <= NaN) + "\n");
    System.out.print("NaN == NaN  false     " + (NaN == NaN) + "\n");
    System.out.print("NaN != NaN  true      " + (NaN != NaN) + "\n");
    System.out.print("NaN >= NaN  false     " + (NaN >= NaN) + "\n");
    System.out.print("NaN >  NaN  false     " + (NaN > NaN) + "\n");
  }

  private static void nanTestDouble() {
    double zero = 0;
    double NaN = zero / zero;

    System.out.println("  expr     expected    got    ");
    System.out.println("---------- -------- ----------");
    System.out.println("NaN <  NaN  false     " + (NaN < NaN));
    System.out.println("NaN <= NaN  false     " + (NaN <= NaN));
    System.out.println("NaN == NaN  false     " + (NaN == NaN));
    System.out.println("NaN != NaN  true      " + (NaN != NaN));
    System.out.println("NaN >= NaN  false     " + (NaN >= NaN));
    System.out.println("NaN >  NaN  false     " + (NaN > NaN) );
  }

  private static void remTest() {
    rem(+2, +3);
    rem(+2, -3);
    rem(-2, +3);
    rem(-2, -3);
  }

  private static void rem(final double a, final double b) {
    System.out.println(a + "  /  " + b + "=" + Long.toHexString(Double.doubleToLongBits(a / b)));
    System.out.println(a + "  %  " + b + "=" + Long.toHexString(Double.doubleToLongBits(a % b)));
    System.out.println(a + " rem " + b + "=" + Long.toHexString(Double.doubleToLongBits(Math.IEEEremainder(a, b))));
    System.out.println();
  }
}
