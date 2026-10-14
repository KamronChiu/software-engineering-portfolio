package com.kamron.software_testing.whiteboxtesting;

/*public class OperationString {
    public static String operationString(String a){
        String b = a;
        b = a+"c";
        // System.out.println(a+b);
        return a+b;

    }
    public static StringBuilder operationStringBuilder(StringBuilder a){
        StringBuilder b = a;
        b = a.append("c");
        // System.out.println(a.append(b));
        return a.append(b);
    }
}*/

public class OperationString {
    /**
     * Returns the original string twice, with "c" appended at the end.
     * Examples:
     *  - ""   → "c"
     *  - "x"  → "xxc"
     *  - "ab" → "ababc"
     *
     * @param a  the input string (must be non-null)
     * @return   a + a + "c"
     * @throws   NullPointerException if a is null
     */
    public static String operationString(String a) {
        if (a == null) {
            throw new NullPointerException("Input string cannot be null");
        }
        return a + a + "c";
    }

    /**
     * Returns a new StringBuilder containing the original content twice
     * followed by 'c'. Does NOT mutate the input builder.
     * Examples:
     *  - ""   → "c"
     *  - "x"  → "xxc"
     *  - "ab" → "ababc"
     *
     * @param a  the input StringBuilder (must be non-null)
     * @return   new StringBuilder(a + a + "c")
     * @throws   NullPointerException if a is null
     */
    public static StringBuilder operationStringBuilder(StringBuilder a) {
        if (a == null) {
            throw new NullPointerException("Input StringBuilder cannot be null");
        }
        String s = a.toString();
        return new StringBuilder(s).append(s).append('c');
    }
}
