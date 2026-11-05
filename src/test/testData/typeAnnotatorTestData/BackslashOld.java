package typeAnnotatorTestData;

public class BackslashOld {

    /*@ ensures \old(true);
      @ ensures \old(<error descr="Reference cannot be resolved">number</error>);
      @ ensures \old(<error descr="Reference cannot be resolved">nonExistantVar</error>);
      @ ensures <error descr="This must be of type boolean, but is Object">\old(<error descr="Object allocation is not permitted in specification expressions">new Object()</error>)</error>;
      @ ensures <error descr="This must be of type boolean, but is int">\old(getInt())</error>;
      @ pure
    */
    int getInt() {
        return 0;
    }
}
