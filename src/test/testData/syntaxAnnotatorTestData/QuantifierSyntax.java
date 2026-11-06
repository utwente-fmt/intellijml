package syntaxAnnotatorTestData;

// the //@ comments are separated by other comments, as the JML of //@ comments on consecutive lines is one text when
// a parenthesis is still open
public class QuantifierSyntax {
    //@ ensures (\forall int i; i > 0 && i < 10; i+5 >= 5);
    //@ ensures <error descr="A quantified expression should be inside parentheses, including the keyword">\forall</error> (int i; i > 0 && i < 10; i+5 >= 5);
    // as in OpenJML, a whole expression can be a quantified expression without parentheses
    //@ ensures \forall int i; i > 0 && i < 10; i+5 >= <error descr="Incorrectly formed or terminated ensures statement near here -- perhaps a missing semicolon">5</error>
    // another case
    //@ ensures <error descr="A quantified expression should be inside parentheses, including the keyword">\forall</error>
    // another case
    //@ ensures (<error descr="Variable declaration expected after this token">\forall</error>
    // another case
    //@ ensures (\forall <error descr="Identifier or left bracket expected after this token">int</error>
    // another case
    //@ ensures (\forall int <error descr="Comma, left bracket or semicolon expected after this token">i</error>
    // another case
    //@ ensures (\forall int i<error descr="Range condition or expression expected after this token">;</error>
    // another case
    //@ ensures (\forall int i; i > 0 && i < <error descr="Right parenthesis or semicolon expected after this token">10</error>
    // another case
    //@ ensures (\forall int i; i > 0 && i < 10; i+5 >= <error descr="Right parenthesis expected after this token">5</error>
    // another case
    //@ ensures (<weak_warning descr="Runtime assertion checking is not implemented for this type or number of declarations in a quantified expression">\forall</weak_warning> int i;; i+5 >= 5);
    //@ ensures (\forall int i; i > 0 && i < 10;<error descr="Expression expected, got ')'">)</error>;
    //@ ensures (\forall int i; i > 0 && i < 10);
    //@ ensures (\forall int i;<error descr="Range condition or expression expected, got ')'">)</error>;
    //@ ensures (\forall int i<error descr="Comma, left bracket or semicolon expected, got ')'">)</error>;
    public void loop(int[] arr) {

    }
}
