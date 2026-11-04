package typeAnnotatorTestData;

import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.List;

public class PurenessOfCalls {
    int number = 0;
    String s = "hello";
    List<String> stringList = new ArrayList<>();

    // on own methods
    /*@ invariant <warning descr="A non-pure method is being called where it is not permitted: typeAnnotatorTestData.PurenessOfCalls.getNumberPureNotAnnotated()">getNumberPureNotAnnotated()</warning> == 0;
      @ invariant <warning descr="A non-pure method is being called where it is not permitted: typeAnnotatorTestData.PurenessOfCalls.getNumberNonPureNotAnnotated()">getNumberNonPureNotAnnotated()</warning> == 0;
      @ invariant getNumberNonPureAnnotated() == 0;
      @ invariant getNumberIntelliJContractPure() == 0;
      @ invariant <warning descr="A non-pure method is being called where it is not permitted: typeAnnotatorTestData.PurenessOfCalls.getNumberIntelliJContractNotPure()">getNumberIntelliJContractNotPure()</warning> == 0;
    */

    // Java built-in methods
    /*@ invariant stringList.size() > 0;
      @ invariant stringList.get(4) == null;
      @ invariant stringList.get(4).equals("test");
      @ invariant s.length() > 0;
      @ invariant <warning descr="A non-pure method is being called where it is not permitted: java.util.List.add(E)">stringList.add("test")</warning>;
    */

    // chained calls
    /*@ invariant <warning descr="A non-pure method is being called where it is not permitted: typeAnnotatorTestData.PurenessOfCalls.getStringList()">getStringList()</warning>.size() > 0;
      @ invariant <warning descr="A non-pure method is being called where it is not permitted: typeAnnotatorTestData.PurenessOfCalls.getStringList()">getStringList()</warning>.get(8).length() > 0;
      @ invariant <warning descr="A non-pure method is being called where it is not permitted: typeAnnotatorTestData.PurenessOfCalls.modifyInputStringList(java.util.List<java.lang.String>)">modifyInputStringList(<warning descr="A non-pure method is being called where it is not permitted: typeAnnotatorTestData.PurenessOfCalls.getStringList()">getStringList()</warning>)</warning>.get(8).length() > 0;
    */

    //@ ensures \result.size() == stringList.size();
    List<String> getStringList() {
        return stringList;
    }

    //@pure
    List<String> modifyInputStringList(List<String> list) {
        list.add("test");
        return list;
    }

    int getNumberPureNotAnnotated() {
        return number;
    }

    int getNumberNonPureNotAnnotated() {
        return number++;
    }

    //@ pure
    int getNumberNonPureAnnotated() {
        return number++;
    }

    @Contract(pure = true)
    int getNumberIntelliJContractPure() {
        return number;
    }

    @Contract(pure = false)
    int getNumberIntelliJContractNotPure() {
        return number;
    }
}
